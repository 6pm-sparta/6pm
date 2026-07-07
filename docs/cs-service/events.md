# 이벤트 연동

## 이벤트 연동

소비 이벤트:

- `user.deleted` — `UserDeletedConsumer`가 소비해 해당 사용자 문의 이력을 초기화(soft delete)한다.

## Kafka 운영 계약

소비 토픽:

| 토픽 | 메시지 | 필드 | 처리 |
| --- | --- | --- | --- |
| `user.deleted` | UserDeletedMessage | `user_id` | 해당 사용자 문의 이력 soft delete |

운영 세부:

- **컨슈머 그룹**: `${spring.kafka.consumer.group-id}-user-deleted`.
- **에러 핸들러**: `FixedBackOff(1000ms, 2회)` 재시도 후 로그 남기고 스킵.