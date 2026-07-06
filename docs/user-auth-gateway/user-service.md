# User Service 설계

## 1. 문서 목적

이 문서는 User Service의 도메인 책임, 주요 엔티티, API 흐름, 삭제 정책, 내부 연동 API, repository 계층 구조를 정리한다.

인증과 Gateway를 포함한 전체 흐름은 [auth-user-architecture.md](./auth-user-architecture.md)를 따른다.

## 2. User Service 책임 범위

User Service는 사용자 도메인의 source of truth다.

주요 책임은 다음과 같다.

- 일반 회원가입
- 크리에이터 회원가입
- 회원 정보 수정
- 공개 프로필 조회
- 프로필 수정
- 팔로우
- 언팔로우
- 팔로워/팔로잉 조회
- Feed 팬아웃 및 타임라인 구성을 위한 내부 Follow 조회 API 제공
- 회원 탈퇴
- 사용자 도메인 이벤트 발행

Auth Service는 인증 토큰을 담당하고, User Service는 사용자 도메인 상태를 담당한다.

## 3. 도메인 모델

### 3.1 User

`User`는 계정의 기준 엔티티다.

주요 속성:

- id
- email
- password
- role
- status
- zipCode
- address1
- address2

`User`는 soft delete 대상이다.

### 3.2 Creator

`Creator`는 크리에이터 계정의 추가 정보다.

주요 속성:

- id
- user
- agencyName

크리에이터 가입 시 `User`와 함께 생성된다.

### 3.3 Profile

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

회원가입 시 Profile은 자동 생성된다.

`followerCount`와 `followingCount`는 팔로우/언팔로우 시 함께 갱신되는 집계값이다. Feed 팬아웃 여부 판단, 대형 크리에이터 여부 판단처럼 실시간 full count보다 빠른 조회가 필요한 내부 API에서는 이 집계값을 사용한다.

### 3.4 Follow

`Follow`는 follower와 followee 사이의 관계다.

| 필드 | 의미 |
| --- | --- |
| followerId | 팔로우를 수행한 사용자 |
| followeeId | 팔로우 대상 크리에이터 |

`followeeId`는 크리에이터 사용자 id를 의미한다.

`Follow`는 soft delete 대상이 아니다. 동일 follower/followee 조합으로 다시 팔로우할 수 있어야 하므로 언팔로우 시 hard delete를 사용한다.

## 4. 일반 회원가입

일반 회원가입은 다음 엔티티를 생성한다.

- `User`
- `Profile`

처리 흐름:

1. 이메일 중복 확인
2. 닉네임 중복 확인
3. 비밀번호 암호화
4. `User` 저장
5. `Profile` 저장
6. 회원가입 응답 반환

`User` 저장과 `Profile` 저장은 하나의 트랜잭션에서 처리한다.

## 5. 크리에이터 회원가입

크리에이터 회원가입은 다음 엔티티를 생성한다.

- `User`
- `Creator`
- `Profile`

처리 흐름:

1. 이메일 중복 확인
2. 닉네임 중복 확인
3. 비밀번호 암호화
4. `User` 저장
5. `Creator` 저장
6. `Profile` 저장
7. `user.creator-created` 이벤트 발행
8. 회원가입 응답 반환

크리에이터 생성 이벤트는 채팅방 자동 생성에 사용된다.

## 6. 회원 정보 수정

회원 정보 수정은 로그인 사용자의 계정 정보를 변경한다.

수정 가능 정보 예시:

- email
- password
- zipCode
- address1
- address2
- agencyName

모든 수정 필드가 `null`이면 요청 검증 실패로 처리한다.

비밀번호가 포함된 경우 암호화 후 저장한다.

## 7. 프로필 생성, 조회, 수정

### 7.1 프로필 생성

Profile은 회원가입 시 자동 생성된다.

일반 회원과 크리에이터 모두 Profile을 가진다.

### 7.2 프로필 조회

프로필 조회는 공개 API로 취급한다.

대표 API:

- `GET /api/v1/members/{memberId}/profile`
- `GET /api/v1/creators/{creatorId}/profile`

외부 서비스나 클라이언트가 사용자 공개 정보를 필요로 할 때는 `User` 직접 조회보다 Profile 조회를 우선한다.

### 7.3 프로필 수정

프로필 수정은 로그인 사용자 본인만 가능하다.

수정 가능 정보 예시:

- nickname
- birthday
- profileMessage
- profileImage
- bannerImage

일반 회원은 birthday 수정이 제한될 수 있고, 크리에이터는 birthday를 포함한 공개 프로필을 관리할 수 있다.

공백 문자열은 검증 실패로 처리한다.

