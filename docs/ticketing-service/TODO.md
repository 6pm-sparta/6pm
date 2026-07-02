# TODO

## 2026-06-18

### 즉시 처리 필요

- [x] `domain/` 패키지 미사용 엔티티 삭제 (#225 패키지 구조 정리로 해소, 2026-06-30 확인)
  - `Performance.java`, `Show.java`, `Venue.java`, `VenueSeat.java`는 `show/`, `venue/` 도메인 패키지로 이동
  - 중복돼 있던 `ShowSeat.java`는 `seat/domain/entity/`로 단일화

### 기능 미구현

- [x] `QueueScheduler.ACTIVE_SHOW_IDS` 동적 조회 구현 (완료 2026-06-23)
  - `redisTemplate.keys("waiting_queue:*")`로 실제 대기열이 존재하는 공연만 동적으로 조회하도록 변경

- [x] 좌석 선점 해제 API (`DELETE /api/v1/tickets/shows/{showId}/seats/{seatId}/hold`) (#56, 완료 2026-06-19)
  - TTL 자연 만료 자동 해제(`SeatHoldExpirationListener`)도 함께 구현
  - hold/release 동시 호출 레이스는 owner 키 `PENDING`/`CONFIRMED` 상태로 차단

- [x] 구매 토큰 발급 및 검증 (#57, 완료 2026-06-23)
  - `PurchaseTokenService` 추가, `purchase-token:{showId}:{userId}` 키로 발급/검증
  - `SeatService.hold()` 진입 시 토큰 존재 여부 검증 (`PURCHASE_TOKEN_NOT_FOUND`, 403)

### 버그

- [x] `SeatConfirmService.confirmSeat()` 좌석 조회 실패 시 `seatId = null`로 `SeatBookFailedEvent` 발행 (#58, 완료 2026-06-19)

### 코드 품질

- [ ] `SeatService.hold()` switch default 케이스 → `SEAT_ALREADY_HELD` 대신 `INTERNAL_SERVER_ERROR`로 변경

---

## 2026-06-19

### 기능 미구현

- [x] order-service 주문 취소 연동 (#155, #231, 완료 2026-07-01 확인)
  - ticketing→order: `releaseHold()`가 `OrderClient.cancel(orderId)` 호출 (`SeatService.java`)
  - order→ticketing: order-service 타임아웃 자동취소(#231)가 `order.hold.released` 발행, ticketing `PaymentEventConsumer.onHoldReleased()`가 구독해 `SeatConfirmService.releaseSeat()` 호출

### 알려진 제약 (낮은 우선순위)

- [ ] `SeatService.hold()`에서 `assignOrder` 저장과 owner 키 `CONFIRMED` 갱신 사이의 트랜잭션 커밋 지연 윈도우
  - `@Transactional` 커밋 전에 owner가 `CONFIRMED`로 바뀌어, 그 틈에 release가 들어오면 이론상 레이스가 재현될 수 있음
  - 네트워크 호출 구간(수백ms)을 막은 것에 비해 윈도우가 매우 작아(로컬 DB 커밋 지연 수준) 우선순위 낮음

---

## 2026-06-23

### 알려진 제약 (낮은 우선순위)

- [ ] `QueueScheduler.findActiveShowIds()`가 `KEYS` 명령(O(N) 전체 스캔) 사용
  - Redis 싱글 스레드 특성상 키 개수가 늘어나면 다른 명령(좌석 Hold 등)을 블로킹시켜 선착순 공정성에 영향
  - 현재 동시 활성 공연 수가 적어 우선순위 낮음. 키 스페이스가 커지면 `SCAN` 커서 기반으로 전환 필요

- [ ] `QueueScheduler`에 분산 락 없음
  - 멀티 인스턴스 환경에서 같은 배치를 중복 처리할 수 있음
  - `issue()`(SETNX)/`ZREM`이 멱등이라 데이터 정합성은 깨지지 않고 로그 중복/SSE 중복 호출 정도의 비효율만 발생 → 우선순위 낮음
