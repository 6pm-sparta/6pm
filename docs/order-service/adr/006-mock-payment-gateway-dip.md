# ADR 006 — MockPaymentGateway DIP 구조

**날짜**: 2026-06  
**상태**: 확정

---

## 배경

실제 PG 없이 개발·테스트 환경에서 결제 흐름 전체를 검증해야 한다. PG 연동 방식을 어떻게 구성할지 검토했다.

---

## 결정: PaymentGateway 인터페이스 + MockPaymentGateway 구현체 (DIP)

---

## 이유

PG 연동 코드를 서비스 레이어에 직접 박으면 테스트에서 실제 PG API를 호출하거나, 테스트마다 PG 응답을 외부에서 주입하기 어렵다.

`PaymentGateway` 인터페이스를 두고 `MockPaymentGateway`가 이를 구현하는 구조를 채택했다. 서비스 레이어는 인터페이스에만 의존하므로 실제 PG 구현체로 교체할 때 서비스 코드를 건드리지 않아도 된다.

---

## MockPaymentGateway 시나리오 트리거

실제 PG 없이 결정론적으로 시나리오를 재현한다. `Idempotency-Key` 헤더 접두사로 분기한다.

| Idempotency-Key 접두사 | 동작 |
|------------------------|------|
| (없음, 일반) | 웹훅 콜백 APPROVED |
| `FAIL_` | 웹훅 콜백 FAILED ("잔액이 부족합니다.") |
| `TIMEOUT_` | 웹훅 미발송 (PAYMENT_REQUESTED zombie 상태 시뮬레이션) |

환불은 PG 트랜잭션 ID가 있으면 항상 성공 처리 (Mock 단순화).  
웹훅 콜백 지연: 기본 1500ms (비동기 흐름 시뮬레이션).

---

## 트레이드오프

- 실제 PG의 네트워크 지연, 타임아웃, 부분 실패 등 엣지 케이스는 Mock으로 완전히 재현할 수 없다.
- 실제 PG 연동 시 `PaymentGateway` 구현체만 교체하면 되므로 서비스 레이어 변경은 없다.
