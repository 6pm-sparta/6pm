# ADR 011 — releaseHold()는 order-service에 취소를 요청하지 않는다

**날짜**: 2026-07-09
**상태**: 확정

---

## 배경

사용자가 `DELETE .../seats/{seatId}/hold`(좌석 선점 직접 해제)를 호출했을 때, 이미 체크아웃까지 진행돼 order-service에 주문(PENDING)이 생성돼 있다면 그 주문도 취소돼야 한다. 이 연동을 어떻게 구현할지를 두고 세 번 오갔다:

1. `OrderClient.cancel()` Feign 동기 호출 (커밋 #155) — order-service에 대응 엔드포인트가 없어 항상 404, 주석 처리됨
2. order-service에 `DELETE /internal/v1/orders/{orderId}` 신설 후 동기 호출 재개 시도 (2026-07-09) — ticketing이 order-service에 주는 좌석 해제 트리거는 Kafka로 관리하기로 결정되어 있어 반려됨
3. `ticketing.seat.hold.released` Kafka 이벤트로 비동기 전환 시도 (2026-07-09) — 기존 `order.hold.released` 경로로 이미 해결되는 것으로 확인되어 반려됨

---

## 결정

`releaseHold()`(그리고 `releaseExpiredHold()`)는 연결된 주문이 있어도 order-service에 아무것도 알리지 않는다. Redis/DB 쪽 좌석 상태만 로컬로 정리하고 끝낸다.

---

## 이유

- order-service는 이미 PENDING 주문을 취소하는 두 경로를 자체적으로 갖고 있다:
  - `OrderTimeoutWriter.expireIfStillPending()` — `expired_at` 기준 자동 취소
  - `OrderCancelWriter.decide()`의 PENDING 분기 — 사용자가 order-service의 `DELETE /api/v1/orders/{id}`를 직접 호출한 경우
- 두 경로 모두 취소 시점에 `outboxAppender.appendHoldReleased()`로 `order.hold.released`를 발행하고, ticketing의 `PaymentEventConsumer.onHoldReleased()`가 이를 구독해 `SeatConfirmService.releaseSeat()`로 좌석을 정리한다. `releaseSeat()`는 `findByOrderId`로 좌석을 못 찾으면 조용히 스킵하는 멱등 구조라, ticketing이 먼저 좌석을 풀어둔 뒤 이 이벤트가 뒤늦게 와도 안전하다.
- 즉 "주문 취소 → 좌석 해제" 방향은 이미 완성돼 있고 self-heal된다. ticketing이 반대 방향("좌석 해제 → 주문 취소를 요청")으로 별도 채널을 또 만드는 건 같은 결과를 위한 중복 배관이다.
- 유일하게 이 결정을 뒤집을 이유는 "주문이 취소돼야 함을 order-service의 타임아웃까지 기다리지 않고 즉시 반영해야 한다"는 요구사항이 생기는 경우인데, 아직 그런 요구사항은 확인되지 않았다.

---

## 재검토 조건

"결제 전 취소가 즉시(타임아웃 대기 없이) order-service에도 반영돼야 한다"는 요구사항이 명시적으로 확정되면, 그때 Kafka 이벤트(ticketing→order-service) 또는 동기 호출 중 하나를 다시 검토한다. 그 전까지는 이 지점에 새 연동을 추가하지 않는다.

## 관련

- [flows.md §6](../flows.md#6-좌석-선점-해제사용자-직접-취소), [flows.md §5](../flows.md#5-결제-실패취소--좌석-해제)
- [ADR 009](./009-order-cancel-integration.md) — 이 ADR과 반대로 "ticketing이 order-service를 호출하는" 연동이 실제로 필요했던 사례(좌석 해제 자체를 order-service가 몰라서 생긴 문제와는 다른 층위)
- [order-service/architecture.md §4](../../order-service/architecture.md) — `OrderTimeoutWriter`/`OrderCancelWriter`의 `order.hold.released` 발행 지점
