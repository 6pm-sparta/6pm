# ADR 008 — 환불 미완료 복구 배치

> 📌 **상태명 변경 참고**: 이 문서는 (주문 상태 머신 재설계) 이전에 작성되어 `PAYMENT_REQUESTED`/`PAID`/`REFUND_REQUESTED`/`REFUNDED`/`COMPENSATING` 같은 옛 상태명을 그대로 쓴다. 결정 배경을 남긴 역사적 기록이라 원문은 유지하고, 현재 상태명 매핑은 [architecture.md](../architecture.md) 3번 섹션을 참고할 것.

**날짜**: 2026-07  
**상태**: 확정

---

## 배경

`OrderCancelService`와 `OrderCompensationService`가 PG 환불 실패 시 로그만 남기고 끝낸다. 재처리 메커니즘이 없어서 `REFUND_REQUESTED`/`FAILED`(환불 거절)에 멈춘 주문이 방치된다.

---

## 결정 1: "무조건 재시도" 대신 "거래조회 후 분기"

환불이 멈춘 원인이 두 가지일 수 있다.

1. **PG는 환불을 완료했는데 webhook만 유실** → 재시도 불필요, DB 동기화만 하면 됨
2. **PG가 환불을 거절** → 재시도 필요

이 두 케이스를 구분하지 않으면 1번에서 이미 끝난 거래에 또 환불을 요청하게 된다. `PaymentGateway.inquireTransaction()`으로 먼저 진짜 상태를 확인하고 분기한다.

| 거래조회 결과 | 처리 |
|---------------|------|
| REFUNDED | 재환불 없이 우리 쪽 상태만 동기화(SYNCED) |
| REFUND_FAILED / APPROVED | 재환불 요청(RETRIED) |
| 조회 결과 없음 | 즉시 수동 처리 전환(EXHAUSTED) |

---

## 결정 2: FAILED 상태에서 재시도 시 REFUND_REQUESTED로 복귀

환불이 거절돼 `FAILED`로 끝난 주문을 재시도할 때, 주문 상태를 `FAILED`인 채로 두면 안 된다. 재환불을 요청한 순간부터 이 주문은 다시 "PG 환불 처리 대기 중"이 된 거라 `REFUND_REQUESTED`로 되돌려야 다음 webhook 처리가 정상 동작한다.

`Order.markRefundRequested()` 가드에 `FAILED`를 허용하도록 확장했다. 기존 호출부(`OrderCancelWriter`, `OrderCompensationWriter`)는 자체 분기로 `FAILED`에서 이 메서드에 도달하는 경로가 없어 영향받지 않는다.

---

## 결정 3: MANUAL_REVIEW_REQUIRED 신규 상태값

재시도 소진 시 기존 `FAILED`를 그대로 쓰면 "수동 처리가 필요한 주문 목록" 조회 조건이 복잡해진다(`FAILED` + `payment.status=APPROVED` + `refund_retry_count >= 3` 등 3개 테이블 조인). 스케줄러가 같은 주문에 중복 알림을 보낼 위험도 있다.

`MANUAL_REVIEW_REQUIRED`를 별도 종료 상태로 추가했다. 이 상태의 주문은 스케줄러 스캔 대상에서 제외되고, `GET /admin/v1/orders/manual-review`로 목록 조회 가능하다.

---

## 결정 4: refund_retry_count는 payments 테이블에

재시도 횟수를 어디에 둘지 고민했다.

- `order_status_histories` 이력 개수로 계산: "재시도" 이력만 필터링해야 해서 쿼리가 복잡하고, 상태 변경 없는 재시도를 표현하기 어렵다.
- `payments.refund_retry_count` 컬럼: 명시적이고, 조회가 단순하며, 원자적 업데이트가 쉽다.

`payments.refund_retry_count` 컬럼을 채택했다. 현재 시점(결제 재시도 미구현)에서는 주문당 APPROVED Payment가 하나라 어느 레코드에 두는지 모호하지 않다.

---

## 배치 구조

`OrderTimeoutScheduler`/`OrderTimeoutWriter` 패턴을 그대로 따랐다.

1. `RefundRecoveryScheduler`가 락 없이 폴링으로 후보 ID 목록만 조회
2. 후보 건마다 `try-catch`로 감싸서 처리 (한 건의 실패가 배치 전체를 막지 않음)
3. 실제 처리는 `RefundRecoveryWriter`가 건당 독립 트랜잭션 안에서 비관적 락을 잡고 상태를 재검증한 뒤 수행

---

## 운영 알림

재시도 소진 시 `log.error`로만 남긴다. Prometheus/Loki 기반 observability 스택에서 이 로그를 감지해 알림을 보낼 수 있다. Slack 직접 연동은 추후 고도화.

---

## 한계 및 후속 과제

- `MANUAL_REVIEW_REQUIRED` 주문을 운영자가 수동 처리 후 상태를 어떻게 갱신하는지 관리 도구 미비. 지금은 DB 직접 수정이 유일한 방법.
- 실제 PG 연동 시 거래조회 API 스펙이 PG사마다 달라 `PgTransactionResult` enum 재설계 필요.