## 8. 팔로우/언팔로우

### 8.1 팔로우

팔로우는 로그인 사용자가 크리에이터를 follow하는 기능이다.

처리 흐름:

1. follower 사용자 조회
2. followee 크리에이터 사용자 조회
3. 자기 자신 팔로우 방지
4. 중복 팔로우 확인
5. Follow 저장
6. follower/followee Profile count 증가
7. `user.followed` 이벤트 발행

중복 팔로우는 애플리케이션 레벨 확인과 DB unique 제약으로 방어한다.

### 8.2 언팔로우

언팔로우는 기존 Follow row를 hard delete한다.

처리 흐름:

1. Follow 조회
2. Follow 삭제
3. follower/followee Profile count 감소
4. `user.unfollowed` 이벤트 발행

count 감소는 0 미만으로 내려가지 않도록 처리한다.

## 9. 내부 Follow 조회 API

Feed 서비스는 팬아웃 및 타임라인 조회를 위해 User Service의 Follow 관계를 내부 API로 조회한다.

내부 API는 Gateway 라우팅 대상이 아니며, 서비스 간 직접 호출 전용이다. 응답 형식은 공통 `ApiResponse`를 사용하고, 커서 응답은 `CursorPageResponse<T>`를 사용한다.

### 9.1 팬아웃 여부 판단용 팔로워 수 조회

```http
GET /internal/v1/follows/{authorId}/count
```

용도:

- Feed 서비스가 게시글 작성 또는 삭제 시 팬아웃 여부를 판단한다.
- 알림 발행 대상 규모를 판단하는 기준으로 사용할 수 있다.

정책:

- `authorId`는 크리에이터 사용자 id다.
- 반환값은 `Profile.followerCount` 집계값이다.
- 실시간 count 쿼리보다 빠른 판단을 우선한다.
- 집계값 정합성 검증이나 재집계는 후속 운영 과제로 분리한다.

응답 예시:

```json
{
  "status": 200,
  "message": "성공",
  "data": 12345
}
```

### 9.2 팬아웃 대상 조회용 팔로워 ID 조회

```http
GET /internal/v1/follows/{authorId}/followers?cursor={followId}&size={size}
```

용도:

- Feed 서비스가 특정 크리에이터의 팔로워에게 피드 아이템을 팬아웃할 때 사용한다.
- Notification 서비스로 알림 발행 대상을 넘기기 전 대상 userId 목록을 페이지 단위로 조회한다.

정책:

- `cursor`는 `follow.id` 기준이다.
- `cursor`가 없으면 첫 페이지부터 조회한다.
- 정렬은 `follow.id ASC` 기준이다. UUIDv7 특성상 생성 시간 순서와 유사하게 동작한다.
- `size` 허용 범위는 1 이상 1000 이하다.
- 조회 결과는 프로필 정보를 포함하지 않고 follower userId 목록만 반환한다.
- `hasNext`는 `size + 1` 조회로 판단하고, `nextCursor`는 반환된 마지막 `follow.id`다.

응답 예시:

```json
{
  "status": 200,
  "message": "성공",
  "data": {
    "content": ["<userId>", "<userId>"],
    "nextCursor": "<followId | null>",
    "hasNext": true
  }
}
```

### 9.3 타임라인 조회용 팔로잉 목록 조회

```http
GET /internal/v1/follows/{userId}/followings?cursor={followId}&size={size}&minFollowerCount={count}
```

용도:

- Feed 서비스가 사용자의 타임라인을 구성할 때 팔로잉 목록을 조회한다.
- 각 팔로잉 대상이 대형 크리에이터인지 함께 판단해 Fan-out on Write 대상과 Pull 조회 대상을 구분할 수 있다.

정책:

- `userId`는 타임라인을 조회하는 사용자 id다.
- `cursor`는 `follow.id` 기준이다.
- `size` 허용 범위는 1 이상 1000 이하다.
- 응답 content는 `authorId`, `isLarge`를 포함한다.
- `isLarge`는 팔로잉 대상의 `Profile.followerCount > minFollowerCount` 기준이다.
- `minFollowerCount`는 호출 측인 Feed 서비스가 결정한다.

응답 content 예시:

```json
{
  "authorId": "<creatorUserId>",
  "isLarge": true
}
```

### 9.4 대형 크리에이터 팔로잉 조회

```http
GET /internal/v1/follows/{userId}/followings/large?cursor={followId}&size={size}&minFollowerCount={count}
```

용도:

- Feed 서비스가 팔로잉 중 대형 크리에이터만 별도로 조회할 때 사용한다.
- 대형 크리에이터의 게시글은 팬아웃 대신 조회 시점 Pull 방식으로 합성할 수 있다.

