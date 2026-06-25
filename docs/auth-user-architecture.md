# 인증 및 사용자 도메인 설계 문서

## 1. 문서 목적

이 문서는 6pm Fandom 프로젝트의 인증, 사용자, Gateway, 공통 인증 모듈이 어떤 책임을 갖고 협력하는지 정리한다.

주요 범위는 다음과 같다.

- Gateway의 Access Token 검증 및 내부 인증 컨텍스트 전파
- common 모듈의 `UserIdCard`, `@CurrentIdCard`, HMAC 검증 구조
- Auth Service의 로그인, 토큰 재발급, 로그아웃, 토큰 무효화
- User Service의 회원가입, 프로필, 팔로우, 회원 탈퇴
- User Service와 Auth Service 사이의 이벤트 기반 토큰 무효화 흐름

이 문서는 구현 세부 코드보다 서비스 간 책임 경계와 요청 흐름을 설명하는 것을 목적으로 한다.

## 2. 전체 구조 개요

인증과 사용자 도메인은 다음 네 영역으로 나뉜다.

| 영역 | 주요 책임 |
| --- | --- |
| Gateway | 외부 요청 진입점, Access Token 검증, 인증 제외 경로 관리, 내부 인증 헤더 생성 |
| common-auth | 내부 서비스에서 인증 컨텍스트를 일관되게 검증하고 주입하기 위한 공통 모듈 |
| Auth Service | 로그인, Access Token 및 Refresh Token 발급, 재발급, 로그아웃, 토큰 무효화 |
| User Service | 회원/크리에이터 가입, 회원 정보, 프로필, 팔로우, 회원 탈퇴, 사용자 이벤트 발행 |

외부 클라이언트는 Access Token을 Gateway에 제출한다. Gateway는 Access Token을 검증한 뒤 내부 서비스가 사용할 수 있는 `UserIdCard`를 생성하고 HMAC으로 서명해 downstream 서비스로 전달한다.

Domain Service는 Access Token을 직접 검증하지 않는다. 대신 common-auth가 제공하는 필터를 통해 Gateway가 전달한 `UserIdCard`와 서명을 검증하고, 컨트롤러에서는 `@CurrentIdCard`로 현재 사용자 정보를 주입받는다.

## 3. 인증 요청 흐름

### 3.1 로그인

```text
Client
  -> Gateway
  -> Auth Service
  -> User Service
  -> Auth Service
  -> Client
```

1. 클라이언트가 이메일과 비밀번호로 로그인 요청을 보낸다.
2. Gateway는 로그인 경로를 인증 예외 경로로 통과시킨다.
3. Auth Service는 User Service에서 사용자 정보를 조회한다.
4. Auth Service는 비밀번호를 검증한다.
5. 검증에 성공하면 Access Token과 Refresh Token을 발급한다.
6. Refresh Token은 Redis에 저장한다.

### 3.2 인증이 필요한 API 호출

```text
Client
  Authorization: Bearer {accessToken}
  -> Gateway
     - Access Token 검증
     - 클라이언트가 임의로 보낸 내부 인증 헤더 제거
     - UserIdCard 생성
     - HMAC 서명 생성
  -> Domain Service
     - common-auth 필터에서 HMAC 검증
     - @CurrentIdCard로 UserIdCard 주입
```

Gateway는 인증 성공 후 내부 서비스에 다음 정보를 전달한다.

| Header | 설명 |
| --- | --- |
| `X-Id-Card` | 사용자 식별자와 역할을 담은 내부 인증 컨텍스트 |
| `X-Id-Card-Signature` | `X-Id-Card`에 대한 HMAC 서명 |

내부 서비스는 이 헤더를 신뢰하기 전에 반드시 HMAC 서명을 검증한다.

### 3.3 Access Token 재발급

```text
Client
  -> Gateway
  -> Auth Service
  -> Redis
  -> Auth Service
  -> Client
```

1. 클라이언트가 Refresh Token으로 재발급을 요청한다.
2. Auth Service는 Refresh Token의 유효성을 검증한다.
3. Redis에 저장된 Refresh Token과 요청 토큰을 비교한다.
4. 유효하면 새로운 Access Token을 발급한다.
5. 사용자 단위 blacklist가 존재하면 재발급을 거부한다.

