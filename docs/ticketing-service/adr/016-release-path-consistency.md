# ADR 016 — 선점 해제 3경로의 레이스 방지와 멱등성 보장 방식 (#326, #365)

**날짜**: 2026-07-10 (수정 자체는 2026-07-07 #326, 2026-07-08 #365 — README에 흩어져 있던 기록을 소급 정리)
**상태**: 확정 (운영 중)

---

## 배경

좌석 선점은 세 경로로 풀린다 — 사용자 직접 취소(`releaseHold`), TTL 자연 만료(`releaseExpiredHold`), 결제 실패/취소·주문취소 Kafka 이벤트(`releaseSeat`). 각 경로가 checkout과 경합하거나 중복 실행될 때의 정합성 규칙이 필요했다.

---

## 결정

### 1. hold ↔ release ↔ checkout 레이스 방지 — owner 3단계 상태 가드

owner 키가 `HELD`(선점만) → `PENDING`(체크아웃 진행 중, `orderClient.create` 외부 호출) → `CONFIRMED`(주문 생성 완료)로 전이한다.

- `releaseHold()`는 **`PENDING`인 동안만** `SEAT_HOLD_PROCESSING`(409)으로 거부하고, `HELD`/`CONFIRMED`에서는 해제를 허용한다. 이 가드가 없으면 "Redis는 풀렸는데 DB에는 뒤늦게 orderId가 박히는" 더블부킹 레이스가 생긴다.
- 같은 가드를 `releaseExpiredHold()`(TTL 만료 경로)에도 적용한다 — owner가 `PENDING`이면 해제를 스킵하고 경고 로그만 남긴다(#326).

### 2. releaseHold()의 원자성 — Lua `RELEASE_SCRIPT`

사용자 직접 취소 경로는 Lua 스크립트(`RELEASE_SCRIPT`)로 owner 확인→키 삭제→재고 INCR→구매수 DECR을 원자 처리한다. **Lua를 쓰는 해제 경로는 이것뿐이다.**

### 3. releaseSeat()의 중복 수신 멱등성 — DB 가드 (#365)

Kafka at-least-once 재전송으로 같은 orderId가 중복 호출돼도 재고/구매수가 두 번 증감하지 않도록:

- **DB 가드**: `findByOrderId(orderId)`로 좌석을 못 찾으면(이미 다른 경로가 해제) 조용히 스킵.
- **owner-null 가드**: owner 키가 이미 없으면(releaseHold가 먼저 처리) purchase-count DECR을 스킵해 중복 감소를 막는다.

> ⚠️ **주의 — 이 경로는 Lua가 아니다**: `releaseSeat()`은 개별 redisTemplate 명령(GET/DELETE/INCR/DECR)을 순차 실행한다. 과거 README가 "RELEASE_SCRIPT로 원자 처리"라고 잘못 기술한 적이 있어 여기 바로잡는다(2026-07-10 코드 리뷰에서 발견). 순차 중복 수신은 위 가드로 안전하지만, **같은 orderId의 동시(concurrent) 중복 수신**은 두 트랜잭션이 모두 가드를 통과해 재고를 이중 INCR할 수 있는 이론상 창이 남아 있다 — 알려진 한계로 기록.

---

## 관련

- [redis-keys.md §Lua 스크립트](../redis-keys.md) — `HOLD_SCRIPT`/`CHECKOUT_CLAIM_SCRIPT`/`RELEASE_SCRIPT` 상세
- [ADR 011](./011-hold-release-no-order-cancel-call.md) — releaseHold의 order-service 미연동 (재검토 발동 중)
- [ADR 015](./015-split-checkout-from-hold.md) — owner 3단계 상태의 도입 배경
