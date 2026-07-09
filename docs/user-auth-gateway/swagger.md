# Swagger / OpenAPI 문서화

## 1. 문서 목적

이 문서는 6pm 도메인 서비스의 API 문서(Swagger/OpenAPI) 구성 방식과 Gateway 통합 노출 정책을 정리한다.

접근 방법, 인증 테스트 절차, internal API 우회 시연, 트러블슈팅 등 실사용 가이드는 [../swagger-guide.md](../swagger-guide.md)를 따른다. 이 문서는 인증/게이트웨이 관점의 구조와 정책만 다룬다.

## 2. 구성 개요

Swagger는 `springdoc-openapi` 기반이며, 서비스별 중복 설정 대신 `common` 모듈의 공통 자동설정으로 일괄 적용한다.

- `common`의 `CommonOpenApiAutoConfiguration`이 9개 도메인 서비스에 공통으로 두 개의 OpenAPI 그룹을 자동 생성한다.
- `common`의 `ParameterCustomizer`가 `@CurrentIdCard` 파라미터를 Swagger 문서에서 숨긴다(실제 동작에는 영향 없음). `@CurrentIdCard`는 Gateway가 주입하는 내부 인증 컨텍스트라 클라이언트가 직접 넣는 값이 아니므로 문서에서 제외한다.
- `gateway-service`는 `springdoc-openapi-starter-webflux-ui`로 여러 서비스 문서를 한 페이지에서 드롭다운으로 통합 제공한다.

## 3. OpenAPI 그룹 정책

각 도메인 서비스는 두 그룹으로 문서를 나눈다.

| 그룹 | 경로 | Server | 인증 |
| --- | --- | --- | --- |
| `public` | `/api/v1/**` | Gateway | Bearer(Access Token) |
| `internal` | `/internal/v1/**` | 자기 자신(서비스 직접) | 없음 |

- `public` 그룹은 Gateway를 Server로 지정해, Swagger에서 그대로 호출하면 실제 요청 경로(Gateway 경유)와 동일하게 동작한다. Bearer 인증 스킴이 등록되어 있어 `Authorize`에 Access Token을 넣고 인증 API를 테스트할 수 있다.
- `internal` 그룹은 서비스 간 직접 호출 전용(`/internal/v1/**`)이라 Server가 자기 자신이고 인증 스킴이 없다.

## 4. Gateway 통합 Swagger UI

Gateway는 aiops를 제외한 8개 서비스의 `public` 문서를 한 페이지에서 통합 제공한다.

- 접근: `http://{gateway}/swagger-ui/index.html` (로컬 기준 `localhost:8080`)
- `swagger-ui.urls` 설정으로 서비스별 문서를 드롭다운으로 전환한다.

Gateway 인증 파이프라인이 Swagger 정적 리소스와 문서 JSON을 막지 않도록, 다음 경로를 인증 예외(permit-all)로 둔다. (`GatewaySecurityRules.isPermitAll()`)

- `/v3/api-docs/**` — OpenAPI 문서(JSON)
- `/swagger-ui/**` — Swagger UI 정적 리소스

인증 예외 경로 전체 표는 [gateway-cors-rbac.md](./gateway-cors-rbac.md) 3장을 따른다.

## 5. 운영 노출 주의사항

현재 구성은 **로컬 개발 환경 기준**이며, 운영 반영 전 다음을 검토해야 한다.

- `gateway.base-url`의 운영 값 미설정, `*-prod.yml` 부재 — 운영 노출은 별도 작업으로 분리한다.
- `internal` 그룹 문서 자체가 내부 API 구조를 드러내는 정보 노출이 될 수 있다. Swagger를 운영에 그대로 노출할지, `internal` 그룹을 운영에서 숨길지 정책 결정이 필요하다.
- `/internal/v1/**`는 Gateway 라우트에 걸리지 않아 외부로 라우팅되지 않을 뿐, 서비스 포트로 직접 접근하면 별도 인증 방어가 없다. 이 사실은 Swagger로 시연 가능하며, 실제 보강(내부 호출 인증)은 후속 과제다.

## 6. 관련 이력

- 도입: PR #332 `[feat] Swagger(OpenAPI) 문서 도입`
- 실사용 가이드: [../swagger-guide.md](../swagger-guide.md)
