# order-service Redis 키 설계 문서

> 대상: order-service에서 사용하는 Redis 키

## 한눈에 보기

| # | 용도 | 키 패턴 | 자료구조 | 위치 |
| --- | --- | --- | --- | --- |
| 1 | 주문 생성 멱등성 (holdId 1차 방어) | `order:hold:{holdId}` | String (SETNX) | `OrderCreationService` |
| 2 | 결제 요청 분산락 | `payment:lock:order:{orderId}` | Redisson RLock | `PaymentRequestService` |
| 3 | 결제 요청 멱등성 (Idempotency-Key) | `payment:idem:{idempotencyKey}` | String (SETNX) | `PaymentRequestService` |
| 4 | PG 웹훅 중복수신 차단 | `webhook:pg:{pgTransactionId}:{status}` | String (SETNX) | `PgWebhookService` |

공통 패턴: 모든 키가 `용도:세부:식별자` 형태의 콜론 구분 네이밍을 따르고, 전부 **SETNX(또는 RLock) + TTL** 조합으로 1차 방어를 구성한다. Redis 장애 시에는 각자 다른 방식의 2차 방어(DB 제약 / 상태 체크)로 폴백한다.

---

## 1. `order:hold:{holdId}` — 주문 생성 멱등성

**문제 상황**
Ticketing → Order Feign 호출이 네트워크 재시도 등으로 중복 전달될 수 있다. 같은 `holdId`로 두 번 들어오면 같은 좌석에 주문이 두 개 생기면 안 된다.

**동작**
```
SET order:hold:{holdId} "CLAIMED" NX EX {claimTtlSeconds}
```
- 성공(true) → 내가 1차 선점 → DB INSERT 진행
- 실패(false) → 이미 누가 처리 중이거나 끝남 → 멱등 응답 경로로 분기

INSERT 성공 후에는 같은 키에 `{orderId}`를 다시 SET해서(NX 없이 덮어쓰기) "CLAIMED" 마커를 실제 값으로 치환한다. 이렇게 하면 키 하나가 두 단계(처리 중 마킹 → 결과 캐싱)를 모두 책임지면서도, 값으로 `UUID 파싱 가능 여부`만 보면 두 상태를 구분할 수 있다.

**TTL 두 단계**

| 설정 | 값 | 의미                                                         |
| --- | --- |------------------------------------------------------------|
| `claimTtlSeconds` | 30초 | DB INSERT가 끝날 때까지만 버티면 되는 짧은 클레임 TTL                       |
| `cacheTtlSeconds` | 600초(10분, 임시값) | INSERT 성공 후 결과 캐시 TTL. 원칙상 Ticketing 좌석 선점 TTL과 동일하게 맞춰야 함 |

**2차 방어 (DB)**
```sql
CREATE UNIQUE INDEX uq_orders_seat_active
ON orders (seat_id)
WHERE status IN ('PENDING', 'PAYMENT_REQUESTED', 'PAID');
```
Redis 장애 시 1차 방어를 건너뛰고 바로 INSERT를 시도하며, 이 부분 UNIQUE 인덱스 충돌로 최종 차단한다. 충돌 시 Redis 클레임을 보상 삭제(DEL)한 뒤 DB에서 실제 진행중 주문을 찾아 멱등 응답으로 돌려준다.

**설계 결정 근거**
- `seat_id` 단독 UNIQUE가 아니라 "진행중 상태" 조건부 인덱스인 이유: 취소 후 재판매(재구매) 흐름과 충돌하지 않기 위함. CANCELLED/REFUNDED/FAILED는 인덱스 대상에서 제외.
- COMPENSATING/REFUND_REQUESTED도 제외했는데, 이는 "Ticketing이 `ticketing.seat.book.failed` 발행 시점에 좌석을 즉시 해제한다"는 전제에 기반함. 이 전제가 깨지면 이 두 상태도 인덱스에 포함해야 함(미확정 항목).
- check-then-act 대신 "일단 INSERT 시도 → 실패 시 DB 조회" 패턴 채택: 별도 존재 확인 쿼리 없이 INSERT 자체를 원자적 판단 기준으로 삼아 race window를 없앰.

