# Gateway CORS 및 RBAC 구현 정책

## 1. 문서 목적

이 문서는 Gateway Service의 CORS, 인증 예외 경로, RBAC, 필터 에러 응답 정책을 정리한다.

Gateway 인증의 상위 흐름은 [gateway-auth.md](./gateway-auth.md)를 따른다.
`UserIdCard`, `@CurrentIdCard`, HMAC 검증 규약은 [common-auth.md](./common-auth.md)를 따른다.

## 2. CORS 정책

Gateway는 브라우저 preflight 요청을 인증 예외로 처리한다.

- `OPTIONS` 요청은 Access Token 검증 없이 통과
- CORS 설정은 Gateway `application.yml`에서 관리
- 로컬 개발 환경에서 허용하는 Origin
  - `http://localhost:3000`
  - `http://localhost:5173`
  - `http://127.0.0.1:3000`
  - `http://127.0.0.1:5173`
- 허용 Method
  - `GET`
  - `POST`
  - `PUT`
  - `PATCH`
  - `DELETE`
  - `OPTIONS`
- 인증 헤더 포함 요청을 위해 credentials 허용

운영 환경의 Origin은 로컬 개발 설정을 그대로 사용하지 않고 Config Server 또는 환경변수 기반으로 분리한다.

## 3. 인증 예외 경로

현재 Gateway 인증 예외 경로는 `GatewaySecurityRules`에서 관리한다.

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/v1/auth/login` | 로그인 |
| `POST` | `/api/v1/auth/reissue` | Access Token 재발급 |
| `POST` | `/api/v1/members` | 일반 회원가입 |
| `POST` | `/api/v1/creators` | 크리에이터 회원가입 |
| `GET` | `/api/v1/members/{userId}/profile` | 일반 회원 공개 프로필 조회 |
| `GET` | `/api/v1/creators/{userId}/profile` | 크리에이터 공개 프로필 조회 |
| `OPTIONS` | `/**` | CORS preflight |

## 4. RBAC 적용 정책

Gateway RBAC는 URL과 HTTP Method 기준의 1차 접근 제어이다.
소유자 검증, 리소스 존재 여부, 도메인별 세부 정책 검증은 각 도메인 서비스에서 수행한다.

| Method | Path | 허용 Role |
| --- | --- | --- |
| `PATCH` | `/api/v1/members/me` | `MEMBER` |
| `PATCH` | `/api/v1/members/me/profile` | `MEMBER` |
| `DELETE` | `/api/v1/members/me` | `MEMBER`, `CREATOR` |
| `PATCH` | `/api/v1/creators/me` | `CREATOR` |
| `PATCH` | `/api/v1/creators/me/profile` | `CREATOR` |
| `POST` | `/api/v1/follows/{creatorId}` | `MEMBER`, `CREATOR` |
| `DELETE` | `/api/v1/follows/{creatorId}` | `MEMBER`, `CREATOR` |
| `POST` | `/api/v1/feeds/posts` | `CREATOR` |
| `PUT` | `/api/v1/feeds/posts/{postId}` | `CREATOR` |
| `DELETE` | `/api/v1/feeds/posts/{postId}` | `CREATOR`, `MASTER` |
| `POST` | `/api/v1/feeds/posts/{postId}/comments` | `MEMBER`, `CREATOR` |
| `PUT` | `/api/v1/feeds/comments/{commentId}` | `MEMBER`, `CREATOR` |
| `POST` | `/api/v1/feeds/posts/{postId}/likes` | `MEMBER`, `CREATOR` |
| `DELETE` | `/api/v1/feeds/posts/{postId}/likes` | `MEMBER`, `CREATOR` |
| `POST` | `/api/v1/feeds/likes/users` | `MEMBER`, `CREATOR` |

위 표에 명시되지 않은 인증 필요 API는 기본적으로 `MEMBER`, `CREATOR`, `MASTER` 인증 사용자를 허용한다.
이 기본 정책은 도메인별 권한 정책이 확정되면 더 좁은 규칙으로 보강한다.

## 5. Gateway 에러 응답

Gateway 필터에서 직접 응답을 작성하는 경우에도 공통 응답 형식인 `ApiResponse`를 사용한다.
응답 메시지는 문자열을 직접 하드코딩하지 않고 common의 `ErrorCode`를 기준으로 생성한다.

| 상황 | ErrorCode | HTTP Status |
| --- | --- | --- |
| Authorization Header 없음 또는 Bearer 형식 아님 | `CommonErrorCode.UNAUTHORIZED` | 401 |
| Access Token 파싱 실패 | `CommonErrorCode.INVALID_ID_CARD` | 401 |
| Access Token blacklist 또는 user blacklist hit | `CommonErrorCode.INVALID_ID_CARD` | 401 |
| RBAC 인증 정보 없음 | `CommonErrorCode.UNAUTHORIZED` | 401 |
| RBAC 권한 부족 | `CommonErrorCode.FORBIDDEN` | 403 |

응답 생성 기준:

```java
ApiResponse.error(errorCode.getStatus(), errorCode.getMessage())
```

## 6. 확인한 스모크 테스트

로컬 통합 환경에서 다음 흐름을 확인했다.

| 테스트 | 기대 결과 |
| --- | --- |
| `OPTIONS /api/v1/members/me` with `Origin: http://localhost:3000` | 200, `Access-Control-Allow-Origin` 응답 |
| 비인증 `PATCH /api/v1/members/me` | 401 |
| `MEMBER`의 `PATCH /api/v1/creators/me` | 403 |
| `CREATOR`의 `PATCH /api/v1/creators/me` | 200 |
| `MEMBER`의 `PATCH /api/v1/members/me` | 200 |
| `MEMBER`의 `POST /api/v1/feeds/posts` | 403 |
| `CREATOR`의 `POST /api/v1/feeds/posts` | 201 |

팔로우 API는 Gateway에서 `MEMBER`, `CREATOR`를 모두 허용한다.
다만 `CREATOR -> CREATOR` 팔로우를 user-service 내부 검증이 막는 문제는 별도 버그 이슈로 분리한다.