### 3.4 로그아웃

로그아웃은 현재 Refresh Token을 삭제하고, 현재 Access Token을 만료 전까지 사용할 수 없도록 blacklist에 등록하는 흐름이다.

```text
Client
  -> Gateway
  -> Auth Service
  -> Redis
```

로그아웃 응답은 별도 data 없이 `ApiResponse` 성공 응답을 사용한다.

### 3.5 회원 탈퇴와 토큰 무효화

회원 탈퇴는 User Service의 도메인 상태 변경이지만, 인증 토큰 무효화는 Auth Service의 책임이다.

```text
Client
  -> Gateway
  -> User Service
     - 회원 soft delete
     - user.deleted 이벤트 발행
  -> Kafka
  -> Auth Service
     - 사용자 단위 blacklist 등록
     - 해당 사용자의 Refresh Token 삭제
```

회원 탈퇴 이후에는 기존 Access Token을 더 이상 사용할 수 없어야 하며, 기존 Refresh Token으로 Access Token을 재발급할 수 없어야 한다.

## 4. Gateway 인증 처리

Gateway는 외부 요청의 인증과 내부 인증 컨텍스트 생성을 담당한다.

### 4.1 인증 제외 경로

다음 성격의 API는 Access Token 검증 대상에서 제외한다.

- 회원가입
- 로그인
- 토큰 재발급
- 공개 조회 API
- actuator 등 운영 확인 경로

인증 제외 경로는 Gateway에서 관리한다. 내부 서비스 컨트롤러에서 `@CurrentIdCard`를 요구하는 API는 Gateway 인증 정책과 맞아야 한다.

### 4.2 내부 인증 헤더 제거

Gateway는 클라이언트가 임의로 전달한 내부 인증 헤더를 제거한다.

제거 대상 예시는 다음과 같다.

- `X-User-*`
- `X-Id-Card-*`
- `X-Internal-*`

이는 클라이언트가 내부 사용자 컨텍스트를 위조하는 것을 막기 위한 정책이다.

### 4.3 UserIdCard 생성 및 전파

Access Token 검증에 성공하면 Gateway는 token claim을 기반으로 `UserIdCard`를 생성한다.

`UserIdCard`는 최소한의 인증 컨텍스트만 담는다.

- `userId`
- `role`

이메일, 닉네임, 주소 등 추가 사용자 정보는 매 요청마다 헤더로 전달하지 않는다. 필요한 서비스가 `userId`를 기준으로 User Service를 조회한다.

## 5. common-auth 구조

common-auth는 각 도메인 서비스가 내부 인증 컨텍스트를 같은 방식으로 사용할 수 있게 하는 공통 모듈이다.

자세한 헤더 규약과 사용법은 [common-auth.md](./common-auth.md)를 따른다.

### 5.1 UserIdCard

`UserIdCard`는 Gateway가 생성하고 내부 서비스가 검증해 사용하는 사용자 컨텍스트 객체다.

주요 책임은 다음과 같다.

- 현재 사용자 id 제공
- 현재 사용자 role 제공
- role 판별 편의 메서드 제공
- 대상 userId와 현재 사용자가 같은지 확인

예시:

```java
idCard.getUserId();
idCard.getRole();
idCard.isMember();
idCard.isCreator();
idCard.isMaster();
idCard.isMe(userId);
```

`isMe(UUID userId)`는 입력값이 `null`이면 `false`를 반환한다.

### 5.2 @CurrentIdCard

내부 서비스 컨트롤러는 `@CurrentIdCard`를 사용해 현재 사용자 컨텍스트를 주입받는다.

```java
@PatchMapping("/me")
public ApiResponse<?> update(@CurrentIdCard UserIdCard idCard) {
    UUID userId = idCard.getUserId();
    ...
}
```

`@CurrentIdCard`는 인증이 필요한 API에서만 사용한다. 비회원 접근이 가능한 API에서 선택적으로 사용자 정보를 참고해야 하는 경우에는 별도 정책이 필요하다.

