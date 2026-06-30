# Order/Payment Service — Flows

전체 시나리오별 흐름을 정리한다. 상태 전이 규칙은 [architecture.md](./architecture.md)를 참고.

---

## 시나리오 목록

1. [주문 완료 (해피패스)](#1-주문-완료-해피패스)
2. [결제 전 취소](#2-결제-전-취소)
3. [결제 후 취소 (PAID)](#3-결제-후-취소-paid)
4. [예매 확정 후 취소 (CONFIRMED)](#4-예매-확정-후-취소-confirmed)
5. [결제 실패](#5-결제-실패)
6. [SAGA 보상 트랜잭션](#6-saga-보상-트랜잭션)
7. [주문 타임아웃 자동 취소](#7-주문-타임아웃-자동-취소)

---

## 1. 주문 완료 (해피패스)

```
주문 생성 (Ticketing Feign)
→ PG 결제 요청 (응답: PAYMENT_REQUESTED)
→ [비동기] 웹훅 승인 수신
→ order.payment.completed 발행
→ [Ticketing] 좌석 확정 처리
→ ticketing.seat.booked 수신
→ 주문 CONFIRMED
→ notification.send 발행 (ORDER_COMPLETED)
```

```mermaid
sequenceDiagram
    participant T as ticketing-service
    participant C as Client
    participant O as order-service
    participant PG as MockPG
    participant K as Kafka
    participant N as notification-service

    T->>O: POST /internal/v1/orders (holdId, seatId, userId, totalAmount)
    O-->>T: 201 PENDING

    C->>O: POST /api/v1/payments (Idempotency-Key)
    O->>O: PENDING → PAYMENT_REQUESTED
    O->>PG: 결제 요청 접수 (PG 트랜잭션 ID 수령)
    O-->>C: 201 PAYMENT_REQUESTED

    PG->>O: POST /api/v1/webhooks/payments (APPROVED)
    O->>O: PAYMENT_REQUESTED → PAID
    O->>K: order.payment.completed

    K->>T: 좌석 확정 처리
    T->>K: ticketing.seat.booked

    K->>O: ticketing.seat.booked 수신
    O->>O: PAID → CONFIRMED
    O->>K: notification.send (ORDER_COMPLETED)
    K->>N: 예매 확정 알림 발송
```

---

## 2. 결제 전 취소

취소 가능 상태: `PENDING`

```
주문 취소 요청
→ 주문 CANCELLED
→ order.hold.released 발행 (좌석 선점 해제)
→ 응답: 200 CANCELLED
```

> 결제 전 취소 시 알림 발송 여부는 미결정. 현재 미구현.

```mermaid
sequenceDiagram
    participant C as Client
    participant O as order-service
    participant K as Kafka
    participant T as ticketing-service

    C->>O: DELETE /api/v1/orders/{id}
    O->>O: PENDING → CANCELLED
    O->>K: order.hold.released (좌석 선점 해제)
    K->>T: 좌석 해제 처리
    O-->>C: 200 CANCELLED
```

---

## 3. 결제 후 취소 (PAID)

취소 가능 상태: `PAID`

```
주문 취소 요청
→ 주문 REFUND_REQUESTED
→ PG 환불 요청 접수
→ 응답: 200 REFUND_REQUESTED
→ [비동기] 웹훅 환불 완료 수신
→ 주문 REFUNDED
→ order.payment.cancelled 발행 (좌석 해제)
→ notification.send 발행 (ORDER_CANCELED)
```

```mermaid
sequenceDiagram
    participant C as Client
    participant O as order-service
    participant PG as MockPG
    participant K as Kafka
    participant T as ticketing-service
    participant N as notification-service

    C->>O: DELETE /api/v1/orders/{id}
    O->>O: PAID → REFUND_REQUESTED
    O->>PG: 환불 요청 접수
    O-->>C: 200 REFUND_REQUESTED

    PG->>O: POST /api/v1/webhooks/payments (REFUNDED)
    O->>O: REFUND_REQUESTED → REFUNDED
    O->>K: order.payment.cancelled (좌석 해제)
    O->>K: notification.send (ORDER_CANCELED)
    K->>T: 좌석 해제 처리
    K->>N: 취소 알림 발송
```

---

## 4. 예매 확정 후 취소 (CONFIRMED)

취소 가능 상태: `CONFIRMED` (취소 가능 시간 내)

흐름은 [결제 후 취소](#3-결제-후-취소-paid)와 동일. 취소 가능 시간 초과 시 409 `CANCELLATION_WINDOW_EXPIRED`.

> 취소 가능 시간 기준 (공연 시작 시각 vs 확정 시각) 미확정. Ticketing 팀 확인 필요.

```
주문 취소 요청
→ 취소 가능 시간 검증
→ 주문 REFUND_REQUESTED
→ PG 환불 요청 접수
→ 응답: 200 REFUND_REQUESTED
→ [비동기] 웹훅 환불 완료 수신
→ 주문 REFUNDED
→ order.payment.cancelled 발행 (좌석 해제)
→ notification.send 발행 (ORDER_CANCELED)
```

---

## 5. 결제 실패

```
[비동기] 웹훅 실패 수신
→ 주문 FAILED
→ order.payment.failed 발행 (좌석 해제)
```

```mermaid
sequenceDiagram
    participant PG as MockPG
    participant O as order-service
    participant K as Kafka
    participant T as ticketing-service

    PG->>O: POST /api/v1/webhooks/payments (FAILED)
    O->>O: PAYMENT_REQUESTED → FAILED
    O->>K: order.payment.failed (좌석 해제)
    K->>T: 좌석 해제 처리
```

> 결제 실패 후 재시도 정책 미구현. 재시도 허용 시 FAILED 대신 PENDING 유지 필요.

---

## 6. SAGA 보상 트랜잭션

좌석 예매 실패 시 결제를 자동 환불하는 보상 흐름.

```
ticketing.seat.book.failed 수신
→ 주문 COMPENSATING
→ PG 환불 요청 접수
→ 주문 REFUND_REQUESTED
→ [비동기] 웹훅 환불 완료 수신
→ 주문 REFUNDED
→ order.payment.cancelled 발행 (좌석 해제)
→ notification.send 발행 (ORDER_CANCELED)
```

```mermaid
sequenceDiagram
    participant K as Kafka
    participant T as ticketing-service
    participant O as order-service
    participant PG as MockPG
    participant N as notification-service

    T->>K: ticketing.seat.book.failed
    K->>O: 수신
    O->>O: PAID → COMPENSATING → REFUND_REQUESTED
    O->>PG: 환불 요청 접수

    PG->>O: POST /api/v1/webhooks/payments (REFUNDED)
    O->>O: REFUND_REQUESTED → REFUNDED
    O->>K: order.payment.cancelled (좌석 해제)
    O->>K: notification.send (ORDER_CANCELED)
    K->>T: 좌석 해제 처리
    K->>N: 취소 알림 발송
```

**보상 실패 케이스**

PG 환불 요청 접수 자체가 실패(네트워크 오류 등)하면 로그만 남기고 REFUND_REQUESTED 상태에서 멈춘다. 복구는 별도 배치 스코프(현재 미구현).

환불 웹훅으로 `REFUND_FAILED`가 수신되면 FAILED로 전이하고 수동 처리 대상으로 남긴다.

---

## 7. 주문 타임아웃 자동 취소

`expired_at` 기준 폴링 스케줄러(`OrderTimeoutScheduler`)가 PENDING 주문을 자동 취소한다. 후보 조회는 락 없이 ID만 가져오고, 실제 전이는 건당 트랜잭션(`OrderTimeoutWriter`)에서 비관적 락 + 상태 재검증 후 수행한다. 유저 직접 취소와 동시에 경합하면, 락을 먼저 획득한 쪽만 처리되고 나머지는 재검증에서 상태 불일치를 확인해 스킵(스케줄러)되거나 409(유저 요청)로 거부된다.

```
스케줄러 폴링 (expired_at < now, status = PENDING)
→ 건당 비관적 락 획득 + 상태 재검증
→ 여전히 PENDING이면: CANCELLED 전이 + order.hold.released 발행
→ 이미 다른 상태로 바뀌었으면: 스킵 (정상 경합, 에러 아님)
```

```mermaid
sequenceDiagram
    participant S as OrderTimeoutScheduler
    participant O as order-service (OrderTimeoutWriter)
    participant K as Kafka
    participant T as ticketing-service

    loop poll-interval-ms 마다
        S->>O: expired_at 지난 PENDING 주문 ID 조회 (락 없음)
        loop 후보 건마다
            S->>O: expireIfStillPending(orderId)
            O->>O: 비관적 락 + 상태 재검증
            alt 여전히 PENDING
                O->>O: PENDING → CANCELLED
                O->>K: order.hold.released
                K->>T: 좌석 해제 처리
            else 이미 다른 상태로 전이됨
                O-->>S: SKIPPED
            end
        end
    end
```

**PAYMENT_REQUESTED zombie 상태는 이 스케줄러 범위 밖** — PG 웹훅 미수신으로 PAYMENT_REQUESTED에 멈춘 주문은 단순 타임아웃 취소가 위험하다(실제로는 PG가 승인했는데 취소 처리하면 정합성이 깨짐). PG 거래 조회로 실제 승인 여부를 확인하는 별도 복구 로직이 필요하며 현재 미구현이다.