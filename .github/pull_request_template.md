## 🎫 관련 이슈
* resolves #

## 🎯 작업 내용
- [ ] 회원가입 API 로직 구현 (`POST /api/v1/users`)
- [ ] UserContext DTO 추가 및 GlobalExceptionHandler 연동

## 🏗️ 영향을 받는 마이크로서비스
- [ ] `common` (전사 공통 모듈)
- [ ] `eureka-server` / `gateway-service` / `config-server`
- [ ] `user-service`
- [ ] `feed-service`
- [ ] `ticketing-service`
- [ ] `order-service`
- [ ] `notification-service`
- [ ] `aiops-service`
- [ ] 인프라 (Docker, CI/CD, AWS 등)

## 💡 테스트 및 검증 방법
1. `docker compose --profile infra up -d` 로 DB 실행
2. `{서비스명}` 실행 후 Postman으로 아래 JSON 데이터 전송
```json
{
  "email": "test@fandom.com",
  "password": "password123!"
}
```

## 🛡️ 백엔드 체크리스트
- [ ] 빌드 및 테스트가 정상적으로 통과되었는가? (`gradlew build`)
- [ ] 컨트롤러 응답 시 `ApiResponse` 공통 규격을 사용했는가?
- [ ] 에러 발생 시 `CustomException`과 `ErrorCode`를 사용해 예외 처리를 했는가?
- [ ] 민감한 정보(비밀번호, API Key 등)가 하드코딩되지 않았는가?