---
name: "🐛 버그 리포트 (Bug)"
about: "서버 500 에러, 로직 결함, DB 데드락 등 백엔드 버그를 신고합니다."
title: "[BUG] "
labels: "bug"
assignees: ''
---

## 🚨 버그 설명
(예: 피드 작성 API 호출 시 JWT 토큰은 정상인데 401 Unauthorized 에러가 발생합니다.)

## 🔍 재현 방법 (Reproduce)
1. `gateway-service`와 `feed-service`를 실행한다.
2. Postman에서 `Authorization: Bearer <token>` 헤더를 넣고 `POST /api/v1/feeds` 로 요청한다.
3. 응답으로 401 에러가 반환된다.

## 💻 에러 로그 (Stack Trace)
```java
// 여기에 에러 로그를 붙여넣으세요.
```

## 🌐 환경 정보
- **발생 위치:** [ ] Local (인텔리제이) / [ ] Docker 인프라 / [ ] 배포 서버(AWS)
- **발생 서비스:** (예: `gateway-service`)

## 💡 예상되는 원인 (선택)
(예: Gateway의 JWT 필터에서 헤더 검증 로직에 오타가 있는 것 같습니다.)