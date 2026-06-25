# Gateway 인증 설계

## 1. 문서 목적

이 문서는 Gateway Service가 외부 요청을 인증하고, 내부 도메인 서비스로 인증 컨텍스트를 전달하는 방식을 정리한다.

Gateway 인증 흐름의 상위 구조는 [auth-user-architecture.md](./auth-user-architecture.md)를 따른다. `UserIdCard`, `@CurrentIdCard`, HMAC 검증 규약의 상세는 [common-auth.md](./common-auth.md)를 따른다.

## 2. Gateway의 책임

Gateway는 외부 클라이언트 요청의 첫 진입점이다.

주요 책임은 다음과 같다.

- 요청 경로에 따른 서비스 라우팅
- 인증 제외 경로 판단
- Access Token 검증
- 클라이언트가 보낸 내부 인증 헤더 제거
- `UserIdCard` 생성
- `UserIdCard` HMAC 서명 생성
- downstream 서비스로 내부 인증 헤더 전달
- URL과 HTTP Method 기준의 1차 RBAC 적용

Gateway는 사용자 도메인 데이터를 직접 관리하지 않는다. 사용자 상태나 상세 정보가 필요한 경우 Auth Service 또는 User Service와의 계약을 통해 처리한다.

## 3. 인증 제외 경로 정책

인증 제외 경로는 Access Token 없이 접근 가능한 API다.

대표 예시는 다음과 같다.

- 회원가입
- 로그인
- Access Token 재발급
- 공개 프로필 조회
- 공개 게시글 조회
- actuator 등 운영 확인 경로

인증 제외 경로는 Gateway에서 관리한다. 내부 서비스에서 `@CurrentIdCard`가 필요한 API를 인증 제외 경로로 열면 런타임 오류 또는 인증 컨텍스트 누락이 발생할 수 있으므로, Gateway 정책과 컨트롤러 시그니처를 함께 확인해야 한다.

## 4. Access Token 검증 흐름

```text
Client
  Authorization: Bearer {accessToken}
  -> Gateway
     1. Authorization Header 확인
     2. Bearer Token 추출
     3. JWT 서명 검증
     4. 만료 시간 검증
     5. 필수 claim 검증
     6. 토큰 blacklist 검증
     7. 사용자 단위 blacklist 검증
```

Access Token 검증에 실패하면 downstream 서비스로 요청을 전달하지 않는다.

검증 대상 claim은 최소한 다음 값을 포함한다.

- subject 또는 userId
- role
- status
- jti
- issuedAt
- expiration

## 5. 내부 인증 헤더 제거 정책

Gateway는 클라이언트가 임의로 전달한 내부 인증 헤더를 제거한다.

제거 대상 예시는 다음과 같다.

- `X-Id-Card`
- `X-Id-Card-Signature`
- `X-User-*`
- `X-Internal-*`

이 정책은 외부 클라이언트가 내부 사용자 컨텍스트를 위조하는 것을 막기 위한 방어선이다.

## 6. UserIdCard 생성 및 HMAC 서명

Access Token 검증에 성공하면 Gateway는 token claim을 기반으로 `UserIdCard`를 생성한다.

`UserIdCard`에는 최소 인증 컨텍스트만 담는다.

| 필드 | 설명 |
| --- | --- |
| `userId` | 현재 인증 사용자 식별자 |
| `role` | 현재 인증 사용자 역할 |

Gateway는 `UserIdCard`를 JSON으로 직렬화한 뒤 HMAC 서명을 생성한다.

```text
X-Id-Card: {serialized UserIdCard}
X-Id-Card-Signature: {hmac signature}
```

Domain Service는 common-auth 필터를 통해 이 서명을 검증한다.

## 7. downstream 서비스 전달 헤더

Gateway가 downstream 서비스로 전달하는 인증 헤더는 다음과 같다.

| Header | 설명 |
| --- | --- |
| `X-Id-Card` | 인증 사용자 컨텍스트 |
| `X-Id-Card-Signature` | `X-Id-Card` HMAC 서명 |

개별 API 명세에는 이 내부 헤더를 반복해서 적지 않는다. 인증 컨텍스트 규약은 common-auth 문서를 기준으로 한다.

## 8. RBAC 정책

Gateway RBAC는 URL과 HTTP Method 기준의 1차 접근 제어다.

예시:

| API 성격 | 허용 역할 |
| --- | --- |
| 일반 회원 API | `MEMBER`, `CREATOR`, `MASTER` |
| 크리에이터 전용 API | `CREATOR`, `MASTER` |
| 관리자 API | `MASTER` |

Gateway RBAC는 1차 방어선이다. 리소스 소유자 검증은 각 도메인 서비스에서 수행한다.

예를 들어 `PATCH /members/me/profile`은 Gateway에서 로그인 사용자 여부만 확인하고, 실제 수정 대상이 본인인지 여부는 User Service가 `UserIdCard`를 기준으로 검증한다.

## 9. 장애 및 예외 응답 정책

Gateway 인증 실패는 공통 응답 형식으로 반환한다.

| 상황 | 권장 상태 |
| --- | --- |
| Authorization Header 없음 | 401 |
| Access Token 만료 | 401 |
| Access Token 서명 불일치 | 401 |
| blacklist 토큰 | 401 |
| 역할 부족 | 403 |
| 라우팅 대상 서비스 없음 | 503 |

Gateway에서 downstream 서비스 장애를 감지하는 timeout, circuit breaker, fallback 정책은 MVP 이후 고도화 대상으로 둔다.

## 10. 보안 고려사항

- Gateway는 외부 요청의 내부 인증 헤더를 반드시 제거한다.
- Domain Service는 `X-Id-Card`를 서명 검증 없이 신뢰하지 않는다.
- Gateway와 Domain Service가 공유하는 HMAC secret은 Config Server 또는 환경변수로 관리한다.
- Access Token과 Refresh Token은 로그에 남기지 않는다.
- 인증 실패 로그에는 토큰 원문을 포함하지 않는다.

## 11. 테스트 방법

Gateway 인증은 다음 흐름으로 확인한다.

1. 인증 제외 API가 Access Token 없이 호출되는지 확인
2. 인증 필요 API가 Access Token 없이 401을 반환하는지 확인
3. 로그인 후 인증 필요 API가 정상 호출되는지 확인
4. 잘못된 Access Token이 401을 반환하는지 확인
5. 역할이 부족한 API가 403을 반환하는지 확인
6. 회원 탈퇴 후 기존 Access Token이 401을 반환하는지 확인

## 12. 후속 과제

- Gateway RBAC 정책 문서화
- 인증 선택 API의 optional id card 처리 방식 정리
- 장애 대응 정책 도입
- timeout, circuit breaker, fallback 응답 설계
- Gateway 공통 로깅과 requestId 전파
- 운영 알림 기준 정리

