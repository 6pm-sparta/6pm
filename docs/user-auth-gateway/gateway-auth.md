# Gateway 인증 설계

## 1. 문서 목적

이 문서는 Gateway Service가 외부 요청을 인증하고, 내부 도메인 서비스로 인증 컨텍스트를 전달하는 방식을 정리한다.

Gateway 인증 흐름의 상위 구조는 [auth-user-architecture.md](./auth-user-architecture.md)를 따른다. `UserIdCard`, `@CurrentIdCard`, HMAC 검증 규약의 상세는 [common-auth.md](./common-auth.md)를 따른다.

Gateway의 로그 파일 형식, Loki 수집 방식, 공통 로그 필드 기준은 [infra/logging-standard.md](./infra/logging-standard.md)를 따른다.

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
- 로그인/재발급 경로에 대한 rate limit 적용
- downstream 서비스 장애 전파를 줄이기 위한 timeout, circuit breaker, fallback 처리
- 요청 단위 추적을 위한 traceId 전파, access log 기록, `X-Trace-Id` 응답 헤더 제공

Gateway는 사용자 도메인 데이터를 직접 관리하지 않는다. 사용자 상태나 상세 정보가 필요한 경우 Auth Service 또는 User Service와의 계약을 통해 처리한다.

## 3. 인증 제외 경로 정책

인증 제외 경로는 Access Token 없이 접근 가능한 API다.

대표 예시는 다음과 같다.

- 회원가입
- 로그인
- Access Token 재발급
- 공개 프로필 조회
- 공개 게시글 조회
- Swagger/OpenAPI 문서 경로(`/v3/api-docs/**`, `/swagger-ui/**`)
- actuator 등 운영 확인 경로

인증 제외 경로는 Gateway에서 관리한다. 내부 서비스에서 `@CurrentIdCard`가 필요한 API를 인증 제외 경로로 열면 런타임 오류 또는 인증 컨텍스트 누락이 발생할 수 있으므로, Gateway 정책과 컨트롤러 시그니처를 함께 확인해야 한다.

로그인과 Access Token 재발급은 인증 제외 경로이지만, 비정상 반복 요청의 위험이 높은 경로이므로 Gateway rate limit 대상에 포함한다.

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

## 9. 인증 경로 rate limit 정책

로그인과 Access Token 재발급 경로는 인증 전 요청이므로 userId 기반 제한을 적용할 수 없다. 따라서 Gateway는 IP와 path를 기준으로 rate limit을 적용한다.

적용 대상:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/reissue`

정책:

- Redis Token Bucket 기반 rate limit을 사용한다.
- 인증 전 요청이므로 `IP + path` 기준으로 bucket을 구분한다.
- IP 추출은 `XForwardedRemoteAddressResolver.maxTrustedIndex(1)`을 사용한다. 운영(ALB 경유)에서 모든 요청이 ALB IP 단일 버킷으로 집계되어 rate limit이 무력화되는 문제를 막기 위해, X-Forwarded-For의 오른쪽 1홉(ALB가 확인한 값)만 신뢰하고 클라이언트 위조 헤더는 무시한다. 로컬(X-Forwarded-For 없음)에서는 remoteAddress로 자동 fallback된다.
- 제한값은 기본적으로 초당 5회, burst 10회를 기준으로 한다.
- 제한 초과 시 downstream Auth Service로 요청을 전달하지 않고 429 응답을 반환한다.
- 429 응답도 공통 `ApiResponse` 형식으로 반환한다.
- 429 발생 시 `[RATE-LIMIT] ip={} path={}` 형식의 WARN 로그를 남겨 Loki에서 과호출/공격 신호를 추적할 수 있게 한다(429는 AccessLogFilter 도달 전 차단되어 별도 로깅이 필요).

rate limit은 Auth Service의 비밀번호 검증 로직을 대체하지 않는다. Gateway 단계에서 brute-force 또는 비정상 반복 요청을 1차로 줄이는 방어선이다.

## 10. 장애 및 예외 응답 정책

Gateway 인증 실패와 라우팅/호출 실패는 공통 응답 형식으로 반환한다.

| 상황 | 권장 상태 |
| --- | --- |
| Authorization Header 없음 | 401 |
| Access Token 만료 | 401 |
| Access Token 서명 불일치 | 401 |
| blacklist 토큰 | 401 |
| 역할 부족 | 403 |
| rate limit 초과 | 429 |
| downstream 서비스 장애 또는 fallback | 503 |
| downstream timeout | 504 |

Gateway는 downstream 서비스 장애가 전체 요청 경로로 전파되는 것을 줄이기 위해 route별 circuit breaker와 HTTP client timeout을 적용한다.

정책:

- Gateway HTTP client에는 연결 timeout과 응답 timeout을 둔다.
- route별 circuit breaker를 적용해 특정 downstream 장애가 Gateway 전체 장애로 번지지 않게 한다.
- circuit breaker open 또는 downstream 호출 실패 시 `/fallback`으로 forward해 fallback 응답을 반환한다.
- `FallbackController`는 fallback 원인을 `CIRCUIT_OPEN` / `TIMEOUT` / `NO_INSTANCE_AVAILABLE` / `CONNECTION_FAILED` / `DOWNSTREAM_ERROR` / `UNKNOWN`으로 분류하고, 응답에 `X-Fallback-Reason`·`X-Fallback-Route` 헤더를 담는다. 로그에도 route/reason/status/cause를 한 줄로 남긴다.
- 원인이 `TIMEOUT`이면 **504**를, 그 외(CB open, 인스턴스 부재 등)는 **503**을 반환한다. CircuitBreaker가 TimeLimiter와 묶여 동작하므로 CB가 적용된 라우트(현재 주요 라우트 전체)에선 타임아웃도 이 fallback을 통해 처리된다.
- fallback 또는 timeout 응답도 공통 `ApiResponse` 형식으로 반환한다.
- trace가 존재하면 오류 응답에도 `X-Trace-Id` 헤더를 포함한다.

## 11. access log 및 traceId 전파 정책

Gateway는 외부 요청의 첫 진입점이므로 요청 단위 추적을 위한 access log와 traceId 전파를 담당한다.

처리 흐름:

```text
Client
  -> Gateway
     - trace context 생성 또는 수신
     - Gateway access log 기록
     - downstream 서비스로 trace context 전파
     - 응답 헤더에 X-Trace-Id 추가
  -> Domain Service
     - common access log 기록
     - 같은 traceId로 로그/trace 연결
