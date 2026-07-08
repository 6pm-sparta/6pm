# ADR 010 — holdId를 별도 테이블 없이 휘발성 UUID로 유지

**날짜**: 2026-07-07
**상태**: 확정

---

## 배경

`holdId`는 `SeatService.checkout()`이 order-service에 주문 생성을 요청할 때 함께 보내는 값으로, order-service의 Feign 호출 멱등성 1차 방어(Redis SETNX) 키로 쓰인다. README(#9 미확정 항목)에는 이 값을 별도 `SeatHolds` 테이블로 분리할지, `Orders.id`를 그대로 holdId로 쓸지가 미확정 항목으로 남아 있었다.

---

## 결정

별도 테이블을 만들지 않는다. `checkout()` 호출마다 `UUID.randomUUID()`로 새로 발급하는 현재 방식을 유지한다.

---

## 이유

- holdId는 지금 "이 checkout 시도 하나"에 대한 멱등성 키로만 쓰이고, 좌석 선점 상태 자체는 이미 Redis owner 키(`HELD`/`PENDING`/`CONFIRMED` + TTL)와 `ShowSeat.orderId`로 추적되고 있다. 별도 테이블은 이 정보를 중복 저장하는 셈이다.
- `CHECKOUT_CLAIM_SCRIPT`(Lua, 원자적)가 동일 좌석에 대한 동시 checkout 자체를 막아주므로, 매 시도마다 새 UUID를 발급해도 같은 좌석에 holdId가 두 번 쓰일 일이 없다. 정합성은 order-service의 seatId UNIQUE 인덱스로 2차 보장된다.
- `Orders.id`를 그대로 쓰는 안은 순서가 맞지 않는다 — holdId는 order가 생성되기 *전*, 좌석 선점 클레임 시점에 발급돼야 하는 값이라 아직 존재하지 않는 `Orders.id`를 가리킬 수 없다.
- 별도 테이블을 추가하면 Redis 상태와 동기화해야 할 대상이 하나 늘어나는 것과 같다. 지금도 겪고 있는 "Redis-DB 사이 크래시 레이스"류 정합성 이슈(`checkout()` 트랜잭션 커밋 지연 레이스, README §9)를 하나 더 만드는 셈이라 실익보다 비용이 크다.

---

## 재검토 조건

선점 이력 조회/감사(누가 언제 어떤 좌석을 몇 번 시도했는지 추적)가 실제 요구사항으로 들어오면, 그때 별도 테이블 도입을 재검토한다. 현재는 그런 요구사항이 없다.
