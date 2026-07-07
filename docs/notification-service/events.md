# 이벤트 및 Kafka

## 이벤트 및 토픽

| 토픽 | 방향 | 용도 |
| --- | --- | --- |
| `notification.send` | 소비 | 알림 생성 요청 |
| `notification.push` | 발행/소비 | 발송 트리거(내부) |
| `notification.push.failed` | 발행/소비 | 실패 기기 재시도 |
| `user.deleted` | 소비 | 회원 탈퇴 정리 |

## Kafka 운영 계약

토픽 구성: `notification.push`, `notification.push.failed` 모두 파티션 3 / 리플리카 1로 생성한다.

소비 토픽 / 메시지:

| 토픽 | 페이로드 | 파티션 키 |
| --- | --- | --- |
| `notification.send` | NotificationSendMessage `reference_id`, `type`, `title`, `content`, `target_user_ids` | (프로듀서 지정) |
| `notification.push` | 문자열(`notificationId`) | `userId` |
| `notification.push.failed` | PushFailedMessage `notification_id`, `device_token` | `notificationId` |
| `user.deleted` | UserDeletedMessage `user_id` | (프로듀서 지정) |

발행 토픽: `notification.push`(키=`userId`), `notification.push.failed`(키=`notificationId`). `push`는 사용자 단위 순서 보장을 위해 `userId`를 키로 쓴다.

운영 세부:

- **컨슈머 그룹**: `${spring.kafka.consumer.group-id}-{suffix}`(`-send`, `-push`, `-push-failed`, `-user-deleted`).
- **에러 핸들러**: 모든 컨슈머 `FixedBackOff(1000ms, 2회)` 재시도 후 로그 남기고 스킵.
- **멱등 생성**: `(referenceId, type, targetUserIds)`로 기존 사용자를 제외한 신규 대상만 벌크 insert한다.
- **재시도 조건**: 실패 기기는 `attemptCount < max-attempt`일 때만 `notification.push.failed`를 발행하고, 한계 도달 시 발행 없이 최종 실패 로그를 남긴다.
- **무기기 처리**: 발송 대상 기기가 없으면 알림을 즉시 SUCCESS로 집계한다.
