# ADR 005 — Writer 빈 분리 (트랜잭션 경계)

**날짜**: 2026-06  
**상태**: 확정

---

## 배경

서비스 레이어에서 `@Transactional` 메서드를 같은 클래스 내 다른 메서드에서 호출하면 트랜잭션이 적용되지 않는다. Spring의 `@Transactional`은 AOP 프록시 기반인데, 같은 빈 내부 호출(self-invocation)은 프록시를 거치지 않고 직접 호출되기 때문이다.

결제 요청 흐름에서 트랜잭션 경계를 명확히 나눠야 하는 케이스가 있었다.

```
1. payment INSERT + order.status → PAYMENT_REQUESTED (짧은 트랜잭션, 커밋 후 분산락 해제)
2. PG API 호출 (트랜잭션 밖)
3. pg_transaction_id DB 저장 (별도 트랜잭션)
```

1번과 3번을 같은 클래스에 두고 self-invocation으로 호출하면 트랜잭션 경계가 생기지 않는다.

---

## 결정: Writer를 별도 빈으로 분리

`PaymentRequestService` (Service 레이어) → `PaymentRequestWriter` (별도 빈) 구조.

모든 도메인에 동일하게 적용한다.

```
OrderCreationService      → OrderCreationWriter
PaymentRequestService     → PaymentRequestWriter
OrderCancelService        → OrderCancelWriter
OrderConfirmationService  → OrderConfirmationWriter
OrderCompensationService  → OrderCompensationWriter
```

---

## 이유

Writer를 별도 빈으로 분리하면 Service가 Writer를 주입받아 호출하는 구조가 된다. 이 경우 Spring 프록시를 통해 호출되므로 `@Transactional`이 정상 동작한다.

추가로 트랜잭션 경계가 코드 구조에서 명시적으로 드러난다. Writer 클래스 = 트랜잭션 단위라는 규칙이 생기므로, 어떤 작업이 같은 트랜잭션에 묶이는지 파악하기 쉽다.

---

## 트레이드오프

- 클래스 수가 도메인당 2개씩 늘어난다.
- Service는 비즈니스 흐름 제어, Writer는 DB 쓰기 책임으로 역할이 명확히 분리된다.
