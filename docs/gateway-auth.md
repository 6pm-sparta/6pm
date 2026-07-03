# Gateway 인증 설계

## 1. 문서 목적

이 문서는 Gateway Service가 외부 요청을 인증하고, 내부 도메인 서비스로 인증 컨텍스트를 전달하는 방식을 정리한다.

Gateway 인증 흐름의 상위 구조는 [auth-user-architecture.md](./auth-user-architecture.md)를 따른다. `UserIdCard`, `@CurrentIdCard`, HMAC 검증 규약의 상세는 [common-auth.md](./common-auth.md)를 따른다.

## 2. Gateway의 책임

Gateway는 외부 클라이언트 요청의 첫 진입점이다.

주요 책임은 다음과 같다.

- 요청 경로에 따른 서비스 라우팅
- 인증 제외 경로 판단
- 인증 전 위험 경로에 대한 rate limit 적용
- Access Token 검증
- 클라이언트가 보낸 내부 인증 헤더 제거
- `UserIdCard` 생성
- `UserIdCard` HMAC 서명 생성
- downstream 서비스로 내부 인증 헤더 전달
- URL과 HTTP Method 기준의 1차 RBAC 적용
- downstream 장애 격리를 위한 timeout, circuit breaker, fallback 처리
- traceId 기반 요청 추적 및 access log 기록

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

로그인과 Access Token 재발급은 인증 제외 경로이지만, 인증 전 요청 중 brute-force 및 비정상 반복 요청의 주요 표적이므로 Gateway에서 별도 rate limit을 적용한다.

## 4. 인증 전 요청 rate limit 정책

Gateway는 로그인/재발급 경로에 Redis Token Bucket 기반 rate limit을 적용한다.

