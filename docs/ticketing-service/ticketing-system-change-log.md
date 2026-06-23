# ticketing-system.md 변경 이력

## 2026-06-23 — 구매 토큰 검증 주체 변경 (Gateway → SeatService)

- 관련 작업 문서: [260623 대기열 토큰.md](./260623%20대기열%20토큰.md)
- 브랜치: `feature/57-purchase-token`

### 변경 내용

| 항목 | Before | After |
|---|---|---|
| Redis 키 | `purchase-token:{userId}` | `purchase-token:{showId}:{userId}` (showId별 격리) |
| 토큰 검증 주체 | Gateway가 좌석 선택 화면 진입 시점에 검증 | `SeatService.hold()`가 좌석 Hold 요청 시점에 직접 검증 |
| 좌석 목록 조회 | 토큰 검증 통과 후에만 가능하다고 기술됨 | 토큰 없이도 조회 가능, Hold 시점에만 검증 |
| 토큰 미존재 시 응답 | 명시 안 됨 | `PURCHASE_TOKEN_NOT_FOUND` (403) |

### 변경 이유
- 실제 구현 시점에 Gateway에는 `purchase-token` 관련 코드가 전혀 없었음 (설계 문서만 존재, 구현 안 됨)
- 한 사용자가 여러 공연의 대기열에 동시에 있을 수 있어, 키에 `showId`를 포함하지 않으면 공연 A에서 받은 토큰으로 공연 B 구매가 통과되는 문제가 있었음
- 검증이 필요한 지점이 현재는 좌석 Hold 하나뿐이라, Gateway 레벨 필터보다 `SeatService` 내부에 직접 두는 쪽이 더 단순함 (API가 늘어나면 추후 AOP/인터셉터로 추출 검토)

### 변경 파일
- `ticketing-service/src/main/java/com/fandom/ticketing_service/queue/service/PurchaseTokenService.java` (신규)
- `ticketing-service/src/main/java/com/fandom/ticketing_service/queue/service/QueueScheduler.java`
- `ticketing-service/src/main/java/com/fandom/ticketing_service/seat/service/SeatService.java`
- `ticketing-service/src/main/java/com/fandom/ticketing_service/common/exception/TicketingErrorCode.java`
- `docs/ticketing-service/ticketing-system.md`

### 남은 미결 사항
- 스케줄러 멀티 인스턴스 환경에서의 배치 중복 처리 가능성 → 분산 락(ShedLock 등) 필요 여부 검토 필요
- TTL 만료 후 재진입 정책 (재발급 vs 대기열 맨 뒤로) 미정

---

## 2026-06-23 (추가) — `ACTIVE_SHOW_IDS` 하드코딩 제거

### 변경 내용
- `QueueScheduler.ACTIVE_SHOW_IDS`(빈 `Set.of()` 하드코딩, TODO 주석)를 제거
- 대신 `redisTemplate.keys("waiting_queue:*")`로 실제 존재하는 대기열 키를 스캔해 활성 공연 ID를 동적으로 추출하는 `findActiveShowIds()` 추가

### 변경 이유
- 기존 코드는 `ACTIVE_SHOW_IDS`가 항상 빈 Set이라 `@Scheduled` 잡이 60초마다 실행은 되지만 루프 본문이 절대 동작하지 않음 → 대기열에 진입한 사용자가 영원히 토큰을 못 받는 치명적 문제였음
- `waiting_queue:{showId}` 키는 `QueueService.enter()` 시점에 생성되고, 사용자가 모두 빠지면 ZSET이 비워지며 자연히 사라지므로, 별도 "활성 공연" 테이블/캐시 없이도 Redis 키 존재 여부만으로 처리 대상을 판단할 수 있음

### 변경 파일
- `ticketing-service/src/main/java/com/fandom/ticketing_service/queue/service/QueueScheduler.java`
- `ticketing-service/src/test/java/com/fandom/ticketing_service/queue/service/QueueSchedulerTest.java`
- `docs/ticketing-service/ticketing-system.md` (9. 미확정 항목에 분산 락 항목 추가)

### 남은 제약
- `redisTemplate.keys()`는 Redis `KEYS` 명령(O(N), 전체 키스페이스 스캔)을 사용함. 현재 단일 노드 Redis + 동시 활성 공연 수가 많지 않은 규모라 허용했으나, 키 스페이스가 커지면 `SCAN` 기반으로 전환 검토 필요
