# Swagger(OpenAPI) 가이드

## 1. 문서 목적

이 문서는 6pm 프로젝트에 도입한 Swagger(OpenAPI) 문서 구조와 사용 방법을 정리한다.

설계 배경: API 시연을 Postman 대신 Swagger로 진행하기 위해, 9개 도메인 서비스에 공통 OpenAPI 설정(`common`의 `CommonOpenApiAutoConfiguration`)을 적용하고 Gateway에 통합 뷰를 구성했다.

## 2. 로컬 실행 전제

필요 구성:

- Docker infrastructure profile
- Config Server
- Eureka Server
- Gateway Service
- User, Auth, Feed, Ticketing, Order, Notification, Chat, CS, AIOps 9개 도메인 서비스

권장 실행 순서:

1. Docker infra
2. Config Server
3. Eureka Server
4. Gateway Service + 9개 도메인 서비스 (순서 무관)

Gateway 통합 문서 페이지:

```text
http://localhost:8080/swagger-ui/index.html
```

서비스 자기 포트 문서 페이지 (예: user-service):

```text
http://localhost:8081/swagger-ui/index.html
```

## 3. 문서 구조: public / internal 그룹

서비스마다 OpenAPI 문서를 두 그룹으로 나눈다.

| 그룹 | 매칭 경로 | Server | 인증 스킴 | 용도 |
|---|---|---|---|---|
| public | `/api/v1/**` | Gateway (`http://localhost:8080`) | Bearer (JWT) | 실제 사용자 흐름 시연 |
| internal | `/internal/v1/**` | 자기 자신 포트 | 없음 | 서비스 간 통신 API 확인, Gateway 우회 시연 |

internal 그룹에 인증 스킴이 없는 것은 실수가 아니다. `/internal/v1/**`는 Gateway 라우트가 없다는 것이 유일한 방어선이고, 코드 레벨에서 별도 인증을 하지 않는다는 사실을 문서에서도 그대로 드러내기 위함이다. 자세한 내용은 7절을 참고한다.

## 4. 접근 방법

### 4.1 Gateway 통합 페이지 (권장, 정상 흐름 시연용)

```text
http://localhost:8080/swagger-ui/index.html
```

우측 상단 `Select a definition` 드롭다운에서 8개 서비스의 public 문서를 선택할 수 있다.

aiops-service는 이 드롭다운에 없다. Alertmanager가 aiops-service를 직접 호출하는 구조라 Gateway 라우트 자체가 없기 때문이다. aiops 문서는 자기 포트(`localhost:8086`)에서 확인한다.

### 4.2 서비스 자기 포트 (internal 그룹 확인용)

```text
http://localhost:{서비스 포트}/swagger-ui/index.html
```

포트 목록:

- user-service: 8081
- auth-service: 8087
- feed-service: 8082
- ticketing-service: 8083
- order-service: 8084
- notification-service: 8085
- aiops-service: 8086
- chat-service: 8088
- cs-service: 8089

상단 드롭다운에서 `{서비스명} (internal, Gateway 우회)`를 선택하면 internal 그룹 문서가 뜬다.

## 5. 인증 테스트 절차

### 5.1 로그인으로 accessToken 발급

Gateway 통합 페이지에서 `auth-service` 선택 후 `POST /api/v1/auth/login` 실행.

기대 결과:

- HTTP 200
- `data.accessToken` 존재

### 5.2 Authorize 등록

우측 상단 `Authorize` 버튼 클릭 후 발급받은 accessToken을 그대로 입력한다.

기대 결과:

- 자물쇠 아이콘이 잠긴 상태로 바뀜
- 이후 실행하는 모든 요청에 `Authorization: Bearer {token}` 헤더가 자동으로 붙음

### 5.3 인증이 필요한 API 실행

예: `user-service` 선택 후 `PATCH /api/v1/members/me` 실행.

기대 결과:

- Parameters에 `userId`, `role` 같은 파라미터가 보이지 않음 (`@CurrentIdCard` 파라미터는 문서에서 제거됨, 6절 참고)
- HTTP 200
- Authorize 없이 실행하면 HTTP 401

## 6. `@CurrentIdCard` 파라미터 숨김 처리

`@CurrentIdCard`가 붙은 컨트롤러 파라미터는 클라이언트가 직접 채우는 값이 아니라, Gateway가 JWT 검증 후 서명된 `X-Id-Card` 헤더로 주입하는 값이다.

