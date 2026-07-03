# 티켓팅 시스템 설계 문서

> **[주의]** 이 문서의 Order Service 관련 설계는 임의로 작성해둔 것이며 확정된 사항이 아닙니다.
> 자세한 내용은 [Order/Payment 서비스 설계 문서](docs/)를 참고해주세요.

## 0. 요약 및 문서 읽는 법

**한 줄 요약:**
> PostgreSQL + Redis + Kafka 기반의 선착순 티켓팅 시스템. 대기열 → 좌석 선점 → 결제 확정의 3구간으로 동작한다.

| 목적 | 이동 |
|---|---|
| 전체 흐름을 빠르게 파악하고 싶다 | → [5. 해피패스 플로우](#5-해피패스-플로우) |
| Redis 키 구조가 궁금하다 | → [3. Redis 키 설계](#3-redis-키-설계) |
| Kafka 토픽 목록이 필요하다 | → [4. Kafka 토픽](#4-kafka-토픽) |
| 미확정 항목을 확인하고 싶다 | → [9. 미확정 항목](#9-미확정-항목) |

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

**패키지 구조 (#222):** 위 ERD 바운더리에 맞춰 `venue`(Venue/VenueSeat), `show`(Performance/Show)를 분리하고, `seat`(ShowSeat + hold/confirm 전체 흐름), `queue`, `order`(infrastructure)는 각각 `presentation/application/domain/infrastructure` 4계층으로 재정리했다.

---

## 3. Redis 키 설계

| 키 | 자료구조 | TTL | 설명 |
|---|---|---|---|
| `waiting_queue:{show_id}` | Sorted Set | - | 대기열. score = 입장 요청 timestamp |
| `purchase-token:{showId}:{userId}` | String | 600초 | 좌석 구매 자격 토큰 (showId별 격리) |
| `show:{show_id}:seat:{show_seat_id}` | String | 600초 | 좌석 상태: `AVAILABLE` / `HOLDING` / `BOOKED` |
| `show:{show_id}:seat:{show_seat_id}:owner` | String | 600초 (seatKey와 동일) | 선점한 `{userId}:{status}`. status는 `PENDING`(주문 생성 중) / `CONFIRMED`(주문 생성 완료) |
| `inventory:{show_id}` | String (Counter) | - | 남은 좌석 수 |
| `purchase-count:{userId}:{showId}` | String (Counter) | - | 사용자별 구매 한도 체크 |

---

## 4. Kafka 토픽

| 토픽 | Producer | Consumer | 용도 |
|---|---|---|---|
| `order.payment.completed` | order-service | ticketing-service | 결제 승인 → 좌석 BOOKED |
| `order.payment.failed` | order-service | ticketing-service | 결제 실패 → 좌석 해제 |
| `order.payment.cancelled` | order-service | ticketing-service | 결제 취소 → 좌석 해제 |
| `ticketing.seat.booked` | ticketing-service | order-service | 좌석 확정 → 주문 CONFIRMED |
| `ticketing.seat.book.failed` | ticketing-service | order-service | 좌석 예매 실패 → SAGA 보상 시작 |

---

## 5. 해피패스 플로우

### 전체 흐름 요약

```mermaid
flowchart TD
    A[대기열 등록]
    B[순번 대기 - SSE]
    C[purchase-token 발급]
    E[좌석 목록 조회]
    F{좌석 Hold 요청 - 토큰 검증}
    G0[HELD - 선점만, 주문 없음]
    G1{체크아웃 요청}
    G[주문 생성 - PENDING → CONFIRMED]
    H[결제 요청]
    I[PAID → CONFIRMED + 좌석 BOOKED]
    J[FAILED + 좌석 해제]

    A --> B --> C --> E
    E --> F
    F -->|토큰 있음 + 선점 성공| G0
    F -->|토큰 없음 403 / 선점 실패 409| E
    G0 --> G1
    G1 -->|본인 소유 + HELD| G --> H
    G1 -->|CONFIRMED| G
    H -->|결제 성공| I
    H -->|결제 실패| J
```

> **변경 (2026-06-23):** 토큰 검증 주체를 Gateway → `SeatService.hold()`로 변경. 좌석 목록 조회는 토큰 없이도 가능하며, 검증은 Hold 시점에 이루어진다. 자세한 배경은 [260623 대기열 토큰.md](./260623%20대기열%20토큰.md), 변경 이력은 [ticketing-system-change-log.md](./ticketing-system-change-log.md) 참고.
>
> **변경 (2026-07-03):** 주문 생성을 `hold()`에서 분리해 별도 체크아웃 API(`POST .../seats/{seatId}/checkout`)로 이전. 가장 트래픽이 몰리는 hold 구간에 동기 cross-service 호출(order-service Feign)이 들어있던 걸 가벼운 Redis 전용 연산으로 줄이기 위함. 자세한 배경은 [SA-260703.md §11](../SA-260703.md) 참고.

---

### 구간 1 — 대기열 → 좌석 선택 화면

```mermaid
sequenceDiagram
    actor Client
    participant Gateway
    participant Queue
    participant Redis
    participant Scheduler

    Client->>Gateway: POST /api/v1/tickets/shows/{showId}/queue
    Gateway->>Queue: 대기열 등록
    Queue->>Redis: ZADD NX (중복 방지)
    Redis-->>Client: 등록 완료

    Client->>Queue: SSE 연결 (GET /api/v1/tickets/shows/{showId}/queue/stream)
    loop 순번 안내
        Scheduler->>Redis: ZRANK 조회
        Redis-->>Scheduler: 현재 순번
        Scheduler-->>Client: 순번(RANK) 이벤트 전송
    end

    Scheduler->>Redis: ZRANGE + ZREM (앞 N명 추출)
    Scheduler->>Redis: SETNX purchase-token:{showId}:{userId} TTL 600s
    Scheduler-->>Client: READY 이벤트 (입장 가능)
    Client->>Gateway: 좌석 목록 조회 (토큰 불필요)
```

> **변경 (2026-06-23):** 이전 버전은 Gateway가 좌석 선택 화면 진입 시점에 토큰을 검증한다고 되어 있었으나, 실제로는 좌석 목록 조회까지는 토큰 없이 가능하고 **좌석 Hold 요청 시점**에 `SeatService`가 직접 검증한다 (구간 2 참고).

---

### 구간 2 — 좌석 선점(hold) + 체크아웃(주문 생성)

**좌석 Hold 시 구매 토큰 검증 (2026-06-23 추가):**
`SeatService.hold()` 진입 시 `purchase-token:{showId}:{userId}` 존재 여부를 가장 먼저 확인한다. 없으면 `PURCHASE_TOKEN_NOT_FOUND`(403)로 즉시 거부하고, 있으면 좌석 선점 Lua 스크립트(`HOLD_SCRIPT`)만 실행하고 끝난다(**2026-07-03부터 주문 생성 없음**). 토큰은 Hold 성공 여부와 무관하게 TTL(600초)로만 만료되며 별도 삭제 로직은 없다.

**체크아웃(주문 생성, 2026-07-03 신설):** `POST .../seats/{seatId}/checkout`이 별도 API로 분리됐다. `SeatService.checkout()`이 owner 상태가 `HELD`인지 확인 후 `CHECKOUT_CLAIM_SCRIPT`로 `PENDING` 전이 → `orderClient.create()` 호출(기존 hold()가 하던 것과 동일) → 성공 시 `CONFIRMED` 전이 + `HoldResponse(orderId)` 반환. 이미 `CONFIRMED`(재요청)면 주문 생성 없이 기존 orderId로 멱등 응답. 주문 생성 실패 시 이 좌석의 선점만 롤백(seatKey/ownerKey 삭제, 재고/구매카운트 복구).

(이하 결제/확정은 order-service에서)

### 구간 3 — 결제 및 예매 확정 (order-service에서)

---

### 좌석 선점 해제 (hold release)

선점은 세 가지 경로로 풀린다.

| 경로 | 트리거 | 처리 |
|---|---|---|
| 결제 실패/취소 | Kafka `order.payment.failed`, `order.payment.cancelled` | `SeatConfirmService.releaseSeat()` — DB `orderId` 해제, Redis seat/owner 키 삭제, 재고 복구 |
| TTL 자연 만료 | Redis keyspace notification (`__keyevent@__:expired`) | `SeatHoldExpirationListener` → `SeatService.releaseExpiredHold()` — DB `orderId` 해제, 재고 복구 (사용자가 직접 해제/결제하지 않고 600초 동안 방치한 경우) |
| 사용자 직접 취소 | `DELETE /api/v1/tickets/shows/{showId}/seats/{seatId}/hold` | `SeatService.releaseHold()` — owner 본인 확인 후 Redis seat/owner 키 삭제, 재고 복구, DB `orderId` 해제 |

**hold ↔ release ↔ checkout 동시 호출 레이스 방지 (2026-07-03 갱신):** owner 키 상태가 `HELD`(선점만, 주문 없음) → `PENDING`(체크아웃 진행 중, `orderClient.create` 외부 네트워크 호출) → `CONFIRMED`(주문 생성 완료) 3단계로 전이한다. `releaseHold()`는 `PENDING`인 동안(체크아웃이 주문 생성을 기다리는 중)만 `SEAT_HOLD_PROCESSING`(409)으로 거부하고, `HELD`나 `CONFIRMED` 상태에서는 해제를 허용한다. 이게 없으면 "Redis는 풀렸는데 DB에는 뒤늦게 orderId가 박히는" 더블부킹 레이스가 생길 수 있다.

> **주문 취소 연동:** `releaseHold()`는 `OrderClient.cancel(orderId, requesterId)`로 order-service 주문도 함께 취소한다. order-service의 `DELETE /internal/v1/orders/{orderId}` 엔드포인트가 2026-07-03 이전엔 실제로 존재하지 않아 항상 404였음(정합성 버그) — 2026-07-03에 수정 완료. order-service 쪽 타임아웃 자동취소(#231)는 `order.hold.released` 이벤트를 발행하고, ticketing `PaymentEventConsumer.onHoldReleased()`가 이를 구독해 `SeatConfirmService.releaseSeat()`로 좌석을 해제한다(멱등 처리).

---

## 6. API 명세

### ticketing-service

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/tickets/shows/{showId}/queue` | 대기열 등록 |
| GET | `/api/v1/tickets/shows/{showId}/queue/status` | 현재 순번 조회 |
| GET | `/api/v1/tickets/shows/{showId}/queue/stream` | SSE 연결 — 순번 실시간 수신 |
| GET | `/api/v1/tickets/shows/{showId}/seats` | 회차별 좌석 목록 + 상태 조회 |
| POST | `/api/v1/tickets/shows/{showId}/seats/{seatId}/hold` | 좌석 선점만(주문 없음, HELD 상태). 응답 바디 없음 |
| POST | `/api/v1/tickets/shows/{showId}/seats/{seatId}/checkout` | 체크아웃 — 주문 생성(2026-07-03 신설). HELD 상태에서만 가능, 이미 CONFIRMED면 멱등 응답 |
| DELETE | `/api/v1/tickets/shows/{showId}/seats/{seatId}/hold` | 좌석 선점 해제 (본인 선점만 가능) |

---

## 7. Rate Limit 기준 (15,000석 공연 기준)

| 항목 | 수치 |
|---|---|
| 좌석 hold API | 초당 50~60건 |
| 대기열 입장 배치 | 분당 3,000~3,600명 |
| purchase-token TTL | 600초 |
| 결제 성공률 (외부 PG 기준) | 40~50% |

---

## 8. 구현 일정

[github projects - 6pm 일정 관리](https://github.com/orgs/6pm-sparta/projects/2) 

---

## 9. 미확정 항목

| 항목 | 현황 | 결정 필요 사항 |
|---|---|---|
| `holdId` | 미확정 | 별도 `SeatHolds` 테이블로 분리할지, `Orders.id`를 그대로 holdId로 사용할지 |
| 스케줄러 분산 락 | 미구현 | `QueueScheduler`가 멀티 인스턴스로 떠 있을 때 같은 배치를 중복 처리할 수 있음. ShedLock 등 분산 락 적용 필요 |
| `GET /purchase-limit` 엔드포인트 | 미문서화 | `SeatController.java:54-60`에 구현되어 있으나 섹션 6 API 명세에 누락. 코드에 `// TODO: api 엔드포인트 설계 괜찮은지 검토 필요` 주석 있어 설계 자체도 미확정 |
| 구매 한도 값(`MAX_PER_USER`) | 미문서화 | 섹션 3 Redis 키 설계에 `purchase-count` 키는 있지만 실제 한도 값(현재 코드상 4)이 어디에도 명시돼 있지 않음. 2→4 변경 사실도 문서에 반영 안 됨 |
| SSE `ENTERED` 이벤트 (문서/코드 불일치) | 미구현 | "260623 대기열 토큰.md"는 토큰 발급 시 SSE로 `ENTERED` 이벤트를 전송한다고 적혀 있으나, 실제 `QueueSseService.java:58,61`에서는 `READY`/`RANK` 이벤트만 전송하고 `ENTERED`는 코드 어디에도 없음 |
| CHANGELOG.md API 경로 변경 이력 | 수정 필요 | `/queue/enter` → `/queue/shows/{showId}/enter` 기록 이후 실제로는 `/api/v1/tickets/shows/{showId}/queue`로 한 번 더 변경됐는데 반영 안 됨. 최신 경로 변경 이력 추가 필요 |
