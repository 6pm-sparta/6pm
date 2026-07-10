# ADR 012 — Kafka 발행 Outbox 패턴 도입 (초안)

**날짜**: 2026-07-09
**상태**: 초안 (구현 전, 검토 중)

---

## 배경

`SeatEventProducer`는 `KafkaTemplate.send()`를 DB 트랜잭션과 별개로 직접 호출한다([architecture.md §4](../architecture.md#4-kafka-이벤트)). `confirmSeat()`/`releaseSeat()`가 DB/Redis를 이미 갱신한 *뒤에* Kafka 발행이 실패하면(브로커 다운 등) 이벤트가 유실된다:

- `ticketing.seat.booked` 유실 → order-service 주문이 영원히 `PAID`(결제완료, 좌석확정 대기)에 머묾
- `ticketing.seat.book.failed` 유실 → SAGA 보상이 트리거되지 않아 결제된 주문이 고아 상태로 남음

order-service는 이미 이 문제를 Transactional Outbox 패턴으로 풀어놨다(`OrderOutbox` 엔티티 + `OutboxAppender` + `OutboxPublisher`/`OutboxRecordPublisher`). ticketing에도 같은 패턴을 이식할지가 이 ADR의 주제.

---

## 결정(초안)

order-service의 구조를 그대로 이식한다. 새로 설계하지 않고 검증된 패턴을 재사용.

**신규 컴포넌트 (ticketing-service, order-service 대응 클래스 매핑):**

| ticketing-service (신규) | order-service (기존, 참고) | 역할 |
|---|---|---|
| `TicketingOutbox` (`@Entity`) | `OrderOutbox` | `aggregateId`(orderId), `topic`, `payload`(JSON), `status`(PENDING/PUBLISHED/FAILED), `retryCount` |
| `TicketingOutboxRepository` | `OrderOutboxRepository` | `findByStatusOrderByCreatedAtAsc` |
| `OutboxAppender` | `OutboxAppender` | `appendSeatBooked(orderId, seatId)`, `appendSeatBookFailed(orderId, seatId, reason)` — `SeatEventProducer` 대체 |
| `OutboxPublisher` | `OutboxPublisher` | `@Scheduled` 폴링, PENDING 배치 조회 후 레코드별 위임 |
| `OutboxRecordPublisher` | `OutboxRecordPublisher` | 레코드 단위 트랜잭션, `kafkaTemplate.send().get()`로 브로커 ack 동기 대기 후 `markPublished()`, 실패 시 `retryCount` 증가 → `MAX_RETRY_COUNT`(5) 초과 시 `FAILED` |

**호출부 변경:** `SeatConfirmService.confirmSeat()`/향후 releaseSeat() 관련 발행 지점에서 `seatEventProducer.publishSeatBooked(...)` → `outboxAppender.appendSeatBooked(...)`로 교체. 두 메서드 다 이미 `@Transactional`이라 Outbox INSERT가 같은 트랜잭션에 자연스럽게 묶인다.

**DB 마이그레이션:** `ticketing_outbox` 테이블은 Hibernate `ddl-auto`로 자동 생성(order-service `order_outbox`와 동일 방식, 별도 수동 마이그레이션 불필요 — 인덱스는 `status` 컬럼 1개면 충분).

---

## 스코프에서 뺀 것 (초안 단계 미정)

- **수신측(Consumer) 변경 없음** — `PaymentEventConsumer`는 그대로 둔다. Outbox는 발행(Producer) 신뢰성 문제고, 수신측 DLQ는 이미 별도로 존재([architecture.md §4](../architecture.md#4-kafka-이벤트)).
- **`SeatEventProducer` 완전 삭제 여부** — Outbox로 옮기면 `SeatEventProducer.send()`의 직접 발행 경로가 필요 없어지는데, 클래스를 아예 지울지 유지할지는 구현 PR에서 결정.
- **폴링 주기/배치 크기** — order-service 기본값(`poll-interval-ms:2000`, `BATCH_SIZE=100`)을 그대로 가져올지, ticketing 트래픽 특성(대기열 통과 시점에 몰림)에 맞춰 조정할지 미정.
- **releaseHold()/releaseExpiredHold() 쪽 이벤트도 포함할지** — 이쪽은 현재 order-service로 나가는 이벤트가 없음([ADR 011](./011-hold-release-no-order-cancel-call.md) 참고). Outbox 적용 대상은 우선 `SeatEventProducer`가 이미 발행 중인 `ticketing.seat.booked`/`ticketing.seat.book.failed` 2개로 한정.

---

## 트레이드오프

- **장점:** DB 커밋과 이벤트 발행이 원자적으로 묶여 유실 가능성 제거. order-service와 동일 패턴이라 팀 전체 학습비용 낮음.
- **비용:** 폴링 지연만큼 이벤트 발행이 늦어짐(order-service 기준 최대 2초). 새 테이블 하나 추가(운영/모니터링 대상 늘어남). `kafkaTemplate.send().get()` 동기 대기라 브로커 지연 시 폴링 스레드가 그만큼 블로킹됨(order-service도 동일 트레이드오프를 이미 감수 중).

---

## 재검토 조건

지금은 이벤트 유실이 실제로 관측된 적은 없어 초안 단계에 머무른다. 아래 중 하나가 발견되면 "확정"으로 전환하고 구현에 들어간다.

- 운영/부하테스트 중 `ticketing.seat.booked`/`ticketing.seat.book.failed` 발행 실패 로그(`[Kafka 발행 실패]`)가 실제로 찍히는 경우
- order-service 쪽에 `PAID`(좌석확정 대기)에서 안 넘어가는 고아 주문이 발견되는 경우 (유실 의심 정황)
- Kafka 브로커 장애/재시작이 잦아져서 발행 신뢰성이 실제 운영 이슈로 올라오는 경우

## 다음 단계

이 초안에 합의되면: (1) "스코프에서 뺀 것" 항목들 결정 → 상태를 "확정"으로 전환 → (2) 구현 PR.
