# ADR 009 — 결제 재시도 설계 (P1)

**날짜**: 2026-07
**상태**: 구현 완료

---

## 배경

현재 결제 실패 시 주문은 즉시 `FAILED`로 종료된다. "PG API 일시적 오류 시 재시도"라는 요구사항이 P1으로 남아있다. 환불 복구 배치 설계 중 payments 1:N 구조와 맞물린 설계 이슈들을 정리했다.

---

## 실제 구현 요약

아래 "결제 재시도 도입 시 핵심 설계 이슈"에서 검토한 옵션 중 실제로 채택된 것을 먼저 정리한다.

| 이슈 | 채택안 | 비고 |
|------|--------|------|
| 1. 현재 유효한 결제 시도 조회 | **옵션 B (포인터)** | `orders.latest_payment_id` 컬럼 추가. Payment 생성 시 같은 트랜잭션에서 갱신 |
| 2. 재시도 허용 상태 전이 | **방향 B (PAYMENT_REQUESTED 유지)** | Webhook `FAILED` + `TRANSIENT:` 접두사 → 주문 상태 유지, `Payment.retryable=true` 마킹. 상태 자체에 별도 의미를 얹지 않고 Payment 레벨 플래그로 구분 |
| 3. 멱등성 키 | 매 재시도마다 신규 발급 | `"retry-" + orderId + "-" + UUID` |
| 4. 재시도 대상 조건 | `failureReason`의 `TRANSIENT:` 접두사 | Mock PG가 시뮬레이션. 판단 시점은 웹훅 수신 시(`PgWebhookService`) — Payment에 `retryable` 플래그로 미리 마킹해두고, 스케줄러는 플래그만 보고 폴링 |

**설계 시점과 달라진 점**: 최초 설계 문서는 "재시도 가능 여부를 재시도 시점에 판단"하는 뉘앙스였으나, 실제로는 **웹훅 수신 시점에 `retryable` 여부를 미리 확정**해 Payment에 저장하는 방식으로 구현했다. 스케줄러 폴링 쿼리가 `retryable=true` 조건으로 바로 후보를 찾을 수 있어 더 단순하다.

