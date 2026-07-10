# ticketing-service Redis 키 설계 문서

> 대상: ticketing-service에서 사용하는 Redis 키

## 한눈에 보기

| # | 용도 | 키 패턴 | 자료구조 | TTL | 위치 |
| --- | --- | --- | --- | --- | --- |
| 1 | 대기열 | `waiting_queue:{showId}` | Sorted Set (score=진입 timestamp) | - | `QueueService`, `QueueScheduler` |
| 2 | 구매 자격 토큰 | `purchase-token:{showId}:{userId}` | String (SETNX) | 600초 | `PurchaseTokenService` |
| 3 | 좌석 상태(클라이언트 노출) | `show:{showId}:seat:{seatId}` | String | 600초 → 확정 시 영구 | `SeatService`, `SeatConfirmService` |
| 4 | 좌석 소유권(내부 동시성 제어) | `show:{showId}:seat:{seatId}:owner` | String (`{userId}:{status}`) | 600초(seatKey와 동일 시작점) | `SeatService`, `SeatConfirmService` |
| 5 | 잔여 재고 카운터 | `inventory:{showId}` | String (Counter) | - | `SeatService`, `SeatConfirmService` |
| 6 | 사용자별 구매 카운터 | `purchase-count:{userId}:{showId}` | String (Counter) | - | `SeatService`, `SeatConfirmService` |

공통 패턴: 1~4는 진입 순서를 강제하는 게이트(SETNX/NX 옵션) 역할, 5~6은 단순 증감 카운터다. 좌석 관련 3~6 중 `SeatService`(hold/checkout/releaseHold)가 다루는 구간은 `HOLD_SCRIPT`/`CHECKOUT_CLAIM_SCRIPT`/`RELEASE_SCRIPT`/`CONFIRM_OWNER_SCRIPT` 네 개의 Lua 스크립트로 원자성을 보장한다. `SeatConfirmService`(confirmSeat/releaseSeat)와 `releaseExpiredHold()`(TTL 만료)는 Lua를 쓰지 않고 개별 Redis 명령을 순차 실행한다 — 상세는 아래 참고.

---

## 1. `waiting_queue:{showId}` — 대기열

**문제 상황**
선착순 티켓팅이라 동시 진입 시 순번이 꼬이면 안 되고, 같은 유저가 중복으로 줄을 서면 안 된다.

**동작**
```
ZADD waiting_queue:{showId} NX {timestamp} {userId}
```
- `NX`: 이미 대기열에 있으면 추가 안 함(중복 진입 방지)
- score = 진입 시각(`System.currentTimeMillis()`) — 먼저 온 사람이 앞 순번

`QueueScheduler`가 60초 주기(`queue.scheduler.delay`, 기본 60000ms — 로컬 테스트 시 `QUEUE_SCHEDULER_DELAY` 환경변수로 단축 가능)로 앞 50명(`ZRANGE 0~49`)을 추출해 `purchase-token`을 발급하고 `ZREM`으로 대기열에서 제거한다. 이 키는 별도 초기화 로직이 없고 `enter()` 시점에 최초 생성되며, 비면 자연히 사라진다 — `QueueScheduler`는 매 주기 `waiting_queue:*` 패턴으로 활성 show를 스캔해 처리 대상을 찾는다.

**설계 결정 근거**
- Sorted Set을 택한 이유: `ZRANK`로 순번 조회, `ZRANGE`로 배치 추출이 모두 O(log N)이라 대기열 규모가 커져도 SSE 순번 안내가 느려지지 않는다.
- 배치 크기 50은 하드코딩(`BATCH_SIZE`). 트래픽 규모에 따라 조정 여지가 있으나 별도 설정화는 안 되어 있음.

---

## 2. `purchase-token:{showId}:{userId}` — 구매 자격 토큰

**문제 상황**
대기열을 통과한 사람만 좌석 선점(`hold`)/체크아웃을 시도할 수 있어야 한다.

**동작**
```
SETNX purchase-token:{showId}:{userId} "1" EX 600
```
`hold()`와 `checkout()` 모두 진입 시 가장 먼저 `EXISTS`로 이 키를 확인한다. 없으면 `PURCHASE_TOKEN_NOT_FOUND`(403)로 즉시 거부한다.

**TTL 자연 소멸만 사용**
토큰은 성공/실패 여부와 무관하게 TTL(600초)로만 만료된다 — 별도 삭제 로직이 없다. 좌석을 하나 선점(hold)한 뒤에도 토큰이 살아있는 동안은 다른 좌석을 계속 시도할 수 있다(구매 한도는 `purchase-count`가 별도로 막는다).

