# feature/22-seat-confirm 변경 내역 (2026-06-18)

## 커밋 로그
- `feat: 좌석 확정 및 Kafka SAGA 구현 (#22)`
- `feat: 좌석 선점 API 및 주문 생성 연동 구현` (feature/21 내용 포함)
- `docs: order service 설계 미확정 주의 문구 및 Notion 링크 추가`

## 변경 파일

| 분류 | 파일 |
|------|------|
| Kafka Consumer | `KafkaConsumerConfig.java`, `PaymentEventConsumer.java` |
| Kafka Event | `PaymentCompletedEvent.java`, `PaymentFailedEvent.java`, `SeatBookedEvent.java`, `SeatBookFailedEvent.java` |
| Kafka Producer | `SeatEventProducer.java` |
| 좌석 확정 | `SeatConfirmService.java` |
| 테스트 | `SeatConfirmServiceTest.java` |
| feature/21 포함 | ShowSeat, SeatService 등 |

## 주요 내용
- 코레오그래피 SAGA 구현
  - `order.payment.completed` → 좌석 확정 → `ticketing.seat.booked` 발행
  - `order.payment.failed` / `order.payment.cancelled` → 좌석 해제 (Redis 복구 + 재고 반환)
  - 확정 실패 시 → `ticketing.seat.book.failed` 발행 (보상 트리거)
- `ShowSeat`에 `status`, `orderId` 컬럼 추가 → 이후 설계 문서와 불일치로 status 제거됨
