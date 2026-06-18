# Ticketing Service CHANGELOG

설계 결정 이력을 기록합니다. 다른 서비스에 영향을 주는 항목은 `[공유 필요]`로 표시합니다.

---

## 2026-06-18

### [공유 필요] Kafka 토픽명 변경: `ticketing.booking.completed` → `ticketing.seat.booked`
- **대상**: order-service, notification-service 담당자
- **이유**: order/payment 설계 문서와 토픽명 정렬. 기존 명칭은 예매 완료에 초점이 맞춰져 있어 좌석 확정 시점과 의미가 모호했음.

### [공유 필요] Kafka 토픽 신규 추가: `ticketing.seat.book.failed`
- **대상**: order-service 담당자
- **이유**: 좌석 예매 실패 시 SAGA 보상 트랜잭션 트리거 용도. order-service가 구독하여 COMPENSATING 상태로 전환 후 환불 처리.

### [공유 필요] Kafka 토픽 제거: `ticketing.seat.held`
- **대상**: order-service 담당자
- **이유**: 구간 2(좌석 선점 → 주문 생성)를 Feign 동기 호출로 구현하면서 불필요해짐. orderId 즉시 응답이 필요한 동기 흐름에는 Feign이 적합.

### [공유 필요] notification Consumer 이관
- **대상**: notification-service 담당자
- **이유**: 예매완료 push는 좌석 확정 시점이 아니라 주문 최종 확정 시점에 발송해야 함. `ticketing.seat.booked` 대신 order-service가 발행하는 `order.confirmed` 구독으로 이관.

### Orders 상태 `PENDING_ORDER` → `PENDING`
- **이유**: order/payment 설계 문서와 상태명 정렬.

### Orders 상태 추가: `PAID`, `COMPENSATING`, `REFUND_REQUESTED`, `FAILED`
- **이유**: order/payment 설계 문서 기준으로 상태 머신 확장. `PAID`는 PG 승인 완료 ~ 좌석 예매 확정 사이 중간 상태.

### `PAYMENT_REQUESTED` 상태 확정
- **이유**: 코레오그래피 SAGA 구조상 PG 콜백 수신 ~ ticketing 좌석 확정 사이 중간 상태가 필요. 멱등성·장애 복구 관점에서 유지.

### 주문 생성 실패 시 Redis 선점 해제 추가
- **이유**: 선점 성공 후 order-service Feign 호출 실패 시 좌석이 HOLDING 상태로 600초 동안 묶이는 문제 방지. 실패 시 Redis DEL로 즉시 AVAILABLE 복원.

### API 경로 변경
- `/queue/enter` → `/queue/shows/{showId}/enter`
- `/queue/status` → `/queue/shows/{showId}/status`
- `/queue/stream` → `/queue/shows/{showId}/stream`
- **이유**: showId 없이는 어느 공연 대기열인지 식별 불가.

### Redis 백업 전략 확정: RDB + PostgreSQL 재적재
- **이유**: AOF는 모든 쓰기에 I/O 비용이 발생. BOOKED 상태의 원본은 PostgreSQL Orders 테이블에 있으므로, 장애 시 CONFIRMED 주문 기준으로 Redis 재적재하는 방식으로 단순화.

### PostgreSQL 17 채택
- **이유**: EOL이 2029년 11월로 15보다 2년 더 길고, SKIP LOCKED 성능 개선으로 배치 처리에 유리. 신규 프로젝트 기준 15를 선택할 이유 없음.
