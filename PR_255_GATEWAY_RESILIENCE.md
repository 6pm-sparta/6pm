## PR 제목

[feat] Gateway 장애대응 1차 구현

## 관련 이슈
Closes #255

## 변경 유형
- [x] feat (기능)  · [ ] fix (버그)  · [ ] refactor  · [ ] infra  · [x] test  · [ ] docs/chore

## 관련 서비스/모듈
gateway

## 변경 내용
- Gateway 전역 HTTP client timeout 설정 추가
- `connect-timeout: 2000ms`, `response-timeout: 10s` 적용
- 주요 downstream route에 CircuitBreaker filter 적용
- auth/user/feed/ticketing/order/notification/chat route별 CircuitBreaker instance 구성
- CircuitBreaker open 또는 downstream 호출 실패 시 `/fallback`으로 forward
- fallback 응답을 `503 + ApiResponse` 형식으로 표준화
- Gateway timeout 예외를 `504 + ApiResponse` 형식으로 표준화
- CircuitBreaker 상태를 actuator health에서 확인할 수 있도록 설정
- fallback/timeout 응답에 현재 trace가 있으면 `X-Trace-Id` 헤더 포함
- fallback 응답 및 timeout 응답 단위 테스트 추가

## 영향 / 마이그레이션
- [x] 다른 서비스에 영향(Breaking) 없음
- [x] DB 스키마 변경 없음

## 테스트 방법
- [x] `./gradlew :gateway-service:test` 통과

```powershell
.\gradlew.bat :gateway-service:test --no-daemon
```

결과:

```text
BUILD SUCCESSFUL
```

- 확인한 API:

```powershell
# Gateway timeout 재현용 검증
# 15초 지연 응답을 반환하는 로컬 slow server를 임시 기동
# Gateway 검증 인스턴스를 18080 포트로 임시 기동
# /api/v1/auth/login route를 slow server로 임시 연결
curl.exe -s -o timeout-response.txt -w "status=%{http_code} time=%{time_total}\n" `
  -X POST http://localhost:18080/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"userA@example.com\",\"password\":\"password123\"}"
```

확인 결과:

```text
status=504 time=10.188310
```

표준화된 timeout 응답 형식:

```json
{
  "status": 504,
  "message": "요청 처리 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.",
  "timestamp": "2026-07-02T00:41:10.1873266"
}
```

CircuitBreaker 수동 검증:

```text
Auth 서비스 정상 상태에서 Gateway 경유 로그인 10회 성공 확인
Auth 서비스 중단 후 Gateway 경유 로그인 실패 누적 확인
최근 10회 기준 실패율 50% 도달 시 authCircuitBreaker OPEN 전환 확인
OPEN 상태에서 추가 요청 시 notPermittedCalls 증가 확인
wait-duration-in-open-state 10s 이후 trial 요청으로 HALF_OPEN 진입 확인
Auth 서비스 복구 후 half-open permitted call 3회 성공 시 CLOSED 전환 확인
```

## 체크리스트
- [x] 셀프 리뷰 완료 (올리기 전 내 diff 직접 확인)
- [ ] 로컬 빌드 통과 (`./gradlew clean build`)
- [x] 공통 규약 준수 (`ApiResponse` · `CustomException`/`ErrorCode` · 엔티티 `BaseEntity` 상속)
- [x] `application.yml`에 평문 시크릿 없음 (환경변수/Config 사용)
- [x] 신규 서비스/엔드포인트면 Eureka 등록 · Gateway 라우트 확인

## 비고 (선택)
- `504 Gateway Timeout`은 Gateway httpclient timeout에서 발생하므로 CircuitBreaker fallback과 별도로 WebFlux 전역 예외 처리에서 표준화
- SSE/streaming 성격 route의 timeout 분리는 부하테스트 이후 route별 timeout 튜닝 작업으로 후속 분리 가능
- Auth 재기동 직후 Gateway half-open trial이 실패할 수 있는 readiness/warm-up 이슈는 본 PR 범위 밖의 후속 운영 이슈로 분리 필요