### 5.3 HMAC 검증

Gateway와 Domain Service는 같은 HMAC secret을 공유한다.

Domain Service는 다음 값을 검증한다.

- `X-Id-Card`
- `X-Id-Card-Signature`

서명이 유효하지 않으면 요청을 거부한다. 이를 통해 Gateway를 우회하거나 내부 인증 헤더를 위조한 요청을 방지한다.

## 6. Auth Service 설계

Auth Service는 인증 토큰의 생성, 저장, 폐기 책임을 가진다.

### 6.1 책임 범위

Auth Service의 책임은 다음과 같다.

- 로그인 요청 처리
- Access Token 발급
- Refresh Token 발급 및 Redis 저장
- Access Token 재발급
- 로그아웃
- Access Token blacklist 등록
- 사용자 단위 토큰 무효화
- User Service의 사용자 상태 조회

Auth Service는 사용자 프로필이나 팔로우 같은 사용자 도메인 데이터를 직접 관리하지 않는다.

### 6.2 Redis key 정책

현재 Auth Service는 DB 없이 Redis를 사용해 토큰 상태를 관리한다.

| 용도 | 예시 key | 설명 |
| --- | --- | --- |
| Refresh Token | `refresh:{userId}:{tokenId}` | 사용자별 Refresh Token 저장 |
| Access Token blacklist | `blacklist:access:{jti}` | 로그아웃된 Access Token 차단 |
| 사용자 단위 blacklist | `blacklist:user:{userId}` | 탈퇴 등으로 사용자 전체 토큰 차단 |

Redis key의 TTL은 토큰 만료 정책과 맞춰 설정한다.

### 6.3 사용자 단위 토큰 무효화

회원 탈퇴, 계정 정지, 권한 변경처럼 기존 토큰을 계속 허용하면 안 되는 경우 사용자 단위 무효화를 사용한다.

사용자 단위 blacklist가 등록되면 다음 요청이 실패해야 한다.

- 기존 Access Token으로 Gateway 인증
- 기존 Refresh Token으로 Access Token 재발급

## 7. User Service 설계

User Service는 사용자 도메인의 source of truth 역할을 한다.

### 7.1 책임 범위

User Service의 책임은 다음과 같다.

- 일반 회원가입
- 크리에이터 회원가입
- 회원 정보 수정
- 프로필 생성, 조회, 수정
- 팔로우, 언팔로우
- 팔로워/팔로잉 조회
- 회원 탈퇴
- 사용자 상태 변경 이벤트 발행

### 7.2 회원가입

일반 회원가입은 `User`와 `Profile`을 생성한다.

크리에이터 회원가입은 하나의 트랜잭션에서 `User`, `Creator`, `Profile`을 생성한다.

닉네임은 `Profile`의 속성으로 관리한다.

### 7.3 프로필

프로필은 외부에 공개되는 사용자 정보다.

일반적으로 외부 조회가 필요한 사용자 정보는 `User`가 아니라 `Profile` API를 통해 제공한다.

프로필 수정은 로그인 사용자 본인만 가능하다. 일반 회원과 크리에이터는 수정 가능한 필드가 다를 수 있다.

### 7.4 팔로우

팔로우는 팬이 크리에이터를 구독하는 관계다.

| 필드 | 의미 |
| --- | --- |
| followerId | 팔로우를 수행한 사용자 |
| followeeId | 팔로우 대상 크리에이터 |

팔로우 관계는 중복될 수 없다. 동시 요청에 대한 중복 방지는 DB unique 제약을 기준으로 한다.

`Follow`는 soft delete 대상이 아니다. 언팔로우 시 동일 조합으로 다시 팔로우할 수 있어야 하므로 hard delete 정책을 사용한다.

### 7.5 회원 탈퇴

회원 탈퇴는 User Service에서 사용자 상태를 삭제 상태로 변경하고, 관련 도메인 이벤트를 발행한다.

회원 탈퇴 이후에는 다음 상태가 보장되어야 한다.

