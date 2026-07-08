# 아키텍처

## 도메인 모델

### Notification

`Notification`은 사용자에게 도달할 알림 한 건이다.

주요 속성:

- id
- userId
- referenceId (알림 원천 식별자)
- type (CHAT / FEED_NEW_POST / ORDER_COMPLETED / ORDER_CANCELED)
- title
- body
- read (읽음 여부)
- sendStatus (PENDING / SUCCESS / FAILED)

`(userId, type, referenceId)`에 unique 제약을 둬 **중복 알림을 방지**한다. soft delete 대상이다.

### NotificationDelivery

`NotificationDelivery`는 알림의 기기별 발송 결과다.

주요 속성:

- id
- notificationId
- deviceToken
- deviceType
- status (PENDING / SUCCESS / FAILED)
- attemptCount (시도 횟수)

`(notificationId, deviceToken)` unique. 한 알림이 여러 기기로 나갈 때 기기 단위로 상태를 추적한다.

### UserNotificationToken

`UserNotificationToken`은 사용자의 기기 푸시 토큰이다.

주요 속성:

- id
- userId
- deviceToken (unique)
- deviceType
- notified (알림 수신 on/off)

같은 토큰 재등록 시 소유자/타입을 갱신(`reassign`)한다. 소유자가 바뀌면 수신 설정을 초기화한다.

### 열거형

| 열거형 | 값 |
| --- | --- |
| NotificationType | CHAT, FEED_NEW_POST, ORDER_COMPLETED, ORDER_CANCELED |
| NotificationSendStatus | PENDING, SUCCESS, FAILED |
| DeviceType | IOS, ANDROID, WEB |

## Repository 계층 구조

domain 포트와 infrastructure 구현체로 분리한다.

```text
domain/repository
  NotificationRepository
  NotificationDeliveryRepository
  UserNotificationTokenRepository

infra/repository
  NotificationJpaRepository / NotificationRepositoryImpl
  NotificationDeliveryJpaRepository / NotificationDeliveryRepositoryImpl
  UserNotificationTokenJpaRepository / UserNotificationTokenRepositoryImpl
```

멱등 조회(`findExistingUserIds`), 벌크 insert(`saveAll`), 일괄 soft delete, PENDING 조회 등 커스텀 쿼리는 infrastructure 계층에서 담당한다.

## 삭제 정책

| 엔티티 | 삭제 정책 | 이유 |
| --- | --- | --- |
| Notification | soft delete | 알림 이력 보존 |
| NotificationDelivery | hard delete(탈퇴 시) | 발송 기록 보존 가치 낮음 |
| UserNotificationToken | hard delete | 기기/토큰은 재등록 가능 |

## 예외 및 응답 정책

공통 응답 형식 `ApiResponse`와 `CustomException`/`ErrorCode`를 사용한다.

주요 에러 코드:

- `TOKEN_NOT_FOUND` (404)
- `NOTIFICATION_NOT_FOUND` (404)
- `NOTIFICATION_ACCESS_DENIED` (403)

Kafka 소비 실패는 에러 핸들러로 재시도 후 스킵(로그) 처리한다.

## 설정 레퍼런스

| 프로퍼티 | 기본값 | 설명 |
| --- | --- | --- |
| `notification.dispatch.max-attempt` | 3 | 기기 단위 발송 최대 시도 횟수 |
| `notification.reconciler.interval-ms` | 300000 | PENDING 재조정 스케줄러 주기(ms, 5분) |
| `notification.reconciler.cutoff-minutes` | 5 | 재조정 대상 판단 경과 기준(분) |
| `notification.reconciler.batch-size` | 100 | 재조정 1회 배치 크기 |
| `fcm.enabled` | (미설정=false) | FCM 발송 활성화. false/미설정 시 `LogNotificationSender`로 대체 |
| `fcm.credentials-path` | (없음) | FCM 서비스계정 키 경로(`classpath:`/`file:` 지원, `fcm.enabled=true`면 필수) |
