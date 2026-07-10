# ADR 011 — releaseHold()의 order-service 취소 연동

**날짜**: 2026-07-09 원결정 · 2026-07-10 재검토 발동
**상태**: 재검토 중 — 코드 미반영, 대응 방향만 확정 (착수 시점 미정)

---

## 배경

사용자가 `DELETE .../seats/{seatId}/hold`(좌석 선점 직접 해제)를 호출했을 때, 이미 체크아웃까지 진행돼 order-service에 주문(PENDING)이 생성돼 있다면 그 주문도 취소돼야 한다. 이 연동을 어떻게 구현할지를 두고 세 번 오갔다:

1. `OrderClient.cancel()` Feign 동기 호출 (커밋 #155) — order-service에 대응 엔드포인트가 없어 항상 404, 주석 처리됨
2. order-service에 `DELETE /internal/v1/orders/{orderId}` 신설 후 동기 호출 재개 시도 (2026-07-09) — ticketing이 order-service에 주는 좌석 해제 트리거는 Kafka로 관리하기로 결정되어 있어 반려됨
3. `ticketing.seat.hold.released` Kafka 이벤트로 비동기 전환 시도 (2026-07-09) — 기존 `order.hold.released` 경로로 이미 해결되는 것으로 확인되어 반려됨

---

## 원래 결정 (2026-07-09)

`releaseHold()`(그리고 `releaseExpiredHold()`)는 연결된 주문이 있어도 order-service에 아무것도 알리지 않는다. Redis/DB 쪽 좌석 상태만 로컬로 정리하고 끝낸다.

**이유:**
- order-service는 이미 PENDING 주문을 취소하는 두 경로를 자체적으로 갖고 있다 — `OrderTimeoutWriter.expireIfStillPending()`(타임아웃 자동 취소), `OrderCancelWriter.decide()`의 PENDING 분기(사용자 직접 `DELETE /api/v1/orders/{id}`).
- 두 경로 모두 취소 시점에 `order.hold.released`를 발행하고, ticketing의 `PaymentEventConsumer.onHoldReleased()`가 이를 구독해 `SeatConfirmService.releaseSeat()`로 좌석을 정리한다(멱등 구조라 뒤늦게 와도 안전).
- 즉 "주문 취소 → 좌석 해제" 방향은 이미 self-heal되므로, ticketing이 반대 방향으로 별도 채널을 또 만드는 건 중복 배관이라고 판단했다.

---

## 재검토 발동 (2026-07-10) — 교차 유저 주문 반환 버그

코드 리뷰에서 위 판단("기존 경로로 이미 해결됨")이 성립하지 않는 케이스가 발견됐다.

**버그:** 해제된 좌석의 stale PENDING 주문이 타임아웃(기본 10분)까지 order-service의 `uq_orders_seat_active`(seat_id 단독 partial unique index) 슬롯을 점유한다. 그 창 안에서:

1. A가 hold → checkout(PENDING 주문 생성) → `releaseHold()` — 좌석은 즉시 AVAILABLE, A의 주문은 PENDING 잔존
2. B가 같은 좌석을 hold → checkout → order-service INSERT가 유니크 위반
3. 멱등 폴백이 **userId 비교 없이 A의 주문을 B에게 반환** — 교차 유저 주문 노출
4. 이후 A의 주문이 타임아웃 취소되며 `order.hold.released`(A의 orderId) 발행 → ticketing이 **B의 좌석을 찾아 해제** — B의 정상 선점이 풀림

부수 결과 두 가지도 함께 기록: (a) 좌석을 스스로 해제한 유저의 잔존 주문이 여전히 결제 가능해 결제→자동환불 창이 생김, (b) `GET /api/v1/orders`에 해제한 좌석의 주문이 최대 ~10분간 PENDING으로 노출됨.

---

## 대응 방향 (확정, 착수 전)

- **A안 — order-service 폴백에 `Order.userId` 비교 가드 (즉시 적용 권장)**: 멱등 폴백에서 기존 주문의 userId ≠ 요청 userId면 기존 주문 반환 대신 409. 교차 유저 노출·좌석 탈취는 즉시 차단되지만, 좌석이 AVAILABLE인데 ~10분간 구매 불가한 상태와 결제→환불 창은 남는다.
- **B안 — 해제 시 주문 즉시 취소 채널 복원 (근본 해결)**: 반려안 3(`ticketing.seat.hold.released` Kafka 이벤트)의 재개. "삭제"가 아니라 order-service 기존 취소 경로(`OrderCancelWriter`)를 통한 취소 전이여야 하며, 이벤트 유실 대비로 기존 타임아웃 self-heal은 안전망으로 유지한다.
- A안이 들어가도 취소 이벤트 도착 전의 짧은 창이 있으므로, B안 채택 시에도 A안은 병행할 가치가 있다.

**현재 상태:** 방향만 확정, 코드 미반영. order 파트와 A안/B안 채택 협의 후 별도 이슈로 진행한다.

## 관련

- [flows.md §6](../flows.md#6-좌석-선점-해제사용자-직접-취소)
- [ADR 009](./009-order-cancel-integration.md) — 이 ADR과 반대로 "ticketing이 order-service를 호출하는" 연동이 실제로 필요했던 사례
- [ADR 016](./016-release-path-consistency.md) — 선점 해제 경로의 레이스 방지·멱등성
- [order-service/architecture.md §4](../../order-service/architecture.md) — `OrderTimeoutWriter`/`OrderCancelWriter`의 `order.hold.released` 발행 지점
