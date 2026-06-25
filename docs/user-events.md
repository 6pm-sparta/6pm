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

MVP 단계에서는 서비스 로직 내에서 Kafka 발행을 수행한다. Outbox 패턴은 후속 고도화 과제로 둔다.

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

Consumer는 멱등성 key를 기준으로 중복 처리를 방지해야 한다.

예시:

| 이벤트 | 멱등성 기준 |
| --- | --- |
| `user.deleted` | `user_id` |
| `user.creator-created` | `user_id` |
| `user.followed` | `follow_id` |
| `user.unfollowed` | `follow_id` |

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

## 4. Auth 연동 이벤트

### 4.1 user.deleted

| 항목 | 값 |
| --- | --- |
| topic | `user.deleted` |
| key | `user_id` |
| producer | user-service |
| consumer | auth-service |
| 목적 | 탈퇴 사용자 토큰 무효화 |

Payload:

```json
{
  "user_id": "uuid"
}
```

Auth Service 처리:

1. 사용자 단위 blacklist 등록
2. 해당 사용자 Refresh Token 삭제

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
| `user.creator-created` | 크리에이터 회원가입 트랜잭션 성공 이후 |
| `user.followed` | 팔로우 생성 및 count 증가 성공 이후 |
| `user.unfollowed` | 언팔로우 삭제 및 count 감소 성공 이후 |

트랜잭션 성공 이후 발행이 필요한 이벤트는 after commit 시점 발행을 우선한다.

## 7. 이벤트 발행 실패 처리

현재 정책:

- 이벤트 발행 실패 시 로그 기록
- 핵심 도메인 트랜잭션은 이미 성공한 상태로 간주
- 운영 재처리 체계는 후속 과제

후속 개선:

- Outbox table 도입
- Outbox relay 도입
- DLQ 도입
- 이벤트 재처리 도구 또는 운영 절차 정리

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

- 이벤트 명세 문서 중앙화
- Consumer별 멱등성 저장소 정책 정리
- DLQ 구현
- 재처리 정책 구현
- Outbox 패턴 도입
- 이벤트 schema version 관리
- 이벤트 payload 필드 네이밍 규약 정리