```

Gateway access log의 주요 필드는 다음과 같다.

| 필드 | 설명 |
| --- | --- |
| `traceId` | 요청 단위 추적 식별자 |
| `spanId` | 현재 span 식별자 |
| `method` | HTTP method |
| `path` | 요청 path |
| `status` | HTTP 응답 상태 |
| `elapsedTimeMs` | Gateway 처리 시간 |

Gateway는 WebFlux 기반이므로 trace context가 downstream 호출까지 이어지도록 context propagation 설정을 사용한다.

`X-Trace-Id` 응답 헤더는 클라이언트나 운영자가 장애 문의, 로그 검색, Zipkin 조회를 할 때 같은 요청을 찾기 위한 외부 노출용 식별자다. 이 값은 인증 토큰이 아니며, 사용자 식별이나 권한 판단에 사용하지 않는다.

## 12. 로그 수집 연계

Gateway가 남긴 access log는 서비스 로그 파일에 기록된다.

로컬 개발/관측 환경에서는 각 서비스가 프로젝트 루트 기준 `./logs/<service>.log` 파일에 로그를 남기고, Promtail이 `./logs/*.log`를 tail 하여 Loki로 전송한다.

예시:

```text
./logs/gateway-service.log
./logs/auth-service.log
./logs/user-service.log
```

Gateway access log와 downstream 서비스 access log에 같은 traceId가 포함되면, Grafana Loki에서 traceId 기준으로 한 요청의 Gateway 진입 로그와 도메인 서비스 처리 로그를 함께 검색할 수 있다. Zipkin trace와 함께 보면 어느 구간에서 지연 또는 실패가 발생했는지 추적할 수 있다.

## 13. 보안 고려사항

- Gateway는 외부 요청의 내부 인증 헤더를 반드시 제거한다.
- Domain Service는 `X-Id-Card`를 서명 검증 없이 신뢰하지 않는다.
- Gateway와 Domain Service가 공유하는 HMAC secret은 Config Server 또는 환경변수로 관리한다.
- Access Token과 Refresh Token은 로그에 남기지 않는다.
- 인증 실패 로그에는 토큰 원문을 포함하지 않는다.
- `X-Trace-Id`는 요청 추적용 식별자일 뿐 인증 정보가 아니므로, 사용자 인증/인가 판단에 사용하지 않는다.
- access log에는 비밀번호, 토큰, HMAC secret, 개인정보 등 민감정보를 포함하지 않는다.

## 14. 테스트 방법

Gateway 인증은 다음 흐름으로 확인한다.

1. 인증 제외 API가 Access Token 없이 호출되는지 확인
2. 인증 필요 API가 Access Token 없이 401을 반환하는지 확인
3. 로그인 후 인증 필요 API가 정상 호출되는지 확인
4. 잘못된 Access Token이 401을 반환하는지 확인
5. 역할이 부족한 API가 403을 반환하는지 확인
6. 회원 탈퇴 후 기존 Access Token이 401을 반환하는지 확인

Gateway 운영 정책은 다음 흐름으로 확인한다.

1. 로그인 또는 재발급 경로를 반복 호출해 rate limit 초과 시 429가 반환되는지 확인
2. downstream 서비스 중지 또는 지연 상황에서 fallback 또는 timeout 응답이 공통 `ApiResponse` 형식으로 반환되는지 확인
3. Gateway 응답 헤더에 `X-Trace-Id`가 포함되는지 확인
4. `./logs/gateway-service.log`에 access log가 남는지 확인
5. downstream 서비스 로그와 Gateway 로그에서 같은 traceId로 요청을 연결할 수 있는지 확인
6. Zipkin에서 Gateway와 downstream 서비스가 같은 trace로 이어지는지 확인

## 15. 후속 과제

- 인증 선택 API의 optional id card 처리 방식 정리
- Grafana Loki derivedFields를 통한 traceId → Zipkin 링크 연계 고도화
- access log 필드 표준과 서비스별 비즈니스 로그 MDC 확장
- 운영 알림 기준 정리