---

## 2. `payment:lock:order:{orderId}` — 결제 요청 분산락

**문제 상황**
서버 인스턴스가 여러 대일 때 동일 주문에 대한 동시 결제 요청이 서로 다른 인스턴스로 들어오면, DB 비관적 락만으로는 "PG API 자체가 중복 호출되는 것"까지는 막지 못한다(각 인스턴스가 별도 트랜잭션을 열기 전 단계에서 막아야 함).

**동작**
Redisson `RLock.tryLock(waitSeconds, holdSeconds, TimeUnit)` 사용.

| 설정 | 값 | 의미 |
| --- | --- | --- |
| `lockWaitSeconds` | 3초 | 락 대기 시간. 짧게 둬서 대기 중인 요청은 빠르게 409로 거부 |
| `lockHoldSeconds` | 5초 | 락 점유 시간. **PG 호출 전체를 감싸지 않는다** |

**락 범위가 좁은 이유 (중요한 설계 포인트)**
락은 "PENDING → PAYMENT_REQUESTED 전이 + Payment 레코드 생성, 커밋"까지만 감싸고 풀린다. PG 호출(`requestApprovalAndCache`)은 락 밖에서 수행된다.
- 락이 풀린 뒤 들어오는 동시 요청은 이미 주문 상태가 `PAYMENT_REQUESTED`로 바뀌어 있으므로 상태 검증에서 자연히 거부된다.
- 즉 **분산락은 1차 방어(상태 전이 구간의 동시성), DB 상태값은 2차 방어(PG 호출 구간 전체)**로 역할이 분리되어 있다. 외부 API 호출처럼 느릴 수 있는 구간을 락으로 감싸지 않는 것은 좋은 판단 — 락 점유 시간이 외부 의존성에 끌려다니면 장애 전파 위험이 커진다.

**락 획득 순서**
1. Redis 분산락 획득
2. 멱등성 키 확인 (락 안에서, 재확인 포함 — 락 획득 전후 경쟁 상황 대비)
3. DB 비관적 락(`SELECT FOR UPDATE`) + 상태 검증 + 상태 전이 + 커밋
4. 분산락 해제
5. PG 비동기 승인 요청 (락 밖)
6. 결과 캐싱

---

## 3. `payment:idem:{idempotencyKey}` — 결제 요청 멱등성

**문제 상황**
클라이언트가 발급한 `Idempotency-Key`로 재시도/중복 클릭을 막아야 한다. PG 응답이 비동기(webhook)이기 때문에, 캐싱하는 건 "최종 승인 결과"가 아니라 "요청이 접수됐다"는 스냅샷이다.

**값의 3가지 상태**

| 값 | 의미 | 처리 |
| --- | --- | --- |
| 키 없음 | 신규 요청 | 정상 진행 |
| `"IN_PROGRESS"` | 마킹 이후 ~ 캐싱 이전 구간 (동시 처리 중) | 409 반환 |
| `PaymentResponse` JSON | 처리 완료 (REQUESTED 상태 스냅샷) | 캐시값 그대로 200 반환 |

**TTL**
`idempotencyKeyTtlSeconds = 600초`, 주문 `expirationMinutes(10분)`와 동일하게 맞춤 — 결제 유효 시간과 멱등성 캐시 유효 시간을 일치시키는 합리적 선택.

**2차 방어**
DB 레벨 UNIQUE 제약은 `payments.idempotency_key`에 걸려 있지만(테이블 설계), 코드 흐름상 1차인 Redis가 장애나면 멱등성 마킹 자체를 생략하고 결제 흐름을 그대로 진행한다 — 이 경우 최종 방어는 **PG 자체 멱등키**에 위임한다(자체 DB UNIQUE 제약을 활용한 명시적 차단 로직은 별도로 없음, PG가 멱등성을 보장한다고 가정).

