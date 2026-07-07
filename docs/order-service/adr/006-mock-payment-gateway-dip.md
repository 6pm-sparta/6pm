# ADR 006 — MockPaymentGateway DIP 구조

**날짜**: 2026-06  
**상태**: 확정 (2026-07 Mock PG 고도화 반영)

---

## 배경

실제 PG 없이 개발·테스트 환경에서 결제 흐름 전체를 검증해야 한다.

---

## 결정: PaymentGateway 인터페이스 + MockPaymentGateway 구현체 (DIP)

`PaymentGateway` 인터페이스를 두고 `MockPaymentGateway`가 이를 구현한다. 서비스 레이어는 인터페이스에만 의존하므로 실제 PG 구현체로 교체할 때 서비스 코드를 건드리지 않아도 된다.

---

## 패키지 구조

```
payment/infra/pg/
├── PaymentGateway.java          인터페이스 (공용)
├── PgTransactionResult.java     거래 결과 enum (공용)
├── PgTransactionStatus.java     거래조회 결과 DTO (공용)
├── PgWebhookHmacUtil.java       webhook 서명 검증 (verify 전용, 공용)
├── HmacCalculator.java          HMAC 계산 공통 로직
└── mock/
    ├── MockPaymentGateway.java
    ├── MockPgTransaction.java        Mock PG 자체 거래 기록
    ├── MockPgTransactionRepository.java
    ├── MockPgWebhookCallbackSender.java
    └── MockPgWebhookSigner.java      webhook 서명 생성 (Mock 전용)
```

PG 서명`sign()`과 검증`verify()`를 분리한 이유: 실제 PG라면 PG사가 자기 비밀키로 서명해서 보내므로 우리가 `sign()`을 만들 일이 없다. `sign()`은 "Mock이 PG인 척 webhook을 만들어 보내는" Mock 전용 책임이다.

---

## MockPaymentGateway 시나리오 트리거

`Idempotency-Key` 헤더 접두사로 결정론적 시나리오를 재현한다.

| Idempotency-Key 접두사 | 동작 |
|------------------------|------|
| (없음, 일반) | webhook 콜백 APPROVED |
| `FAIL_` | webhook 콜백 FAILED ("잔액이 부족합니다.") |
| `TIMEOUT_` | webhook 미발송 (PAYMENT_REQUESTED zombie 상태 시뮬레이션) |

환불 시나리오는 `pgTransactionId`에 포함된 마커로 분기한다.

| pgTransactionId 포함 마커 | 동작 |
|---------------------------|------|
| (없음) | webhook 콜백 REFUNDED |
| `REFUND_FAIL_` | webhook 콜백 REFUND_FAILED |
| `REFUND_TIMEOUT_` | webhook 미발송 (환불 webhook 유실 시뮬레이션) |

이 마커 방식은 **기능 검증(결정론적 시나리오 재현)** 전용이다. 부하 테스트에서 PG 변동성을 시뮬레이션하려면 별도 확률적 장애 주입 모드가 필요하다(백로그).

---

## Mock PG 거래조회 (2026-07 추가)

webhook은 신뢰할 수 없는 채널이라 유실될 수 있다. 유실 시 "PG가 실제로 어떻게 처리했는지"를 알 방법이 없으면 복구 배치가 무조건 재시도에 의존해야 한다.

Mock PG에 자체 거래 기록(`mock_pg_transactions` 테이블)을 추가하고 `PaymentGateway.inquireTransaction()`으로 조회할 수 있게 했다.

**핵심 설계 포인트: webhook 발송 여부와 거래 기록 저장을 분리.**

`TIMEOUT_` 마커일 때 기존 코드는 그냥 `return`으로 끝났다. 이러면 거래조회를 도입해도 "PG가 진짜 처리했는지"를 알 수 없다. 거래 기록을 먼저 저장하고, webhook 발송만 생략하는 방식으로 바꿨다.

```java
// PG는 결과와 무관하게 자신의 거래 기록을 먼저 저장한다(진짜 상태).
mockPgTransactionRepository.save(transaction);

if (idempotencyKey.startsWith(TIMEOUT_PREFIX)) {
    return pgTransactionId; // webhook만 안 보냄, 거래 기록은 이미 저장됨
}
```

이렇게 하면 TIMEOUT 시나리오가 "webhook 유실 + PG 처리는 성공"을 정확히 재현한다.

---

## 트레이드오프

- 실제 PG의 네트워크 지연, 타임아웃, 부분 실패 등 엣지 케이스는 Mock으로 완전히 재현할 수 없다.
- 실제 PG 연동 시 `mock/` 패키지를 건드리지 않고 `PaymentGateway` 새 구현체만 추가하면 된다.
- 실제 PG사마다 상태 코드 체계가 다르므로 `PgTransactionResult` enum은 교체가 필요할 수 있다.