이 파라미터를 숨기지 않으면 springdoc이 이를 일반 쿼리 파라미터(`userId`, `role`)로 문서화해버려 혼란을 준다. `common`의 `CommonOpenApiAutoConfiguration`에 등록된 `ParameterCustomizer`가 이 파라미터를 문서에서 제거한다. 실제 인증 동작에는 영향이 없다.

## 7. Internal API 우회 시연

목적: `/internal/v1/**`가 Gateway 라우트 부재 외에 별도 방어가 없다는 것을 보여준다.

절차:

1. 아무 도메인 서비스 자기 포트로 접속 (예: `localhost:8081`)
2. `{서비스명} (internal, Gateway 우회)` 그룹 선택
3. 인증 없이 아무 internal API 실행 (예: `GET /internal/v1/users/{userId}`)

기대 결과:

- Authorize 없이도 HTTP 200
- 같은 API를 Gateway(`localhost:8080`)를 거쳐서 호출하면 라우트가 없어 실패함 (CORS 또는 404)

## 8. 알려진 제약사항

- aiops-service는 Gateway 통합 페이지 드롭다운에 없다. 자기 포트에서만 확인한다.
- Gateway 페이지에서는 public 그룹만 정상 동작한다. internal 그룹을 Gateway 페이지에서 실행하면 CORS로 막힌다. 이는 의도된 동작이며 별도로 수정하지 않는다.
- 반대로 서비스 자기 포트 페이지에서 public 그룹을 실행하면 Gateway로 크로스오리진 요청이 나가 CORS로 막힌다. public 그룹 실행은 항상 Gateway 통합 페이지에서 한다.

## 9. 자주 발생한 문제

### 9.1 Servers가 `Generated server url`로 뜸

증상:

Gateway 통합 페이지에서 특정 서비스를 선택했을 때 Servers 드롭다운에 `http://localhost:8080`이 아니라 실제 서버 IP(`http://192.168.x.x:{포트}`)가 `Generated server url`이라는 이름으로 뜬다.

원인:

`gateway-service-dev.yml`의 `springdoc.swagger-ui.urls`에서 그룹 조회 방식을 쿼리 파라미터(`?group=public`)로 지정했기 때문이다. springdoc은 그룹을 경로 세그먼트(`/v3/api-docs/public`)로 조회하므로, 쿼리 파라미터는 무시되고 그룹이 지정되지 않은 기본 문서가 대신 서빙된다. 이 기본 문서에는 커스텀 Server 설정이 없어 discovery locator가 실제로 포워딩한 서비스 주소를 기준으로 자동 생성된 URL이 노출된다.

확인:

```text
http://localhost:8080/{서비스명}/v3/api-docs/public
```

이 URL을 직접 열어 `servers` 필드가 `http://localhost:8080`인지 확인한다.

해결:

`gateway-service-dev.yml`의 `swagger-ui.urls` 항목을 전부 `/v3/api-docs/{그룹명}` 형식(경로 세그먼트)으로 작성한다.

### 9.2 Execute 시 `Failed to fetch` (CORS)

증상: Execute 버튼을 눌렀을 때 CORS 관련 에러로 요청이 아예 나가지 못함.

확인: 8절의 제약사항에 해당하는 조합인지 확인한다 (Gateway 페이지에서 internal 실행, 또는 자기 포트 페이지에서 public 실행).

### 9.3 config-server가 최신 설정을 못 받아옴

증상: `6pm-config` 리포를 수정했는데 Gateway/서비스가 옛날 값 그대로 동작함.

원인: config-server는 로컬 파일이 아니라 GitHub의 `6pm-sparta/6pm-config` 리포(main 브랜치)를 읽는다. 로컬에서 파일만 수정하고 커밋/푸시를 하지 않으면 반영되지 않는다.

확인:

1. `6pm-config` 로컬 변경사항이 커밋 + 푸시됐는지 확인
2. config-server 재시작
3. 해당 값을 쓰는 서비스 재시작 (Config Client는 기동 시점에만 값을 받아오므로, 일반 프로퍼티는 `/actuator/refresh`로도 반영되지 않을 수 있다)

### 9.4 `common` 수정 후 서비스에 반영이 안 됨

증상: `common` 모듈을 수정했는데 이를 의존하는 서비스에서 변경사항이 안 보임.

확인: IntelliJ에서 서비스를 재시작하면 보통 `common`을 포함해 다시 컴파일하지만, 반영이 안 되면 `./gradlew :common:build` 이후 서비스를 재시작한다.
