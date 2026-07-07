# 흐름 및 파이프라인

## 알림 생성 흐름

```text
(발생 서비스) -> Kafka: notification.send
  -> NotificationSendConsumer
  -> NotificationCommandService.create
  -> (커밋 후) push dispatch 발행
```

처리 흐름:

1. `notification.send` 메시지 소비(referenceId, type, title, content, targetUserIds)
2. 멱등성 확인: `(referenceId, type, targetUserIds)`로 이미 존재하는 사용자 제외
3. 신규 대상만 벌크 insert
4. 커밋 후 각 알림에 대해 발송 트리거(push dispatch) 발행

대상이 비어 있거나 전부 기존 알림이면 아무 것도 하지 않는다(멱등).

## 발송 파이프라인

발송은 트랜잭션 경계와 외부 I/O를 분리한 구조다(발송 tx 분리).

```text
Kafka: notification.push
  -> NotificationPushConsumer
  -> NotificationDispatchService.dispatch
       1) TxService.prepare  (@Transactional) : 알림/토큰 로드, PENDING delivery 생성, 발송 대상 산출
       2) NotificationSender.send            (트랜잭션 밖) : FCM 등 외부 발송
       3) TxService.record   (@Transactional) : delivery 상태 갱신, 알림 상태 집계, 실패분 재시도 발행
```

핵심 규칙:

- 외부 발송(FCM 호출)은 `@Transactional` **밖**에서 수행해 DB 커넥션을 장시간 붙잡지 않는다.
- 수신 기기가 없으면 알림을 즉시 SUCCESS 처리한다.
- 이미 성공한 기기는 재발송 대상에서 제외한다.
- 모든 기기 성공 시 알림 SUCCESS, 아니면 FAILED로 집계한다.

## 재시도 및 재조정

### 기기 단위 재시도

발송 실패한 기기는 `notification.push.failed`로 재시도 메시지를 발행하고, `NotificationPushRetryConsumer`가 소비해 해당 기기만 재발송한다.

- 최대 시도 횟수: `notification.dispatch.max-attempt` (기본 3)
- 한계 도달 시 포기(로그 기록)
- 재시도 발행도 트랜잭션 커밋 이후에 수행한다.

### PENDING 재조정 스케줄러

`PendingNotificationReconciler`가 주기적으로 PENDING 상태로 방치된 알림을 재발행한다.

- 주기: `notification.reconciler.interval-ms` (기본 5분)
- 대상: 생성 후 `cutoff-minutes`(기본 5분) 이상 지난 PENDING
- 배치 크기: `batch-size` (기본 100)

컨슈머 실패/유실로 발송 트리거가 누락된 알림을 최종적으로 복구하는 안전망이다.

## 발송 어댑터(FCM)

`NotificationSender` 포트를 통해 발송한다.

| 구현 | 조건 |
| --- | --- |
| FcmSender | Firebase 설정이 활성화된 경우(FirebaseConfig 조건부 빈) |
| LogNotificationSender | FCM 미설정 환경의 대체(로그 출력) |

기기 토큰은 로그에 마스킹(`LogMask.token`, 앞 4자만 남기고 `****`)해 노출을 최소화한다. FCM 발송 구현은 `deviceType`을 인자로 받지만 현재는 사용하지 않는다(향후 플랫폼별 페이로드 분기 여지).

관측성: 컨슈머는 로그 상관관계를 위해 MDC에 식별자를 넣는다 — `notification.send`는 `referenceId`, `notification.push`는 `notificationId`.

## 회원 탈퇴 처리

`user.deleted` 이벤트를 소비해 `UserWithdrawalService`가 정리한다.

1. `notification_deliveries` hard delete
2. `notifications` soft delete
3. `user_notification_tokens` hard delete

전달 기록과 토큰은 보존 가치가 낮아 hard delete, 알림 본문은 이력 보존을 위해 soft delete한다.