**변경 이력 (2026-06-23)**
원래는 Gateway가 좌석 선택 화면 진입 시점에 토큰을 검증한다고 설계했으나, 실제로는 좌석 목록 조회(`GET .../seats`)는 토큰 없이 가능하고, 검증은 `hold()`/`checkout()` 시점에 `SeatService`가 직접 한다. 자세한 배경은 [260623 대기열 토큰.md](./archive/260623%20대기열%20토큰.md) 참고.

---

## 3~4. `show:{showId}:seat:{seatId}` / `:owner` — 좌석 상태 + 소유권

두 키는 4개의 Lua 스크립트로 원자적으로 함께 갱신된다. 상태 모델 자체는 [architecture.md §3](./architecture.md#3-좌석-상태-모델)에서 다루고, 여기서는 스크립트별 동작을 정리한다.

### `HOLD_SCRIPT` — 선점

```lua
local inv = tonumber(redis.call('GET', KEYS[2]) or '0')
if inv <= 0 then return -1 end
local cnt = tonumber(redis.call('GET', KEYS[3]) or '0')
if cnt >= tonumber(ARGV[1]) then return -2 end
local ok = redis.call('SET', KEYS[1], 'HOLDING', 'NX', 'EX', '600')
if not ok then return 0 end
redis.call('SET', KEYS[4], ARGV[2] .. ':' .. ARGV[3], 'EX', '600')
redis.call('DECR', KEYS[2])
redis.call('INCR', KEYS[3])
return 1
```
KEYS = `[seatKey, inventoryKey, countKey, ownerKey]`, ARGV = `[maxPerUser, userId, "HELD"]`. 재고 확인 → 구매 한도 확인 → 좌석 선점(`SET NX`) → 소유권 기록 → 재고/카운트 갱신을 한 번의 원자적 실행으로 처리한다. 반환값 `1`=성공, `0`=이미 선점됨, `-1`=재고없음, `-2`=한도초과.

### `CHECKOUT_CLAIM_SCRIPT` — 체크아웃 진입 클레임

```lua
local owner = redis.call('GET', KEYS[1])
if not owner then return -1 end
local sep = string.find(owner, ':')
local ownerId = string.sub(owner, 1, sep - 1)
local status = string.sub(owner, sep + 1)
if ownerId ~= ARGV[1] then return -2 end
if status == 'CONFIRMED' then return 2 end
if status == 'PENDING' then return -3 end
redis.call('SET', KEYS[1], ARGV[1] .. ':PENDING', 'KEEPTTL')
return 1
```
KEYS = `[ownerKey]`, ARGV = `[userId]`. `HELD` 상태의 소유권을 `PENDING`으로 원자적으로 전이한다(TTL은 유지 — `KEEPTTL`). 반환값 `1`=클레임 성공, `2`=이미 CONFIRMED(멱등 응답 대상), `-1`=선점 없음/만료, `-2`=본인 선점 아님, `-3`=이미 체크아웃 처리 중(동시 중복 요청).

### `CONFIRM_OWNER_SCRIPT` — 주문 생성 완료 반영

```lua
local owner = redis.call('GET', KEYS[1])
if not owner then return 0 end
redis.call('SET', KEYS[1], ARGV[1] .. ':' .. ARGV[2], 'KEEPTTL')
return 1
```
KEYS = `[ownerKey]`, ARGV = `[userId, "CONFIRMED"]`. `checkout()`이 `orderClient.create()` 성공 후 호출한다. TTL을 유지한 채(`KEEPTTL`) 상태 문자열만 갈아 끼운다.

### `RELEASE_SCRIPT` — 해제

```lua
local owner = redis.call('GET', KEYS[1])
if not owner then return -1 end
local sep = string.find(owner, ':')
local ownerId = string.sub(owner, 1, sep - 1)
local status = string.sub(owner, sep + 1)
if ownerId ~= ARGV[1] then return -2 end
if status == 'PENDING' then return -3 end
redis.call('DEL', KEYS[1])
redis.call('DEL', KEYS[2])
redis.call('INCR', KEYS[3])
redis.call('DECR', KEYS[4])
return 1
```
KEYS = `[ownerKey, seatKey, inventoryKey, countKey]`, ARGV = `[userId]`. `HELD`/`CONFIRMED` 상태에서만 해제를 허용하고 `PENDING`(체크아웃 진행 중)은 거부한다 — 여기가 hold↔checkout↔release 3자 레이스를 막는 핵심 지점이다. `PENDING`인데 해제를 허용하면, 체크아웃이 뒤늦게 `orderClient.create()`에 성공해 DB에 `order_id`를 써넣는 순간 Redis는 이미 비어 있어 좌석이 이중으로 잡힐 수 있다.

### Lua를 쓰지 않는 경로 — `confirmSeat()` / `releaseSeat()` / `releaseExpiredHold()`

결제 완료·실패·취소 이벤트를 Kafka 컨슈머가 처리하는 경로(`SeatConfirmService.confirmSeat()`/`releaseSeat()`)와 TTL 자연 만료 경로(`SeatService.releaseExpiredHold()`)는 Lua 스크립트가 아니라 단건 `GET`/`SET`/`DEL`/`INCR`/`DECR` 조합을 순차 실행한다. 이 세 경로는 이미 order-service 쪽에서 상태 전이가 끝났거나(Kafka 이벤트) Redis TTL이 자체 만료시킨 뒤 도착하는 처리라 동시 요청 경합을 걱정할 필요가 없기 때문(같은 `orderId`로 두 이벤트가 겹쳐 와도 각 연산이 멱등: `SET`은 최종값이 같고, `DEL`은 이미 없어도 안전).

### BOOKED는 TTL 없이 영구 SET, DELETE 아님

`confirmSeat()`은 `seatKey`를 `DEL`이 아니라 `SET ... "BOOKED"`(TTL 없음)로 바꾼다. `DEL`하면 `getSeats()`가 기본값 `AVAILABLE`로 반환해 이미 팔린 좌석이 재선점 가능한 것처럼 보이고, `hold()`의 `SET NX`가 이를 그대로 허용해버리는 더블부킹 버그가 생기기 때문이다.

---

## 5. `inventory:{showId}` — 잔여 재고 카운터

**문제 상황**
`show_seats` 테이블에서 매번 `COUNT(*) WHERE order_id IS NULL`을 조회하면 hold 같은 고빈도 요청마다 DB를 때리게 된다.

**동작**
`HOLD_SCRIPT`가 `DECR`, `RELEASE_SCRIPT`/`releaseSeat()`/`releaseExpiredHold()`가 `INCR`한다.

**Lazy 초기화**
쇼/좌석 생성 시점에 이 키를 세팅하는 곳이 없다. 첫 `hold()` 요청이 들어왔을 때 키가 없으면, DB에서 `order_id IS NULL`인 좌석 수를 세어 `SETNX`로 초기화한다(`ensureInventoryInitialized`). `SETNX`라서 동시 요청이 몰려도 한 번만 세팅된다.

> **[TODO]** 좌석/쇼 생성 API가 아직 없어서(architecture.md §6) 이 lazy 초기화가 사실상 유일한 초기화 경로다. 좌석 생성 API가 추가되면 생성 시점에 미리 세팅하는 방식으로 바꿀지 검토 필요.

---

## 6. `purchase-count:{userId}:{showId}` — 사용자별 구매 카운터

**문제 상황**
한 유저가 같은 쇼의 좌석을 무제한으로 선점하지 못하게 막아야 한다(`MAX_PER_USER = 4`).

**동작**
`HOLD_SCRIPT`가 선점 성공 시 `INCR`. 해제되는 모든 경로(`checkout()` 실패 롤백, `releaseHold()`, `releaseExpiredHold()`, `releaseSeat()`)가 각각 `DECR`한다.

**해결된 버그 (#286, 2026-07-06) — TTL 만료/결제실패·취소 경로에서 감소하지 않던 문제**

| 경로 | count 감소 여부 |
| --- | --- |
| `hold()` 성공 | INCR (증가) |
| `checkout()` 주문 생성 실패 롤백 | DECR |
| `releaseHold()` (사용자 직접 해제, `RELEASE_SCRIPT`) | DECR |
| `releaseExpiredHold()` (TTL 자연 만료) | DECR (수정 완료) |
| `releaseSeat()` (결제 실패/취소/`hold.released`) | DECR (수정 완료) |

과거엔 정상적으로 좌석을 선점했다가 결제까지 못 가고(타임아웃) 풀리거나 결제 실패/취소된 경우 카운터가 영구히 남아, 실제로는 아무것도 못 산 유저가 `PURCHASE_LIMIT_EXCEEDED`(400)에 막히는 버그가 있었다. `SeatService.releaseExpiredHold()`/`SeatConfirmService.releaseSeat()`에 `decrement` 로직을 추가해 해결함(owner 값에서 userId를 파싱해 카운트를 내림 — owner가 이미 없으면 다른 경로가 먼저 처리한 것으로 보고 중복 감소하지 않음).

`GET /purchase-limit` 엔드포인트(`SeatController.java:63-68`)는 구현 완료됐고 설계가 미확정인 상태가 아니다.

**확정 후 환불 시 카운터가 안 내려가는 한계**
`confirmSeat()`은 `ownerKey` 값을 읽지 않고 바로 `DEL`한다. 그래서 확정된 좌석이 이후 환불되어 `releaseSeat()`가 호출될 때는 `ownerKey`가 이미 없어 `owner == null`이고, `purchase-count`를 내릴 userId를 구할 수 없어 감소가 스킵된다 — 확정 후 환불 건은 구매 한도 카운트를 영구히 소비한 채로 남는다.

**리팩토링 계획 (착수 전) — Set 기반으로 전환**
지금의 순수 `INCR`/`DECR` 카운터는 해제 경로마다(`checkout()` 실패 롤백/`releaseHold()`/`releaseExpiredHold()`/`releaseSeat()`) 사람이 일일이 짝을 맞춰 감소시켜야 하는 구조라, 한 곳이라도 빠지면 카운터가 영구히 새는 버그 클래스(#286이 바로 이 유형)가 항상 잠재해있다. 유저가 현재 보유한 좌석을 카운터 대신 **Set으로 직접 추적**하는 방식으로 바꾸는 걸 계획 중이다:

```
SADD purchase-seats:{userId}:{showId} {seatId}   # hold 시
SREM purchase-seats:{userId}:{showId} {seatId}   # 모든 해제 경로에서 공통
SCARD purchase-seats:{userId}:{showId}           # 한도 체크(getPurchaseLimit)
```

`SADD`/`SREM`은 멱등이라(이미 있는 걸 또 넣거나, 없는 걸 지워도 안전) 여러 해제 경로가 중복 호출돼도 구조적으로 안전하다 — INCR/DECR처럼 호출 누락이 카운터 드리프트로 이어지는 버그 클래스 자체가 사라진다. 단, 위 "확정 후 환불" 한계는 이 전환만으로는 해결되지 않는다 — `confirmSeat()`가 userId를 남기지 않는 구조가 그대로면 `SREM`도 같은 이유로 스킵되므로, 이 리팩터링과 별개로 `confirmSeat()`가 userId를 확정 후에도 조회 가능하게 남기는 변경이 함께 필요하다.

---

## 키 네이밍 컨벤션 정리

- prefix는 `{도메인}:{세부}:{식별자}` 형태로 콜론 구분(`show:{showId}:seat:{seatId}:owner`처럼 계층이 깊어져도 동일 규칙)
- owner 값처럼 "여러 정보를 한 문자열에 담아야 할 때"는 `{}:{}` 콜론 구분 + Lua `string.find`로 파싱하는 패턴을 일관되게 사용(`HOLD_SCRIPT`/`CHECKOUT_CLAIM_SCRIPT`/`RELEASE_SCRIPT` 전부 동일)
- 상태를 나타내는 값(`HELD`/`PENDING`/`CONFIRMED`/`HOLDING`/`BOOKED`)은 전부 대문자 영문 상수 문자열

## 공통 장애 대응 패턴

order-service의 Redis 키들과 달리, ticketing-service 좌석 관련 키들은 **DB 2차 방어가 없다** — `show_seats`에 상태 컬럼 자체가 없으므로(architecture.md §2 설계 포인트), Redis 장애 시 좌석 상태를 대체 조회할 방법이 없다. 유일한 완화책은 order-service 쪽 `uq_orders_seat_active` 부분 UNIQUE 인덱스([order-service/redis-keys.md §1](../order-service/redis-keys.md#1-orderholdholdid--주문-생성-멱등성))가 "이미 진행중 주문이 있는 좌석에 또 주문이 생기는 것"만 막아준다는 점 — 좌석 자체의 이중 선점(Hold)까지는 못 막는다. Redis가 곧 SoT라는 설계의 트레이드오프로, [architecture.md §6](./architecture.md#6-미확정-항목)의 Kafka 발행 신뢰성 항목과 함께 인프라 가용성 요구사항이 높은 지점이다.
