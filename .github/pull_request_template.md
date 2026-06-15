<!-- 제목 규칙: [feat] 티켓 예매 동시성 처리  /  [fix] 결제 SAGA 보상 누락  /  [infra] ECR 배포 파이프라인 -->

## 관련 이슈
Closes #

## 변경 유형
- [ ] feat (기능)  · [ ] fix (버그)  · [ ] refactor  · [ ] infra  · [ ] test  · [ ] docs/chore

## 관련 서비스/모듈
<!-- 해당되는 것만: gateway / eureka / config / user / feed / ticketing / order / notification / aiops / common / infra -->

## 변경 내용
<!-- 무엇을 왜 바꿨는지 핵심만 -->
-

## 영향 / 마이그레이션
- [ ] 다른 서비스에 영향(Breaking) 없음  <!-- 있으면 내용 적기 -->
- [ ] DB 스키마 변경 없음  <!-- 있으면 적용 순서/스크립트 명시 -->

## 테스트 방법
<!-- 프론트 없음 → API 기준. 리뷰어가 그대로 검증 가능하게 -->
- [ ] `./gradlew :<module>:test` 통과
- 확인한 API:
  ```
  # 예) curl -X POST localhost:8080/api/tickets/1/reserve
  ```

## 체크리스트
- [ ] 셀프 리뷰 완료 (올리기 전 내 diff 직접 확인)
- [ ] 로컬 빌드 통과 (`./gradlew clean build`)
- [ ] 공통 규약 준수 (`ApiResponse` · `CustomException`/`ErrorCode` · 엔티티 `BaseEntity` 상속)
- [ ] `application.yml`에 평문 시크릿 없음 (환경변수/Config 사용)
- [ ] 신규 서비스/엔드포인트면 Eureka 등록 · Gateway 라우트 확인

## 비고 (선택)
<!-- 리뷰 시 봐줬으면 하는 부분, 트레이드오프, 후속 작업 -->
