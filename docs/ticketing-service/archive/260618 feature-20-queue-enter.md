# feature/20-queue-enter 변경 내역 (2026-06-18)

## 커밋 로그
- `feat: 대기열 진입 API 및 SSE 구현`
- `chore: Redis, Kafka 의존성 및 설정 추가`
- `docs: 티켓팅 시스템 설계 문서 작성`
- `docs: order service 설계 미확정 주의 문구 및 Notion 링크 추가`
- `feat: 좌석 선점 API 및 주문 생성 연동 구현` ← Revert됨
- `Revert "feat: 좌석 선점 API 및 주문 생성 연동 구현"`

## 변경 파일

| 분류 | 파일 |
|------|------|
| 설정 | `ticketing-service/build.gradle`, `application.yml`, `RedisConfig.java` |
| 대기열 | `QueueController.java`, `QueueService.java`, `QueueSseService.java`, `QueueScheduler.java`, `QueueStatusResponse.java` |
| 문서 | `docs/ticketing-service/ticketing-system.md`, `CHANGELOG.md` |

## 주요 내용
- Redis Sorted Set 기반 대기열 진입/순번 조회 구현
- SSE(Server-Sent Events)로 실시간 순번 브로드캐스트
- 대기열 토큰 TTL 600초 설정
- 좌석 선점 API는 이 브랜치에서 Revert → feature/21로 이관