적용 대상:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/reissue`

정책:

- 인증 전 요청이므로 userId 기준이 아니라 `IP + path` 기준으로 버킷을 분리한다.
- 로그인/재발급 전용 route를 일반 auth-service route보다 먼저 매칭한다.
- Redis는 일반 Redis(`redis-general`)를 사용한다.
- 제한 초과 시 HTTP 429와 공통 `ApiResponse` 형식으로 응답한다.

초기 제한값:

| 항목 | 값 |
| --- | --- |
| replenishRate | 5 |
| burstCapacity | 10 |
| requestedTokens | 1 |

초기값은 로컬/개발 검증 기준이며, 부하테스트 결과에 따라 조정할 수 있다.

## 5. Access Token 검증 흐름

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

## 6. 내부 인증 헤더 제거 정책

Gateway는 클라이언트가 임의로 전달한 내부 인증 헤더를 제거한다.

제거 대상 예시는 다음과 같다.

- `X-Id-Card`
- `X-Id-Card-Signature`
- `X-User-*`
- `X-Internal-*`

이 정책은 외부 클라이언트가 내부 사용자 컨텍스트를 위조하는 것을 막기 위한 방어선이다.

## 7. UserIdCard 생성 및 HMAC 서명

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

## 8. downstream 서비스 전달 헤더

Gateway가 downstream 서비스로 전달하는 인증 헤더는 다음과 같다.

| Header | 설명 |
| --- | --- |
| `X-Id-Card` | 인증 사용자 컨텍스트 |
| `X-Id-Card-Signature` | `X-Id-Card` HMAC 서명 |

개별 API 명세에는 이 내부 헤더를 반복해서 적지 않는다. 인증 컨텍스트 규약은 common-auth 문서를 기준으로 한다.

## 9. RBAC 정책

Gateway RBAC는 URL과 HTTP Method 기준의 1차 접근 제어다.

예시:

| API 성격 | 허용 역할 |
| --- | --- |
| 일반 회원 API | `MEMBER`, `CREATOR`, `MASTER` |
| 크리에이터 전용 API | `CREATOR`, `MASTER` |
| 관리자 API | `MASTER` |

Gateway RBAC는 1차 방어선이다. 리소스 소유자 검증은 각 도메인 서비스에서 수행한다.

예를 들어 `PATCH /members/me/profile`은 Gateway에서 로그인 사용자 여부만 확인하고, 실제 수정 대상이 본인인지 여부는 User Service가 `UserIdCard`를 기준으로 검증한다.

## 10. 장애 및 예외 응답 정책

Gateway 인증 실패와 운영 실패는 공통 응답 형식으로 반환한다.

| 상황 | 권장 상태 | 설명 |
| --- | --- | --- |
| Authorization Header 없음 | 401 | 인증 필요 API에 토큰 없음 |
| Access Token 만료 | 401 | 만료된 토큰 |
| Access Token 서명 불일치 | 401 | 위조 또는 잘못된 토큰 |
| blacklist 토큰 | 401 | 로그아웃 또는 사용자 무효화 대상 |
| 역할 부족 | 403 | Gateway RBAC 실패 |
| rate limit 초과 | 429 | 로그인/재발급 과호출 |
| CircuitBreaker open 또는 downstream 실패 | 503 | fallback 응답 |
| Gateway HTTP client timeout | 504 | downstream 응답 지연 |
| 라우팅 대상 서비스 없음 | 503 | 서비스 불가 |

### 10.1 CircuitBreaker / fallback

Gateway는 downstream 서비스 장애가 전체 요청 흐름으로 전파되는 것을 줄이기 위해 주요 route에 CircuitBreaker를 적용한다.

적용 대상은 auth, user, feed, ticketing, order, notification, chat 등 주요 downstream route다.

정책:

- CircuitBreaker가 open되거나 downstream 호출이 실패하면 `/fallback`으로 forward한다.
- fallback 응답은 `503 + ApiResponse` 형식으로 반환한다.
- 현재 trace가 있으면 응답 헤더에 `X-Trace-Id`를 포함한다.

### 10.2 Timeout

Gateway는 전역 HTTP client timeout을 둔다.

초기 설정:

- connect-timeout: 2000ms
- response-timeout: 10s

response-timeout을 초과하면 CircuitBreaker fallback과 별도로 `504 + ApiResponse` 형식으로 반환한다.

## 11. Trace / Access Log 정책

Gateway는 WebFlux 기반 요청에서도 tracing context가 유실되지 않도록 Reactor context propagation을 사용한다.

Gateway access log는 다음 필드를 남긴다.

- traceId
- spanId
- method
- path
- status
- elapsedTimeMs

Gateway 응답에는 가능한 경우 `X-Trace-Id` 헤더를 포함한다. 이 값은 클라이언트, Gateway 로그, downstream 서비스 로그, Zipkin trace를 연결하는 기준으로 사용한다.

## 12. 보안 고려사항

- Gateway는 외부 요청의 내부 인증 헤더를 반드시 제거한다.
- Domain Service는 `X-Id-Card`를 서명 검증 없이 신뢰하지 않는다.
- Gateway와 Domain Service가 공유하는 HMAC secret은 Config Server 또는 환경변수로 관리한다.
- Access Token과 Refresh Token은 로그에 남기지 않는다.
- 인증 실패 로그에는 토큰 원문을 포함하지 않는다.
- rate limit key는 인증 전 요청 특성상 IP 기반으로 구성하되, 운영에서 프록시/ALB 뒤에 배치될 경우 `X-Forwarded-For` 처리 정책을 별도로 검토한다.

## 13. 테스트 방법

Gateway 인증은 다음 흐름으로 확인한다.

1. 인증 제외 API가 Access Token 없이 호출되는지 확인
2. 인증 필요 API가 Access Token 없이 401을 반환하는지 확인
3. 로그인 후 인증 필요 API가 정상 호출되는지 확인
4. 잘못된 Access Token이 401을 반환하는지 확인
5. 역할이 부족한 API가 403을 반환하는지 확인
6. 회원 탈퇴 후 기존 Access Token이 401을 반환하는지 확인
7. 로그인/재발급 경로 과호출 시 429를 반환하는지 확인
8. downstream 장애 시 503 fallback 응답을 반환하는지 확인
9. downstream timeout 시 504 응답을 반환하는지 확인
10. 응답 헤더와 access log에서 traceId가 확인되는지 확인

## 14. 후속 과제

- Gateway RBAC 정책 세부 문서화
- 인증 선택 API의 optional id card 처리 방식 정리
- SSE/streaming route의 timeout 분리 여부 검토
- 운영 환경에서 `X-Forwarded-For` 기반 rate limit key 전환 검토
- 부하테스트 결과 기반 rate limit 임계값 조정
- 운영 알림 기준 정리