**흐름**: `PaymentRetryScheduler`(폴링, 락 없이 후보 ID만 조회) → `PaymentRetryWriter.prepareRetry`(비관적 락 + 건당 트랜잭션, 새 Payment INSERT + 포인터 갱신) → `PaymentRetryWriter.requestApproval`(트랜잭션 밖, PG 재호출). 이후 흐름은 최초 결제 요청과 동일하게 웹훅으로 수렴한다. 상세 시퀀스는 [flows.md #8](../flows.md#8-결제-자동-재시도) 참고.

**설정값** (`OrderProperties.PaymentRetry`): `maxAttempts`(재시도 최대 횟수, 초과 시 주문 FAILED), `batchSize`(폴링 1회당 처리 건수), `pollIntervalMs`(폴링 주기).

**남은 갭**: PG 재요청 자체가 실패하면 새로 만든 Payment가 `REQUESTED`에서 orphan 상태로 남는데, 이를 정리하는 스케줄러가 없다(`OrderTimeoutScheduler`는 `PENDING` 주문만 처리). 아래 "구현 전 확인이 필요한 것들" 중 마지막 항목과 이어지는, 여전히 미해결인 문제다.

---

## 현재 구조 (설계 당시)

`payments` 테이블은 결제 시도마다 새 레코드를 INSERT하는 1:N 구조다(실패 이력 추적 목적). 그런데 현재 시점(결제 재시도 미구현)에서는 주문당 APPROVED Payment가 항상 하나다. 결제 실패 시 주문이 즉시 `FAILED`로 끝나기 때문에, 같은 주문에 두 번째 결제 시도가 이루어지는 경로 자체가 없다.

---

## 결제 재시도 도입 시 핵심 설계 이슈

### 1. "현재 유효한 결제 시도"를 어떻게 찾는가

결제 재시도가 들어오면 한 주문에 `FAILED`, `FAILED`, ..., `APPROVED` 순으로 Payment가 쌓일 수 있다. 환불, 재시도 횟수 관리, 복구 배치가 "어느 Payment를 봐야 하는지" 항상 명확해야 한다.

**옵션 A: 정렬 기반 조회**  
`findByOrderIdOrderByCreatedAtDescIdDesc`로 가장 최근 레코드를 "현재 결제 시도"로 간주. 구현이 단순하지만, 동일 밀리초 생성이 발생하면 정렬 순서가 모호해지는 극단적 엣지 케이스가 있다.

**옵션 B: 명시적 포인터 컬럼 (추천)**  
`orders`에 `latest_payment_id` 컬럼을 추가. 새 Payment 생성 시 같은 트랜잭션에서 포인터를 갱신. Stripe(실제 PG)의 `PaymentIntent.latest_charge` 패턴과 동일한 방식. O(1) 조회에 정렬 모호함이 없다.

단, 포인터 갱신 누락이라는 새로운 버그 클래스가 생긴다. Payment를 만드는 코드가 한 곳에 모여있다면(PaymentRequestWriter), 이 위험을 관리하기 쉽다.

**→ 구현 시 B(포인터 방식)를 채택하기로 방향 잡음. 지금은 주문당 APPROVED Payment가 하나뿐이라 `findByOrderIdAndPaymentStatus(APPROVED)`로 단순하게 처리하고, 재시도 구현 시 포인터로 전환.**

### 2. 재시도를 허용하는 상태 전이

결제 실패 후 재시도를 허용하려면 현재 `FAILED → [종료]`인 흐름을 바꿔야 한다. 두 가지 방향이 있다.

**방향 A: FAILED에서 PENDING으로 되돌리기**  
주문 상태 머신을 역행(FAILED → PENDING)시켜야 해서 상태 전이의 단방향성이 깨진다. 복잡도가 높다.

**방향 B: PAYMENT_REQUESTED에서 멈추고 재시도**  
PG API 호출 전에 `PAYMENT_REQUESTED`로 전이했으므로, 응답 실패 시 `FAILED`가 아닌 `PAYMENT_REQUESTED`에 머물면서 재시도할 수 있게 한다. 재시도 가능 횟수를 초과하면 그때 `FAILED`로 전환.

**→ 방향 B가 상태 머신의 단방향성을 유지하면서 재시도를 지원할 수 있어 선호. 다만 `PAYMENT_REQUESTED`가 "대기 중"과 "재시도 중" 두 의미를 갖게 되는 모호함이 생기므로, `payment_status`나 별도 메타데이터로 구분 필요.**

### 3. 멱등성 키 처리

동일 주문에 재시도 결제를 요청할 때, 첫 번째 시도의 `Idempotency-Key`를 그대로 쓰면 PG가 중복으로 간주해 처음 결과를 그대로 반환할 수 있다(의도하지 않은 캐시 히트). 재시도마다 새 `Idempotency-Key`를 발급해야 한다.

DB `idempotency_key` UNIQUE 제약이 걸려 있어서, 새 키를 쓰면 자동으로 새 Payment 레코드가 생성된다. 이 부분은 자연스럽게 처리된다.

### 4. 재시도 대상 조건

무한 재시도는 막아야 한다. "일시적 오류"(PG 타임아웃, 5xx)는 재시도 대상, "영구적 실패"(카드 한도 초과, 도난 카드 등)는 재시도해도 의미 없다.

PG 응답의 `failure_reason`으로 구분하거나, PG사가 제공하는 재시도 가능 여부 플래그를 활용해야 한다. Mock PG에서는 `FAIL_` 마커가 붙은 경우를 "영구 실패"로, 그 외 타임아웃은 "일시적 오류"로 취급하는 방식으로 시뮬레이션 가능.

---

## 관련 문서

- ADR 004: payments 1:N 구조
- ADR 008: 환불 복구 배치 (refund_retry_count 설계 맥락)