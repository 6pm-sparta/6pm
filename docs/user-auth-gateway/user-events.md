# User Service 이벤트 설계

## 1. 문서 목적

이 문서는 User Service가 발행하는 Kafka 이벤트와 관련 정책을 정리한다.

이벤트는 도메인 서비스 간 직접 호출을 줄이고, 사용자 도메인 상태 변경을 필요한 서비스에 전달하기 위해 사용한다.

## 2. 이벤트 발행 원칙

User Service 이벤트 발행 원칙은 다음과 같다.

- 도메인 상태 변경이 성공한 이후 이벤트를 발행한다.
- 이벤트 key는 순서 보장이 필요한 aggregate 기준으로 정한다.
- 이벤트 payload에는 consumer가 처리에 필요한 최소 정보만 담는다.
- 토큰, 비밀번호, secret 등 민감정보를 payload에 포함하지 않는다.
- 발행 실패 시 topic, key, payload를 로그로 남긴다.

발행 신뢰성을 위해 **Transactional Outbox 패턴**을 적용한다. 도메인 상태 변경과 같은 트랜잭션에서 Outbox 레코드를 INSERT하고, 별도 폴링 relay가 Kafka로 발행한다(at-least-once). 중복은 consumer 멱등성으로 흡수한다. (이전: 서비스 로직 내 직접 발행 + 실패 로그 → 발행 유실 위험이 있어 전환)

## 3. 공통 이벤트 정책

### 3.1 발행 실패

이벤트 발행 실패가 핵심 트랜잭션 실패로 이어지면 안 되는 경우에는 실패 로그를 남긴다.

로그에는 다음 정보를 포함한다.

- topic
- key
- payload
- exception message

민감정보는 로그에 남기지 않는다.

### 3.2 소비 멱등성

Kafka 이벤트는 중복 전달될 수 있다.

Consumer는 멱등성 key를 기준으로 중복 처리를 방지해야 한다. 단, 연산 자체가 멱등인 경우(예: 토큰 무효화 — 이미 blacklist면 재등록해도 결과 동일)는 별도 멱등성 저장소 없이도 충족된다(정책 6 — 이벤트별 별도 정책).

현황 / 기준:

| 이벤트 | 멱등성 기준 | 현 구현 |
| --- | --- | --- |
| `user.deleted` | `user_id` | auth/chat/notification — 연산 멱등(도달 회수 적용) |
| `user.member-withdrawn` | `user_id` | feed — `ProcessedEvent`(eventKey=topic:userId) 명시 멱등 |
| `user.creator-withdrawn` | `user_id` | feed — `ProcessedEvent` 명시 멱등 |
| `user.creator-created` | `user_id` | chat |
| `user.followed` | `follow_id` | chat |
| `user.unfollowed` | `follow_id` | chat |

### 3.3 재시도

Consumer 처리 실패 시 기본적으로 재시도를 고려한다.

재시도 대상:

- 일시적 DB 연결 실패
- 외부 API 일시 실패
- 네트워크 오류
- Kafka 일시 장애

비즈니스 검증 실패처럼 재시도해도 성공 가능성이 없는 오류는 재시도 대상에서 제외한다.

### 3.4 DLQ

재시도 이후에도 실패한 이벤트는 `{topic}.DLQ`로 이동시키는 정책을 목표로 한다.

DLQ 실제 구현은 MVP 이후로 둔다.

### 3.5 순서 보장

동일 aggregate에 대한 순서가 중요한 이벤트는 같은 partition key를 사용한다.

예시:

- 사용자 기준 이벤트: `user_id`
- 팔로우 기준 이벤트: `follow_id`

전체 시스템 수준의 전역 순서는 보장하지 않는다.

## 4. 이벤트 명세 및 downstream 연동

User Service는 총 6종의 이벤트를 발행한다. 각 이벤트의 소비자와 발행 실패 시 downstream 정합성 영향은 다음과 같다.

