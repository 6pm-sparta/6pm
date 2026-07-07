# Chat Service 이벤트

## 이벤트 연동

소비 이벤트:

- `user.creator-created`
- `user.followed`
- `user.unfollowed`
- `user.deleted`

발행:

- 채팅 알림 전송 메시지(오프라인 대상 푸시용) — 알림 서비스가 소비

Consumer는 재시도와 멱등성을 고려한다(방/멤버 생성은 존재 확인 후 처리).

## Kafka 운영 계약

외부 서비스가 이 서비스로 이벤트를 보내거나 이 서비스의 발행을 소비할 때 필요한 계약이다.

소비 토픽 / 메시지 필드(JSON snake_case):

| 토픽 | 메시지 | 필드 |
| --- | --- | --- |
| `user.creator-created` | CreatorCreatedMessage | `user_id`, `nickname` |
| `user.followed` / `user.unfollowed` | FollowEventMessage | `follow_id`, `follower_id`, `followee_id`, `nickname` |
| `user.deleted` | UserDeletedMessage | `user_id` |

발행 토픽:

| 토픽 | 메시지 | 필드 | 파티션 키 |
| --- | --- | --- | --- |
| `notification.send` | NotificationSendMessage | `reference_id`, `type`(=CHAT), `title`, `content`, `target_user_ids` | 없음(라운드로빈) |

운영 세부:

- **컨슈머 그룹**: `${spring.kafka.consumer.group-id}-{suffix}` 규칙(`-creator-created`, `-followed`, `-unfollowed`, `-user-deleted`)으로 토픽별 그룹을 분리해 컨슈머 랙을 개별 추적한다.
- **에러 핸들러**: `user.followed`/`user.unfollowed`는 `FixedBackOff(2000ms, 5회)` 재시도 후 경고 로그 남기고 스킵. 나머지는 기본 `FixedBackOff(1000ms, 2회)` 후 스킵.