# ADR 009 — 주문 취소 연동 (#155)

**날짜**: 2026-07-01
**상태**: 확정

---

## 배경

좌석 선점을 해제할 때 연결된 주문도 함께 취소되어야 하는데, 이 연동이 없었다.

---

## 결정

- `OrderClient`에 `cancel(orderId)` Feign 메서드 추가
- `SeatService.releaseHold()`가 좌석 해제와 함께 order-service 주문도 취소하도록 연결

---

## 이유

좌석 해제와 주문 취소가 분리되어 있으면, 좌석은 풀렸는데 주문은 살아있는 상태가 생길 수 있다.

---

## 검증

`OrderClient`(`/internal/v1/orders`)와 order-service `InternalOrderController`(`/internal/v1/orders`) 경로 불일치 없음을 확인했다 — [archive/260624 E2E 점검 및 데모 시나리오.md](../archive/260624%20E2E%20점검%20및%20데모%20시나리오.md)에서 발견됐던 버그 후보는 이미 해소된 상태였다.

## 연계: order-service 타임아웃 자동취소 ([공유 완료])

order-service #231의 `order.hold.released` 토픽을 ticketing `PaymentEventConsumer.onHoldReleased()`가 구독, `SeatConfirmService.releaseSeat()`로 좌석을 해제한다. `releaseHold()`가 먼저 해제한 경우엔 멱등하게 무시된다.

## 후속 변경

2026-07-03에 `OrderClient.cancel()`에 `requesterId` 파라미터가 추가됐다(본인 확인용). 별도 ADR 없이 진행된 후속 변경.