| 이벤트 | key | consumer | 소비 동작 | 발행 실패 시 영향 | 중요도 |
| --- | --- | --- | --- | --- | --- |
| `user.deleted` | `user_id` | **auth + chat + notification** | auth: 토큰 무효화 / chat: 유저 정리 / notif: 탈퇴 처리 | 탈퇴자 **토큰이 안 죽음(보안)** + 채팅/알림 정리 누락 | 🔴 최상 |
| `user.member-withdrawn` | `user_id` | feed | 댓글 익명화 + 좋아요 삭제 | 탈퇴자 콘텐츠/개인정보 정리 누락 | 🟠 높음 |
| `user.creator-withdrawn` | `user_id` | feed | 댓글 익명화 + 좋아요 + **게시물 삭제** | 탈퇴 크리에이터 콘텐츠 잔존(개인정보) | 🟠 높음 |
| `user.creator-created` | `user_id` | chat | 채팅방 자동 생성 | 크리에이터 채팅방 없음 | 🟡 중 |
| `user.followed` | `follow_id` | chat | 채팅방 입장 | 팔로우 후 방 입장 누락 | 🟡 중 |
| `user.unfollowed` | `follow_id` | chat | 채팅방 나가기 | 언팔 후 방 잔존 | 🟢 낮음 |

> 🔴 `user.deleted`는 소비자가 3곳이며 보안(토큰)이 걸려 발행 유실 영향이 가장 크다. Outbox 최우선 적용 대상.
> 개인정보 정리(withdrawn 2종)도 법적 중요도가 높아 Outbox 다음 순위. followed/unfollowed/creator-created는 채팅 편의 성격.
> 본 설계에서는 발행 방식 일관성을 위해 **6종 전부 Outbox로 발행**한다(혼재 방지).

### 4.1 user.deleted

| 항목 | 값 |
| --- | --- |
| topic | `user.deleted` |
| key | `user_id` |
| producer | user-service |
| consumer | **auth-service / chat-service / notification-service** |
| 목적 | 탈퇴 사용자 토큰 무효화 및 연관 데이터 정리 |

Payload:

```json
{
  "user_id": "uuid"
}
```

Consumer 처리:

- **auth**: 사용자 단위 blacklist 등록 + Refresh Token 삭제
- **chat**: 탈퇴 사용자 채팅 정리
- **notification**: 탈퇴 사용자 알림 정리

### 4.2 user.member-withdrawn

| 항목 | 값 |
| --- | --- |
| topic | `user.member-withdrawn` |
| key | `user_id` |
| producer | user-service |
| consumer | feed-service |
| 목적 | 일반 회원 탈퇴 시 콘텐츠 정리 (댓글 익명화, 좋아요 삭제) |

Payload: `{ "user_id": "uuid" }` (key=user_id, payload동일)

### 4.3 user.creator-withdrawn

| 항목 | 값 |
| --- | --- |
| topic | `user.creator-withdrawn` |
| key | `user_id` |
| producer | user-service |
| consumer | feed-service |
| 목적 | 크리에이터 탈퇴 시 콘텐츠 정리 (댓글 익명화, 좋아요 삭제, 게시물 삭제) |

Payload: `{ "user_id": "uuid" }`

> 회원 탈퇴 1회에 역할별 withdrawn(member 또는 creator) + 공통 `user.deleted`가 함께 발행된다.

## 5. Chat 연동 이벤트

### 5.1 user.creator-created

| 항목 | 값 |
| --- | --- |
| topic | `user.creator-created` |
| key | `user_id` |
| producer | user-service |
| consumer | chat-service |
| 목적 | 크리에이터 생성 시 채팅방 자동 생성 |

Payload:

```json
{
  "user_id": "uuid",
  "nickname": "name"
}
```

### 5.2 user.followed

| 항목 | 값 |
| --- | --- |
| topic | `user.followed` |
| key | `follow_id` |
| producer | user-service |
| consumer | chat-service |
| 목적 | 팔로우 발생 시 채팅방 입장 |

Payload:

```json
{
  "follow_id": "uuid",
  "follower_id": "uuid",
  "followee_id": "uuid",
  "nickname": "name"
}
```

