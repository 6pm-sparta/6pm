# ADR 015 — 주문 생성을 hold()에서 분리해 별도 체크아웃 API로 이전한다

**날짜**: 2026-07-10 (결정 자체는 2026-07-03, README에 흩어져 있던 기록을 소급 정리)
**상태**: 확정 (운영 중)

---

## 배경

원래 `hold()`가 좌석 선점과 주문 생성(order-service Feign 동기 호출)을 한 번에 처리했다. 가장 트래픽이 몰리는 hold 구간에 동기 cross-service 호출이 들어있어 병목·장애 전파 지점이 됐다.

---

## 결정

- `hold()`는 **좌석 선점만** 한다(Redis 전용 연산, 주문 없음, owner 상태 `HELD`). 응답 바디 없음 — orderId를 반환하지 않는다.
- 주문 생성은 별도 API `POST .../seats/{seatId}/checkout`으로 분리한다.
- `SeatService.checkout()` 흐름: owner가 `HELD`인지 확인 → `CHECKOUT_CLAIM_SCRIPT`로 `PENDING` 전이 → `orderClient.create()` 호출 → 성공 시 `CONFIRMED` 전이 + `HoldResponse(orderId)` 반환.
- 이미 `CONFIRMED`(재요청)면 주문 생성 없이 기존 orderId로 멱등 응답.
- 주문 생성 실패 시 해당 좌석의 선점만 롤백(seatKey/ownerKey 삭제, 재고/구매카운트 복구 — [ADR 005](./005-hold-rollback-on-order-failure.md)).

---

## 이유

- hold 구간을 가벼운 Redis 전용 연산으로 줄여 최대 트래픽 구간에서 외부 의존을 제거.
- owner 키 3단계 상태(`HELD`→`PENDING`→`CONFIRMED`)로 hold↔release↔checkout 동시 호출 레이스를 제어할 수 있게 됨([ADR 016](./016-release-path-consistency.md)).

---

## 영향 (알아둘 것)

- hold 응답에 orderId가 없으므로, **hold 응답에서 orderId를 캡처하던 기존 클라이언트/테스트는 전부 checkout 응답 기준으로 바꿔야 한다** (postman 컬렉션 13이 이 전환을 반영하지 못해 깨져 있음 — 발견 2026-07-10).

## 관련

- [SA-260703.md §11](../archive/SA-260703.md) — 분리 결정 당시 트레이드오프 논의
- [flows.md §3](../flows.md) — 체크아웃 흐름 상세
