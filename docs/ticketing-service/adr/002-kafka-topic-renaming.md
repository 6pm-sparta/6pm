# ADR 002 — Kafka 토픽 구성 정리 (개명/신규/제거)

**날짜**: 2026-06-18
**상태**: 확정 ([공유 완료] — order-service, notification-service 담당자)

---

## 배경

초기 설계 단계에서 정한 토픽명(`ticketing.booking.completed`)과 임시로 넣어뒀던 토픽(`ticketing.seat.held`)이 order/payment 설계 문서 및 실제 구현 방향과 어긋났다.

---

## 결정

1. `ticketing.booking.completed` → `ticketing.seat.booked`로 개명
2. `ticketing.seat.book.failed` 신규 추가
3. `ticketing.seat.held` 제거

---

## 이유

- **개명**: 기존 명칭은 예매 완료에 초점이 맞춰져 있어 좌석 확정 시점과 의미가 모호했다. order/payment 설계 문서와 토픽명을 정렬한다.
- **신규 추가**: 좌석 예매 실패 시 SAGA 보상 트랜잭션을 트리거하는 용도. order-service가 구독하여 `COMPENSATING` 상태로 전환 후 환불 처리한다.
- **제거**: 구간 2(좌석 선점 → 주문 생성)를 Feign 동기 호출로 구현하면서 불필요해졌다. orderId 즉시 응답이 필요한 동기 흐름에는 Feign이 적합하다. order-service에 해당 토픽 구독 코드가 없음을 확인해 별도 공유가 불필요했다.