### 5.3 user.unfollowed

| 항목 | 값 |
| --- | --- |
| topic | `user.unfollowed` |
| key | `follow_id` |
| producer | user-service |
| consumer | chat-service |
| 목적 | 언팔로우 발생 시 채팅방 나가기 |

Payload:

```json
{
  "follow_id": "uuid",
  "follower_id": "uuid",
  "followee_id": "uuid"
}
```

## 6. 이벤트 발행 시점

| 이벤트 | 발행 시점 |
| --- | --- |
| `user.deleted` | 회원 탈퇴 도메인 처리 성공 이후 |
| `user.member-withdrawn` | 일반 회원 탈퇴 처리 성공 이후 |
| `user.creator-withdrawn` | 크리에이터 탈퇴 처리 성공 이후 |
| `user.creator-created` | 크리에이터 회원가입 트랜잭션 성공 이후 |
| `user.followed` | 팔로우 생성 및 count 증가 성공 이후 |
| `user.unfollowed` | 언팔로우 삭제 및 count 감소 성공 이후 |

Outbox 적용 이후엔 "도메인 상태 변경 + Outbox INSERT"가 **동일 트랜잭션**으로 커밋되고, relay가 커밋된 Outbox를 폴링해 발행한다. (after-commit 훅 방식의 "커밋은 됐는데 발행 실패" 구멍이 제거됨)

## 7. 이벤트 발행 신뢰성 (Outbox)

Transactional Outbox 패턴을 적용해 "도메인 커밋은 성공했는데 이벤트는 유실"되는 문제를 제거한다. (order-service의 기존 Outbox 구현을 참고한다.)

구조:

- **Outbox 테이블**: aggregateId(partition key), topic, payload(JSON), status(PENDING/PUBLISHED), publishedAt
- **Appender**: 도메인 트랜잭션 내에서 Outbox row INSERT (직렬화 실패 시 throw → 롤백으로 상태/이벤트 불일치 방지)
- **relay(폴링)**: PENDING을 오래된 순으로 읽어 레코드 단위 트랜잭션으로 발행, 브로커 ack 확인 후 PUBLISHED 전이. 실패 시 PENDING 유지 → 다음 폴링 재시도(at-least-once)
- 중복은 consumer 멱등성으로 흡수 (3.2 참고)

적용 범위: user 발행 6종 전체. 단, 정합성 critical 순서(`user.deleted` → withdrawn → 나머지)로 도입한다.

후속 과제:

- DLQ 도입 (재시도 이후에도 실패한 레코드)
- Outbox 공통화 (order/user 중복 → common 모듈 추출, 별도 인프라 이슈)
- 발행 완료된 Outbox 레코드 아카이브/정리 배치

## 8. Kafka UI 확인 방법

Kafka UI에서 다음 항목을 확인한다.

1. topic 존재 여부
2. message key
3. payload 필드명
4. payload 값
5. partition
6. timestamp

확인 대상 topic:

- `user.deleted`
- `user.member-withdrawn`
- `user.creator-withdrawn`
- `user.creator-created`
- `user.followed`
- `user.unfollowed`

## 9. 테스트 전략

단위 테스트:

- 이벤트 publisher 호출 여부
- topic/key/payload 구성
- 발행 실패 시 로그 처리
- 트랜잭션 이후 발행 로직

API 스모크 테스트:

- 크리에이터 회원가입 후 `user.creator-created` 확인
- 팔로우 후 `user.followed` 확인
- 언팔로우 후 `user.unfollowed` 확인
- 회원 탈퇴 후 `user.deleted` 확인

## 10. 후속 과제

- DLQ 구현 (재시도 후 최종 실패 레코드)
- Outbox 공통화 (order/user 중복 코드를 common 모듈로 — 별도 인프라 이슈, order 담당 협의)
- 이벤트 schema version 관리
- 이벤트 payload 필드 네이밍 규약 정리
- 발행 완료 Outbox 레코드 아카이브 배치

