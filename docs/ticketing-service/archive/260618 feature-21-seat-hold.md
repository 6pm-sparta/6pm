# feature/21-seat-hold 변경 내역 (2026-06-18)

## 커밋 로그
- `feat: 좌석 선점 API 및 주문 생성 연동 구현`
- `docs: order service 설계 미확정 주의 문구 및 Notion 링크 추가`

## 변경 파일

| 분류 | 파일 |
|------|------|
| ticketing 도메인 | `ShowSeat.java`, `ShowSeatRepository.java`, `SeatController.java`, `SeatService.java`, `HoldResponse.java`, `ShowSeatResponse.java` |
| ticketing 공통 | `TicketingServiceApplication.java`, `TicketingErrorCode.java`, `build.gradle` |
| order 연동 | `OrderClient.java`, `CreateOrderRequest.java`, `CreateOrderResponse.java` |
| order 예시 코드 | `exOrder.java`, `exOrderStatus.java`, `exOrderRepository.java`, `exCreateOrderRequest.java`, `exCreateOrderResponse.java`, `exOrderService.java`, `exInternalOrderController.java` |
| 테스트 | `SeatServiceTest.java` |
| 문서 | `ticketing-system.md` |

## 주요 내용
- Redis Lua Script로 좌석 선점 원자적 처리 (NX + EX 600)
- 선점 성공 시 order-service Feign 동기 호출로 주문 생성
- 주문 생성 실패 시 Redis 선점 즉시 롤백
- 구매 한도(인당 2석) 및 재고 관리 Redis로 처리
- order-service 예시 코드(`ex` 접두사) 포함 → 이후 제거 필요
