# ADR 003 — notification Consumer를 order.confirmed 구독으로 이관

**날짜**: 2026-06-18
**상태**: 확정 ([공유 완료] — notification-service 담당자)

---

## 배경

예매 완료 push 알림을 어느 시점에 발송해야 하는지 결정이 필요했다. 기존에는 좌석 확정 이벤트(`ticketing.seat.booked`)를 notification-service가 직접 구독하는 구조였다.

---

## 결정: `ticketing.seat.booked` 구독을 order-service가 발행하는 `order.confirmed` 구독으로 이관

---

## 이유

예매 완료 push는 좌석 확정 시점이 아니라 주문이 최종 확정된 시점에 발송해야 한다. 좌석 확정과 주문 최종 확정은 시점이 다르므로, notification-service는 order-service의 최종 확정 이벤트를 구독하는 것이 의미상 맞다.