> 🔍 **검토 포인트**: PG 접수(`requestApprovalAndCache`)가 성공해서 `recordPgTransactionId`까지 끝났는데 `cacheResult` 호출 전에 예외/장애가 발생하면, `IN_PROGRESS` 마커가 TTL(10분) 동안 풀리지 않는다. 그 사이 들어오는 재시도는 전부 409로 막히는데, 실제로는 PG에 결제가 이미 접수된 상태라 사용자 입장에서는 "결제가 진행 중인데 재시도가 막힌다"는 애매한 구간이 생긴다. TTL 만료까지 기다리거나 GET으로 상태 확인을 유도하는 정도로 받아들일 트레이드오프인지 판단 필요.

---

## 4. `webhook:pg:{pgTransactionId}:{status}` — PG 웹훅 중복수신 차단

**문제 상황 (이슈 #161 트러블슈팅)**
원래는 `pgTransactionId`만으로 키를 구성했었는데, 같은 트랜잭션 ID에 대해 `APPROVED` 콜백과 `REFUNDED` 콜백이 둘 다 올 수 있다는 걸 놓쳐서, REFUNDED 콜백이 APPROVED의 중복으로 오인되어 차단되는 버그가 있었다. 그 결과 주문이 `REFUND_REQUESTED`에서 영구 고착되는 문제가 발생.

**수정된 키 구조**
```
DEDUPE_KEY_PREFIX + pgTransactionId + ":" + status
```
`status`를 키에 포함시켜서 같은 트랜잭션이라도 상태가 다르면 별개의 키로 취급되도록 함.

**TTL**
`dedupeTtlSeconds = 600초`

**2차 방어의 실체**
Redis 장애 시 `claim()`은 `return true`(즉 통과)로 처리한다. 별도의 DB UNIQUE 제약 같은 명시적 2차 방어는 없고, 실질적인 2차 방어는 `PaymentRequestWriter`의 각 Writer 메서드(`applyApproval`, `applyFailure` 등)가 **주문이 기대하는 상태가 아니면 조용히 no-op(return)** 하는 멱등 설계에 의존한다. 비관적 락(`findByIdForUpdate`) 안에서 상태를 검증하므로 race condition은 없지만, "1차 방어 = Redis SETNX", "2차 방어 = DB UNIQUE 제약"이라는 다른 세 키와는 성격이 다르다는 점은 명확히 인지하고 있어야 함 (다른 키들처럼 DB 레벨의 독립적인 방어선이 아니라, 비즈니스 로직의 상태 체크에 기댄 방어선).

---

## 키 네이밍 컨벤션 정리

- prefix는 `{도메인}:{세부용도}:` 형태로 콜론 구분
- 마커 문자열(`CLAIMED`, `IN_PROGRESS`, `RECEIVED`)은 실제 저장될 값(UUID, JSON)과 형식적으로 구분 가능하게 설계 — 파싱 시도로 상태 판별
- 모든 SETNX는 `try-catch(DataAccessException)`으로 Redis 장애를 격리하고, 장애 시 동작(통과/폴백)을 키마다 명시적으로 결정해둠

## 공통 장애 대응 패턴

전 키 공통으로 "Redis 장애 시 완전히 막히지 않고, 신뢰도가 낮아지더라도 서비스는 계속 동작한다"는 원칙을 따른다. 다만 각 키마다 장애 시 폴백 강도가 다르다는 점이 핵심:

| 키 | 장애 시 동작 | 안전망 강도 |
| --- | --- | --- |
| `order:hold:*` | 1차 생략, DB 부분 UNIQUE 인덱스로 확실히 차단 | 강함 |
| `payment:idem:*` | 1차 생략, PG 자체 멱등키에 위임(우리 쪽 직접 통제 불가) | 약함 |
| `webhook:pg:*` | 무조건 통과, Writer의 상태 체크 no-op에 위임 | 중간 (race는 없지만 명시적 DB 제약은 아님) |
| `payment:lock:order:*` | (RLock 자체 장애 시나리오는 별도 문서화 안 됨) | - |