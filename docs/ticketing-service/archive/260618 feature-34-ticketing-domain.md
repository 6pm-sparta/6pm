# feature/34-ticketing-domain 변경 내역 (2026-06-18)

## 커밋 로그
- `chore: Redis, Kafka 의존성 및 설정 추가`
- `docs: 티켓팅 시스템 설계 문서 작성`
- `feat: 대기열 진입 API 및 SSE 구현`
- `feat: 티켓팅 도메인 엔티티 구현`
- `docs: 엔티티 생성 패턴 결정 문서화 및 Show 시간 검증 추가`

## 변경 파일

| 분류 | 파일 |
|------|------|
| 도메인 엔티티 | `Performance.java`, `Show.java`, `ShowSeat.java`, `Venue.java`, `VenueSeat.java` |
| 대기열 | `QueueController.java`, `QueueService.java`, `QueueSseService.java`, `QueueScheduler.java`, `QueueStatusResponse.java` |
| 설정 | `build.gradle`, `application.yml`, `RedisConfig.java` |
| 문서 | `ticketing-system.md`, `CHANGELOG.md`, `260618 entity rules.md` |

## 주요 내용
- 초기 도메인 엔티티 설계 (`domain/` 패키지) — 객체 연관관계 방식
  - `feature/21` 구현체(`seat/domain/entity/`)와 중복 → 미사용 상태
- 대기열 API 및 SSE 구현 (feature/20과 동일 내용)
- 엔티티 생성 패턴 빌더 채택 결정 및 `Show.endAt > startAt` 검증 추가
