# 티켓팅 시스템 설계 문서

> **[주의]** 이 문서의 Order Service 관련 설계는 임의로 작성해둔 것이며 확정된 사항이 아닙니다.
> 자세한 내용은 [Order/Payment 서비스 설계 문서](https://app.notion.com/p/teamsparta/order-payment-3822dc3ef51480608fd7cb333ea3ba34)를 참고해주세요.

## 0. 요약 및 문서 읽는 법

**한 줄 요약:**
> PostgreSQL + Redis + Kafka 기반의 선착순 티켓팅 시스템. 대기열 → 좌석 선점 → 결제 확정의 3구간으로 동작한다.

| 목적 | 이동 |
|---|---|
| 전체 흐름을 빠르게 파악하고 싶다 | → [6. 해피패스 플로우](#6-해피패스-플로우) |
| Redis 키 구조가 궁금하다 | → [3. Redis 키 설계](#3-redis-키-설계) |
| 주문 상태 흐름이 궁금하다 | → [4. Orders 상태 전이](#4-orders-상태-전이) |
| Kafka 토픽 목록이 필요하다 | → [5. Kafka 토픽](#5-kafka-토픽) |
| 미확정 항목을 확인하고 싶다 | → [10. 미확정 항목](#10-미확정-항목) |

---

## 1. 기술 스택

| 역할 | 기술 |
|---|---|
| DB | PostgreSQL 17 |
| Cache / 상태 관리 | Redis (RDB 영속성) |
| 메시지 큐 | Kafka |
| 대기열 실시간 안내 | SSE (Server-Sent Events) |

---

## 2. ERD 핵심 구조

```
Venues
  └─ VenueSeats       (물리 좌석 마스터)

Performances
  └─ Shows            (회차)
       └─ ShowSeats   (회차별 좌석, status 컬럼 없음 — 상태는 Redis 관리)

Orders               (주문 = 예약 + 확정 통합 단일 애그리게이트)
```

> **설계 포인트:** `ShowSeats`에 `status` 컬럼을 두지 않는다. 좌석 상태는 Redis에서만 관리하여 DB 경합을 제거한다.

---

## 3. Redis 키 설계

| 키 | 자료구조 | TTL | 설명 |
|---|---|---|---|
| `waiting_queue:{show_id}` | Sorted Set | - | 대기열. score = 입장 요청 timestamp |
| `purchase-token:{userId}` | String | 600초 | 좌석 선택 화면 진입 권한 토큰 |
| `show:{show_id}:seat:{show_seat_id}` | String | 600초 | 좌석 상태: `AVAILABLE` / `HOLDING` / `BOOKED` |
| `inventory:{show_id}` | String (Counter) | - | 남은 좌석 수 |
| `purchase-count:{userId}:{showId}` | String (Counter) | - | 사용자별 구매 한도 체크 |

---

## 4. Orders 상태 전이

### 상태 정의

| 상태 | 설명 |
|---|---|
| `PENDING` | 주문 생성 완료, 결제 요청 전 |
| `PAYMENT_REQUESTED` | PG 결제 API 호출 완료, 승인 응답 대기 중 |
| `PAID` | PG 결제 승인 완료, 좌석 예매 확정 요청 전 |
| `CONFIRMED` | 좌석 예매 확정까지 완료, 최종 확정 |
| `COMPENSATING` | 좌석 예매 실패로 보상 트랜잭션 진행 중 |
| `REFUND_REQUESTED` | 환불 API 호출 완료, 환불 처리 대기 중 |
| `CANCELLED` | 주문 취소 완료 (결제 전 취소 또는 환불 완료 후) |
| `REFUNDED` | 환불 완료 |
| `FAILED` | 결제 실패 또는 보상 트랜잭션 최종 실패 |

### 상태 전이 규칙

```mermaid
stateDiagram-v2
    [*] --> PENDING : 주문 생성

    PENDING --> PAYMENT_REQUESTED : 결제 요청
    PENDING --> CANCELLED : 유저 직접 취소 (결제 전)
    PENDING --> CANCELLED : 타임아웃 만료

    PAYMENT_REQUESTED --> PAID : 결제 승인
    PAYMENT_REQUESTED --> FAILED : 결제 실패

    PAID --> CONFIRMED : 좌석 예매 확정
    PAID --> COMPENSATING : 좌석 예매 실패
    PAID --> REFUND_REQUESTED : 유저 직접 취소 (결제 후)

    CONFIRMED --> REFUND_REQUESTED : 취소 가능 시간 내 유저 취소

    COMPENSATING --> REFUND_REQUESTED : 환불 요청
    COMPENSATING --> FAILED : 보상 트랜잭션 최종 실패

    REFUND_REQUESTED --> REFUNDED : 환불 완료

    CANCELLED --> [*]
    REFUNDED --> [*]
    FAILED --> [*]
```

---

## 5. Kafka 토픽

| 토픽 | Producer | Consumer | 용도 |
|---|---|---|---|
| `order.payment.completed` | order-service | ticketing-service | 결제 승인 → 좌석 BOOKED |
| `order.payment.failed` | order-service | ticketing-service | 결제 실패 → 좌석 해제 |
| `order.payment.cancelled` | order-service | ticketing-service | 결제 취소 → 좌석 해제 |
| `ticketing.seat.booked` | ticketing-service | order-service | 좌석 확정 → 주문 CONFIRMED |
| `ticketing.seat.book.failed` | ticketing-service | order-service | 좌석 예매 실패 → SAGA 보상 시작 |
| `order.confirmed` | order-service | notification-service | 주문 최종 확정 → 예매완료 push |
| `order.refund.completed` | order-service | notification-service | 환불 완료 알림 발송 |

---

## 6. 해피패스 플로우

### 전체 흐름 요약

```mermaid
flowchart TD
    A[대기열 등록]
    B[순번 대기 - SSE]
    C[purchase-token 발급]
    D{Gateway 토큰 검증}
    E[좌석 목록 조회]
    F{좌석 선점 요청}
    G[주문 생성 - PENDING]
    H[결제 요청]
    I[PAID → CONFIRMED + 좌석 BOOKED]
    J[FAILED + 좌석 해제]

    A --> B --> C --> D
    D -->|통과| E
    E --> F
    F -->|성공| G --> H
    F -->|실패 409| E
    H -->|결제 성공| I
    H -->|결제 실패| J
```

---

### 구간 1 — 대기열 → 좌석 선택 화면

```mermaid
sequenceDiagram
    actor Client
    participant Gateway
    participant Queue
    participant Redis
    participant Scheduler

    Client->>Gateway: POST /queue/shows/{showId}/enter
    Gateway->>Queue: 대기열 등록
    Queue->>Redis: ZADD NX (중복 방지)
    Redis-->>Client: 등록 완료

    Client->>Queue: SSE 연결 (GET /queue/shows/{showId}/stream)
    loop 순번 안내
        Scheduler->>Redis: ZRANK 조회
        Redis-->>Scheduler: 현재 순번
        Scheduler-->>Client: 순번 이벤트 전송
    end

    Scheduler->>Redis: ZRANGE + ZREM (앞 N명 추출)
    Scheduler->>Redis: SETNX purchase-token TTL 600s
    Scheduler-->>Client: 입장 가능 이벤트
    Client->>Gateway: 좌석 선택 화면 진입 요청
    Gateway->>Redis: purchase-token 검증 통과
```

---

### 구간 2 — 좌석 선점 + 주문 생성

```mermaid
sequenceDiagram
    actor Client
    participant Gateway
    participant Ticketing
    participant Redis
    participant Order

    Client->>Gateway: POST /seats/{seatId}/hold
    Gateway->>Gateway: purchase-token 검증
    Gateway->>Ticketing: 선점 요청 전달

    Ticketing->>Redis: Lua SETNX + INCR (원자적)
    alt 선점 실패 - 이미 선점된 좌석
        Redis-->>Ticketing: FAIL
        Ticketing-->>Client: 409 Conflict
    else 선점 성공
        Redis-->>Ticketing: OK
        Ticketing->>Order: 주문 생성 요청 (Feign)
        alt 주문 생성 성공
            Order-->>Ticketing: orderId 반환
            Ticketing-->>Client: 200 holdId + orderId
        else 주문 생성 실패
            Order-->>Ticketing: 5xx 오류 응답
            Ticketing->>Redis: DEL seat key (선점 해제)
            Ticketing-->>Client: 500
        end
    end
```

---

### 구간 3 — 결제 및 예매 확정

```mermaid
sequenceDiagram
    actor Client
    participant Order
    participant PG
    participant Kafka
    participant Ticketing
    participant Notification

    Client->>Order: POST /orders/{orderId}/payment
    Order->>Order: status → PAYMENT_REQUESTED
    Order->>PG: 결제 API 호출

    alt 결제 성공
        PG-->>Order: 성공 응답
        Order->>Order: status → PAID
        Order->>Kafka: order.payment.completed
        Kafka->>Ticketing: 이벤트 수신
        Ticketing->>Ticketing: Redis 좌석 → BOOKED
        Ticketing->>Kafka: ticketing.seat.booked
        Kafka->>Order: 이벤트 수신
        Order->>Order: status → CONFIRMED
        Order->>Kafka: order.confirmed
        Kafka->>Notification: 예매완료 push 발송
        Order-->>Client: 예매 확정
    else 결제 실패
        PG-->>Order: 실패 응답
        Order->>Order: status → FAILED
        Order->>Kafka: order.payment.failed
        Kafka->>Ticketing: 이벤트 수신
        Ticketing->>Ticketing: Redis 좌석 DEL → AVAILABLE
        Order-->>Client: 결제 실패
    end
```

---

## 7. API 명세

### ticketing-service

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/queue/shows/{showId}/enter` | 대기열 등록 |
| GET | `/queue/shows/{showId}/status` | 현재 순번 조회 |
| GET | `/queue/shows/{showId}/stream` | SSE 연결 — 순번 실시간 수신 |
| GET | `/shows/{showId}/seats` | 회차별 좌석 목록 + 상태 조회 |
| POST | `/seats/{seatId}/hold` | 좌석 선점 + 주문 생성 트리거 |

### order-service

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/orders/{orderId}` | 주문 상세 조회 |
| POST | `/orders/{orderId}/payment` | 결제 요청 |
| POST | `/orders/{orderId}/cancel` | 주문 취소 |
| POST | `/orders/{orderId}/payment/callback` | PG 결제 콜백 수신 |

---

## 8. Rate Limit 기준 (15,000석 공연 기준)

| 항목 | 수치 |
|---|---|
| 좌석 hold API | 초당 50~60건 |
| 대기열 입장 배치 | 분당 3,000~3,600명 |
| purchase-token TTL | 600초 |
| 결제 성공률 (외부 PG 기준) | 40~50% |

---

## 9. 구현 일정

| 날짜 | 작업 |
|---|---|
| 6/16 (월) | 설계 확정 + 명세 작성 |
| 6/17 (화) | 대기열 + 토큰 + SSE |
| 6/18 (수) | 좌석 선점 + 주문 생성 |
| 6/19 (목) | 예매 확정 |
| 주말 | TTL 만료 처리, 결제 실패 SAGA, 취소/환불 |

---

## 10. 미확정 항목

| 항목 | 현황 | 결정 필요 사항 |
|---|---|---|
| `holdId` | 미확정 | 별도 `SeatHolds` 테이블로 분리할지, `Orders.id`를 그대로 holdId로 사용할지 |
