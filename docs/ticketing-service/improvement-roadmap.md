# ticketing-service 개선 방향

README를 요약/네비게이션 용도로 유지하기 위해, 실측 검증 전 설계 목표치와 알려진 이슈를 이 문서로 분리했다.

---

## 1. 설계 목표치 (Rate Limit, 15,000석 공연 기준 — 실측 검증 전)

> 아래 수치는 대기열/재고 설계 시 가정한 목표치이며, 15,000석 규모 실측 부하테스트는 아직 진행 전이다. 현재까지 실측으로 확인된 병목은 gateway ~600req/s 포화 지점뿐이다.

| 항목 | 수치 |
|---|---|
| 좌석 hold API | 초당 50~60건 |
| 대기열 입장 배치 | 분당 3,000~3,600명 |
| purchase-token TTL | 600초 |
| 결제 성공률 (외부 PG 기준) | 40~50% |

---

## 2. 알려진 이슈 및 우선순위

| 항목 | 심각도/현황 | 발견 시점 | 대응 방향 |
|---|---|---|---|
| 해제된 좌석 재구매 시 이전 유저 주문 반환 | 최상 — 해결 방향 확정 | 2026-07-10 코드 리뷰 | order-service 멱등 폴백(`OrderCreationService`)에 `Order.userId` 비교 가드 추가. ADR 011 재검토 사항 |
| `checkout()` 트랜잭션 커밋 지연 레이스 | 알려진 제약(낮은 우선순위) | — | `assignOrder` DB 저장과 owner 키 `CONFIRMED` Redis 갱신 사이, `@Transactional` 커밋 전 시점에 `releaseHold`가 끼어들면 이론상 레이스 재현 가능. 윈도우가 매우 작아(로컬 DB 커밋 지연 수준) 우선순위 낮으나 해결 여부 결정 필요 |
| `QueueScheduler.findActiveShowIds()`의 `KEYS` 사용 | 알려진 제약(낮은 우선순위) | — | O(N) 전체 스캔이라 키 스페이스가 커지면 다른 Redis 명령(좌석 Hold 등)을 블로킹할 수 있음. 현재 동시 활성 공연 수가 적어 우선순위 낮으나 `SCAN` 커서 기반 전환 여부/시점 결정 필요 |
| `inventory:{show_id}` 재고 카운터 Redis 핫키 | 현재 트래픽 수준(50~60 TPS)에서는 문제 아님 | — | 공연 규모가 수십만 석 또는 멀티 리전으로 확장되면 `inventory:{show_id}:{shard_no}` 로컬 카운터 분할 검토. 단, 샤딩+주기적 합산은 현재 Lua 스크립트 기반 원자적 재고확인+선점의 정합성 보장을 깨는 트레이드오프이므로 도입 시 초과판매 방지 설계 재검토 필요 |
| 좌석 배치도 등 읽기 집중 키 부하 | 트리거 시 검토 | — | 반복 조회로 병목이 관측되면 Redis Replica 읽기 분산 또는 애플리케이션 레벨 짧은 TTL(1~2초) 로컬 캐시 검토. 현재는 선제적 대비일 뿐 실측 병목 없음 |
| Kafka 발행 신뢰성(Outbox 미적용) | 초안 존재([ADR 012](./adr/012-outbox-pattern-draft.md)) | — | `SeatEventProducer`가 `KafkaTemplate.send()` 직접 호출 — DB 커밋 후 발행 실패 시 이벤트 유실 가능. ADR 012 확정 후 구현 |
| Venue/Performance/Show 관리 API 부재 | 미구현 | — | 엔티티만 존재, 컨트롤러 없음. 시드 데이터로만 채워짐 |
| CONFIRMED 취소(공연 확정 후 환불) 시 좌석 처리 | 정책 변경([ADR 013](./adr/013-cancellation-window-performance-start-based.md) 결정 1 확정) | — | 취소 가능 시간 기준의 코드 반영은 order-service 담당 — [order-service/architecture.md §6](../order-service/architecture.md#6-미확정-항목) 참고 |
| 다중예매(N좌석 묶음) | v2로 보류 | — | A(1좌석=1주문 유지) / A'(batchId만 부여) / B(N좌석=주문 1개) 트레이드오프 검토 — [SA-260703.md §11](./archive/SA-260703.md#다중예매-옵션-결정-대기--담당자-확인-중) 참고 |
| 좌석 생성 API 부재 → inventory lazy 초기화 | 알려진 제약 | — | 좌석/쇼 생성 API가 없어 `inventory:{showId}`가 첫 hold 요청 시 DB 기준 lazy 초기화되는 게 유일한 경로 — [redis-keys.md §5](./redis-keys.md#5-inventoryshowid--잔여-재고-카운터) 참고. API 추가 시 생성 시점 세팅으로 전환 검토 |
| 대기열 단계 사용자 이탈 감지 수단 없음 | 미착수 | 2026-07-09 | `waiting_queue:{showId}` ZSET엔 TTL이 없고 `QueueSseService`의 emitter 정리는 서버 인스턴스 로컬 in-memory Map만 정리함 — 이탈 유저 자리를 스케줄러가 그대로 소비. 좌석 Hold(TTL 600초)와 달리 안전망 자체가 없음 |
| Redis 장애 복구(ADR 007) 미구현 | 결정만 있고 실행 안 됨 | 2026-07-10 재확인 | [ADR 007](./adr/007-redis-backup-strategy.md)이 "RDB 영속성 + 장애 시 PostgreSQL 기준 재적재"로 확정돼 있으나 재적재 코드가 없음(`CommandLineRunner`/`ApplicationRunner`/`@PostConstruct` 전무). ticketing Redis는 ElastiCache가 아니라 kafka EC2 인스턴스 위 자체 Redis(`terraform/ecs.tf:69`)를 쓰는데, 이 인스턴스는 단일 노드로 관리형 백업·복제가 전혀 없음 — `terraform/datastores.tf`의 ElastiCache "ticketing" 클러스터는 생성만 되고 실제로는 쓰이지 않는 고아 리소스라 그쪽 파라미터를 손봐도 무관하다. 노드 하나 죽으면 좌석 상태 전체가 유실되고 수동 복구 방법도 없음 |

## 관련

- [README.md](./README.md)
- [architecture.md](./architecture.md)
- [flows.md §6](./flows.md#6-좌석-선점-해제사용자-직접-취소) — 이슈 1의 재현 경로
