# Order-Service 설계 문서

**한 줄 요약**
PostgreSQL + Redis + Kafka 기반의 주문/결제 서비스. 좌석 선점 이후 주문 생성 → 결제 → 예매 확정의 흐름을 담당하며, SAGA Choreography 패턴으로 분산 트랜잭션 정합성을 보장한다.
 
---

## 문서 목록

| 문서 | 내용 |
|------|------|
| [architecture.md](./architecture.md) | ERD, 주문 상태 머신, Kafka 토픽, 동시성 제어 전략, 미확정 항목 |
| [flows.md](./flows.md) | 전체 시나리오별 흐름 (주문 완료, 취소, 실패, SAGA 보상) |
| [adr/001-webhook-primary-path.md](./adr/001-webhook-primary-path.md) | PG 웹훅을 Primary Completion Path로 채택한 이유 |
| [adr/002-saga-choreography.md](./adr/002-saga-choreography.md) | SAGA Choreography 패턴 채택 이유 |
| [adr/003-concurrency-strategy.md](./adr/003-concurrency-strategy.md) | 비관적 락 + Redis 분산락 이중 방어 전략 |
| [adr/004-payments-insert-on-retry.md](./adr/004-payments-insert-on-retry.md) | payments 1:N 구조 (실패 시 새 레코드 INSERT) |
| [adr/005-writer-bean-separation.md](./adr/005-writer-bean-separation.md) | Writer 빈 분리로 트랜잭션 경계 명시 |
| [adr/006-mock-payment-gateway-dip.md](./adr/006-mock-payment-gateway-dip.md) | MockPaymentGateway DIP 구조 및 시나리오 트리거 |
| [adr/007-pessimistic-lock.md](./adr/007-pessimistic-lock.md) | 비관적 락 채택 이유 |
 
---

## 빠른 참조

```
전체 시나리오 흐름이 궁금하다      → flows.md
ERD / 상태 머신 / 동시성이 궁금하다 → architecture.md
왜 이렇게 설계했는지 궁금하다      → adr/
```
 
---

## 구현 현황

| 기능 | 상태 |
|------|---|
| 주문 생성 (Ticketing Feign 내부 API) | ✅ |
| 주문 단건 / 목록 조회 | ✅ |
| 주문 취소 (결제 전 / 결제 후 / 확정 후) | ✅ |
| 결제 요청 | ✅ |
| 결제 단건 조회 / 주문별 결제 목록 | ✅ |
| PG 웹훅 수신 (승인 / 실패 / 환불 / 환불실패) | ✅ |
| 좌석 확정 이벤트 수신 → 주문 CONFIRMED | ✅ |
| SAGA 보상 트랜잭션 | ✅ |
| Kafka 이벤트 발행 (전 토픽) | ✅ |
| 단위 / 컨트롤러 테스트 | ✅ |
| 주문 타임아웃 자동 취소 스케줄러 | ✅ |
| PAYMENT_REQUESTED zombie 처리 | ❌ |
| FAILED / REFUND_REQUESTED stuck 복구 배치 | ❌ |