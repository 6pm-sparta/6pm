# ADR 004 — Orders 상태 머신을 order-service와 정렬

**날짜**: 2026-06-18
**상태**: 확정

---

## 배경

order/payment 설계 문서 기준으로 ticketing 쪽 Orders 상태 표기를 정렬할 필요가 있었다.

---

## 결정

1. `PENDING_ORDER` → `PENDING`으로 개명
2. `PAID`, `COMPENSATING`, `REFUND_REQUESTED`, `FAILED` 상태 추가
3. `PAYMENT_REQUESTED` 상태 확정

---

## 이유

- **개명**: order/payment 설계 문서와 상태명을 정렬한다.
- **상태 추가**: order/payment 설계 문서 기준으로 상태 머신을 확장한다. `PAID`는 PG 승인 완료 시점과 좌석 예매 확정 시점 사이의 중간 상태다.
- **`PAYMENT_REQUESTED` 확정**: 코레오그래피 SAGA 구조상 PG 콜백 수신과 ticketing 좌석 확정 사이에 중간 상태가 필요하다. 멱등성·장애 복구 관점에서 유지한다.