- 탈퇴 사용자의 기존 Access Token 사용 불가
- 탈퇴 사용자의 기존 Refresh Token 재발급 불가
- 필요한 downstream 서비스에 사용자 삭제 이벤트 전파

## 8. 이벤트 설계

User Service는 사용자 도메인 상태 변경을 Kafka 이벤트로 발행한다.

### 8.1 Auth 연동 이벤트

| Topic | Key | Payload | 목적 |
| --- | --- | --- | --- |
| `user.deleted` | `user_id` | `{ "user_id": "uuid" }` | 탈퇴 사용자 토큰 무효화 |

Auth Service는 `user.deleted` 이벤트를 소비해 사용자 단위 토큰 무효화를 수행한다.

### 8.2 Chat 연동 이벤트

| Topic | Key | Payload | 목적 |
| --- | --- | --- | --- |
| `user.creator-created` | `user_id` | `{ "user_id": "uuid", "nickname": "name" }` | 크리에이터 생성 시 채팅방 자동 생성 |
| `user.followed` | `follow_id` | `{ "follow_id": "uuid", "follower_id": "uuid", "followee_id": "uuid" }` | 팔로우 발생 시 채팅방 입장 |
| `user.unfollowed` | `follow_id` | `{ "follow_id": "uuid", "follower_id": "uuid", "followee_id": "uuid" }` | 언팔로우 발생 시 채팅방 나가기 |

### 8.3 이벤트 발행 시점

도메인 상태 변경이 성공한 뒤 이벤트를 발행한다.

현재 MVP 단계에서는 서비스 로직 내에서 Kafka 발행을 수행한다. 이벤트 발행 실패가 핵심 트랜잭션 실패로 이어지면 안 되는 경우에는 실패 로그를 남기고, Outbox 패턴은 후속 고도화 대상으로 둔다.

### 8.4 이벤트 실패 로그

User Service에서 발행하는 Kafka 이벤트는 발행 실패 시 topic, key, payload를 로그로 남긴다.

로그에는 토큰, 비밀번호, 개인 민감정보를 남기지 않는다.

## 9. 데이터 모델

### 9.1 User

`User`는 인증과 계정 상태의 기준이 되는 엔티티다.

주요 속성:

- id
- email
- password
- role
- status
- address
- audit fields

### 9.2 Creator

`Creator`는 크리에이터 사용자에 대한 확장 정보다.

주요 속성:

- id
- user
- agencyName

### 9.3 Profile

`Profile`은 외부 공개 사용자 정보다.

주요 속성:

- id
- user
- nickname
- birthday
- profileMessage
- profileImage
- bannerImage
- followerCount
- followingCount

### 9.4 Follow

`Follow`는 사용자와 크리에이터 사이의 팔로우 관계다.

주요 속성:

- id
- followerId
- followeeId
- createdAt
- createdBy

`Follow`는 hard delete 정책을 사용한다.

## 10. API 설계 요약

### 10.1 Auth API

| API | 인증 | 설명 |
| --- | --- | --- |
| `POST /api/v1/auth/login` | 불필요 | 로그인 |
| `POST /api/v1/auth/reissue` | 불필요 | Access Token 재발급 |
| `POST /api/v1/auth/logout` | 필요 | 로그아웃 |

### 10.2 User API

| API | 인증 | 설명 |
| --- | --- | --- |
| `POST /api/v1/members` | 불필요 | 일반 회원가입 |
| `POST /api/v1/creators` | 불필요 | 크리에이터 회원가입 |
| `PATCH /api/v1/members/me` | 필요 | 일반 회원 정보 수정 |
| `PATCH /api/v1/creators/me` | 필요 | 크리에이터 정보 수정 |
| `DELETE /api/v1/members/me` | 필요 | 회원 탈퇴 |

### 10.3 Profile API

| API | 인증 | 설명 |
| --- | --- | --- |
| `GET /api/v1/members/{memberId}/profile` | 불필요 | 일반 회원 프로필 조회 |
| `GET /api/v1/creators/{creatorId}/profile` | 불필요 | 크리에이터 프로필 조회 |
| `PATCH /api/v1/members/me/profile` | 필요 | 일반 회원 프로필 수정 |
| `PATCH /api/v1/creators/me/profile` | 필요 | 크리에이터 프로필 수정 |