정책:

- 대형 여부는 `Profile.followerCount > minFollowerCount` 기준이다.
- 경계값은 대형으로 보지 않는다. 예를 들어 `minFollowerCount=10000`이면 `10000`은 false, `10001`부터 true다.
- 필터링은 애플리케이션이 아니라 DB 쿼리에서 수행한다. 커서 페이징에서 페이지 크기가 흔들리는 문제를 방지하기 위함이다.
- 응답 content는 대형 크리에이터 authorId 목록만 포함한다.

## 10. 회원 탈퇴

회원 탈퇴는 로그인 사용자의 계정을 삭제 상태로 전환한다.

처리 흐름:

1. 현재 사용자 조회
2. 사용자 상태 검증
3. User soft delete
4. 관련 사용자 삭제 이벤트 발행
5. 성공 응답 반환

회원 탈퇴 이후에는 Auth Service가 `user.deleted` 이벤트를 소비해 토큰을 무효화한다.

## 11. soft delete / hard delete 정책

| 엔티티 | 삭제 정책 | 이유 |
| --- | --- | --- |
| User | soft delete | 계정 이력 보존 |
| Creator | soft delete | 사용자 확장 정보 이력 보존 |
| Profile | soft delete | 공개 프로필 이력 보존 |
| Follow | hard delete | unique 제약과 재팔로우 요구사항 |

soft delete 조회 정책은 아직 전역적으로 완전히 정리되지 않았다. 후속으로 repository 조회 정책, unique 제약, partial unique index 도입 여부를 팀 차원에서 정리해야 한다.

## 12. Repository 계층 구조

User Service repository는 domain 포트와 infrastructure 구현체로 분리한다.

구조:

```text
domain/repository
  UserRepository
  CreatorRepository
  ProfileRepository
  FollowRepository

infrastructure/repository
  UserJpaRepository
  UserRepositoryImpl
  CreatorJpaRepository
  CreatorRepositoryImpl
  ProfileJpaRepository
  ProfileRepositoryImpl
  FollowJpaRepository
  FollowRepositoryImpl
```

domain repository는 `JpaRepository`, `@Query`, `@EntityGraph`, `@Modifying` 같은 Spring Data JPA 세부사항을 직접 알지 않는다.

JPA 세부 구현은 infrastructure 계층에서 담당한다.

Follow 내부 조회 API의 커서 페이징 쿼리는 infrastructure 계층의 JPA 구현체가 담당하고, domain repository는 projection과 조회 의도만 노출한다.

## 13. 예외 및 응답 정책

User Service는 공통 응답 형식인 `ApiResponse`를 사용한다.

주요 예외:

- 이메일 중복
- 닉네임 중복
- 사용자 없음
- 크리에이터 없음
- 권한 없음
- 자기 자신 팔로우
- 중복 팔로우
- 팔로우 관계 없음
- 요청 필드 검증 실패
- 내부 Follow 조회 size 범위 위반

도메인 예외는 `CustomException`과 `ErrorCode`로 표현한다.

## 14. 테스트 방법

단위 테스트:

- 일반 회원가입
- 크리에이터 회원가입
- 회원 정보 수정
- 프로필 조회/수정
- 팔로우
- 언팔로우
- 팬아웃용 팔로워 수 조회
- 팬아웃용 팔로워 ID 커서 조회
- 타임라인용 팔로잉 조회
- 대형 크리에이터 팔로잉 조회
- 회원 탈퇴
- 사용자 이벤트 발행

API 스모크 테스트:

- `POST /api/v1/members`
- `POST /api/v1/creators`
- `POST /api/v1/auth/login`
- `GET /api/v1/members/{memberId}/profile`
- `GET /api/v1/creators/{creatorId}/profile`
- `PATCH /api/v1/members/me/profile`
- `POST /api/v1/follows/{creatorId}`
- `DELETE /api/v1/follows/{creatorId}`
- `GET /internal/v1/follows/{authorId}/count`
- `GET /internal/v1/follows/{authorId}/followers`
- `GET /internal/v1/follows/{userId}/followings`
- `GET /internal/v1/follows/{userId}/followings/large`
- `DELETE /api/v1/members/me`

## 15. 후속 과제

- soft delete 조회 정책 전역 정리
- soft delete row와 unique 제약 충돌 정책 정리
- Follow count 동시성 고도화
- Follow count 집계값 재검증/재집계 정책 정리
- repository 계층 분리 적용 범위 확대
- 외부 공개 사용자 정보는 Profile 중심으로 정리
- 이벤트 발행 Outbox 패턴 검토