### 10.4 Follow API

| API | 인증 | 설명 |
| --- | --- | --- |
| `POST /api/v1/follows/{creatorId}` | 필요 | 팔로우 |
| `DELETE /api/v1/follows/{creatorId}` | 필요 | 언팔로우 |
| `GET /api/v1/follows/{creatorId}/followers` | 정책에 따름 | 팔로워 조회 |
| `GET /api/v1/members/{memberId}/followings` | 정책에 따름 | 팔로잉 조회 |

## 11. 예외 및 응답 정책

모든 서비스는 공통 응답 형식인 `ApiResponse`를 사용한다.

도메인 예외는 `CustomException`과 `ErrorCode`를 통해 표현한다.

주요 예외 정책:

- 인증 실패: 401
- 권한 부족: 403
- 존재하지 않는 리소스: 404
- 중복 리소스: 409 또는 팀 규약에 따른 400 계열
- 요청 값 검증 실패: 400
- 서버 내부 오류: 500

회원가입, 프로필 수정, 팔로우 등 사용자 요청 API는 입력값 검증 실패 시 400을 반환한다.

## 12. 보안 고려사항

### 12.1 Access Token 검증 책임

Access Token 검증은 Gateway가 담당한다.

Domain Service는 Access Token을 직접 검증하지 않고, Gateway가 전달한 내부 인증 컨텍스트의 HMAC 서명을 검증한다.

### 12.2 내부 헤더 위조 방지

Gateway는 외부 클라이언트가 전달한 내부 인증 헤더를 제거한 뒤 새로 생성한다.

Domain Service는 HMAC 검증에 실패한 내부 인증 헤더를 신뢰하지 않는다.

### 12.3 로그 보안

로그에 남기면 안 되는 정보:

- Access Token
- Refresh Token
- 비밀번호
- HMAC secret
- Redis 접속 비밀번호

이벤트 발행 실패 로그에도 민감정보를 포함하지 않는다.

## 13. 테스트 전략

### 13.1 단위 테스트

서비스 로직은 repository, publisher, external client를 mock으로 분리해 검증한다.

주요 검증 대상:

- 회원가입 시 User/Profile 생성
- 크리에이터 가입 시 User/Creator/Profile 생성
- 프로필 수정 검증
- 팔로우/언팔로우 중복 및 권한 검증
- 토큰 재발급 및 무효화 검증

### 13.2 API 스모크 테스트

로컬 통합 확인 시 Gateway를 경유해 다음 흐름을 검증한다.

- 회원가입
- 로그인
- 인증 API 호출
- 프로필 조회/수정
- 팔로우/언팔로우
- 로그아웃
- 회원 탈퇴 후 기존 토큰 차단
- Refresh Token 재발급 차단

### 13.3 Redis 확인

Auth 관련 Redis key를 확인한다.

예시:

```powershell
docker exec 6pm-redis redis-cli -a fandom_redis_pw EXISTS "blacklist:user:${userId}"
docker exec 6pm-redis redis-cli -a fandom_redis_pw --scan --pattern "refresh:${userId}:*"
docker exec 6pm-redis redis-cli -a fandom_redis_pw TTL "blacklist:user:${userId}"
```

### 13.4 Kafka 확인

Kafka UI에서 User Service가 발행한 이벤트 topic, key, payload를 확인한다.

확인 대상:

- `user.deleted`
- `user.creator-created`
- `user.followed`
- `user.unfollowed`

## 14. 현재 한계와 후속 과제

현재 설계에서 후속으로 정리할 항목은 다음과 같다.

- soft delete 조회 정책 전역 정리
- soft delete row와 unique 제약 충돌 정책 정리
- Outbox 패턴 도입 검토
- 이벤트 소비 멱등성 저장소 정책
- DLQ 및 재처리 정책 구현
- Gateway 장애 대응
- Gateway RBAC 고도화
- 운영 알림과 모니터링 기준 정리
- User Service repository infrastructure 분리 확대
- 인증 선택 API에서 optional id card 처리 방식 정리

