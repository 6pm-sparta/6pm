# 🧭 6pm 인프라 의사결정 기록 (ADR — "왜 이렇게 정했나")

> 담당: 하준영(인프라) · 형식: 결정 / 배경·이유 / 검토한 대안 / 트레이드오프
> 새 결정은 회의에서 정한 뒤 **여기에 한 항목씩 추가**(서기 기록). 구현 세부는 코드, 결정 근거는 이 문서.

---

## D1. DB 엔진 = PostgreSQL 17
- **결정**: PostgreSQL 17 (`postgres:17-alpine`)
- **이유**: 튜터 피드백 반영(PostgreSQL 권장). JSONB(AIOps raw_payload 등)·풍부한 기능·안정성. 17은 최신 stable.
- **대안**: MySQL → 기능/JSON 처리에서 PostgreSQL 우위로 제외.
- **트레이드오프**: 팀 친숙도는 MySQL이 높을 수 있으나, 표준 JPA라 차이 작음.

## D2. 단일 DB(`fandom_db`) + 서비스별 스키마 분리 ~~(채택)~~ → **D11로 대체됨 (Superseded)**
- **결정**: 인스턴스 1개 + 스키마(`user_db`/`feed_db`/`ticket_db`...)로 논리적 DB-per-service.
- **이유**: MVP/로컬은 단일 인스턴스가 단순·저비용. 코드/JPA는 **스키마만** 바라보므로, 운영에서 서비스별 독립 DB로 분리해도 코드 변경 최소.
- **대안**: 처음부터 서비스별 독립 인스턴스 → 로컬 비용·복잡도 과다로 보류.
- **규칙**: 각 서비스는 **자기 스키마만** 접근. 타 서비스 데이터는 API/이벤트로.
- ⚠️ **2026-06-29 D11로 대체.** Production 대비 인프라 재구성(#207)에서 서비스별 독립 DB 인스턴스로 물리 분리하고 PgSQL 스키마는 제거. 이 결정의 "운영 전환 시 독립 DB 분리" 경로를 앞당겨 실행한 것. (이 항목은 의사결정 이력 보존을 위해 남겨둔다.)

## D3. Read Replica = B안 (트래픽 큰 서비스만)
- **결정**: feed(타임라인)·ticketing(조회)·user(조건부)만 Read Replica. order/notification/aiops는 단일.
- **이유**: 전체 복제는 비용·복잡도↑. 조회≫쓰기인 서비스만 분산이 효율적.
- **주의**: 좌석 선점 같은 **쓰기 동시성은 Replica로 해결 안 됨 → Redis 분산락**. read-after-write 필요 조회는 writer로.

## D4. Redis = A안 (일반 / 티켓팅 2개 분리)
- **결정**: Redis-일반(캐시/세션/refresh) + Redis-티켓팅(대기열/분산락/카운터). **로컬·운영 모두 2개로 분리.**
- **이유**: 티켓팅 피크의 락/대기열 부하가 일반 캐시 성능을 갉아먹지 않게 **부하 특성 분리**.
- **대안**: 단일 Redis → 선착순 피크에 캐시까지 영향 우려로 분리.
- 📌 **2026-06-29 갱신(#207)**: 기존엔 "로컬은 1개 공유"였으나, 로컬도 `redis-general`(6379) / `redis-ticketing`(6380) 2개로 실제 분리. 로컬-운영 환경 동등성 확보.

## D5. Kafka = KRaft + dual-listener(INTERNAL/EXTERNAL)
- **결정**: `INTERNAL://kafka:29092`(컨테이너 내부) + `EXTERNAL://localhost:9092`(호스트).
- **이유**: 컨테이너(kafka-ui 등)와 호스트(IntelliJ 서비스) **양쪽에서 접속** 필요. 단일 리스너면 한쪽이 연결 실패.

## D6. 배포 = ECS Fargate + Terraform(IaC)
- **결정**: 컨테이너를 ECS **Fargate**(서버리스)로, 인프라는 **Terraform**으로 코드화.
- **이유**: EKS(쿠버네티스)는 부트캠프 규모에 과함·운영부담↑. EC2 직접관리보다 Fargate가 서버관리 부담 없음. Terraform=재현성/리뷰/버전관리.
- **현 상태**: 실제 배포 X, **IaC 작성**까지(튜터 피드백). 코드: `/terraform`.

## D7. 관측 스택 = Prometheus + Grafana + Loki + Zipkin + Alertmanager
- **결정**: 메트릭=Prometheus, 시각화=Grafana, 로그=Loki, 추적=Zipkin, 알림=Alertmanager.
- **이유**: 오픈소스 표준 + 무료 + **Grafana 한 화면 통합**. AIOps 입력 데이터 토대.

## D8. 로그 = 구조화(ECS JSON) 파일 → Promtail → Loki
- **결정**: 파일은 ECS JSON 1줄/이벤트(콘솔은 평문).
- **이유**: 검색/파싱/상관관계(trace_id) 용이 + **AIOps LLM이 읽기 좋은 형태**. 상세: `logging-standard.md`.

## D9. 공통 규약 = ApiResponse / CustomException·ErrorCode / BaseEntity(UUIDv7)
- **결정**: 응답=`ApiResponse<T>`, 예외=`CustomException`+도메인 `ErrorCode`, 엔티티=`BaseEntity` 상속(PK=UUIDv7).
- **이유**: 서비스 간 응답/에러 형식 일관 → 협업·리뷰·게이트웨이 처리 쉬움. UUIDv7=시간순 정렬 PK라 인덱스 단편화 적음.
- **코드**: `common/` 모듈. 사용법: `dev-start-guide.md` 5장.

## D10. 패키지 구조 = 레이어드(application/domain/global/infra/presentation)
- **결정**: feed-service 구조를 표준으로 통일.
- **이유**: 서비스 간 구조 일관 → 코드 탐색·리뷰·온보딩 비용↓.

## D11. DB-per-service 물리 분리 + public 스키마 (D2 대체)
- **결정**: 서비스별 **독립 PostgreSQL 인스턴스**로 물리 분리(`user_db`/`feed_db`/`ticketing_db`/`order_db`/`notification_db`/`aiops_db`/`chat_db`). 각 DB는 **public 스키마**를 사용하고, 기존 PgSQL 네임스페이스 스키마(`currentSchema`/`default_schema`)는 **제거**. 로컬 docker-compose가 AWS RDS N개 구조에 1:1 대응.
- **배경·이유**: Production 대비. 단일 인스턴스(D2)는 ① 공유 장애 도메인(DB 하나 죽으면 전 서비스 영향) ② 자원 경합 ③ 서비스별 스케일·튜닝 불가 라는 트레이드오프가 있었음. D2가 "운영 전환 시 독립 DB 분리"를 미래 경로로 뒀는데, 이를 앞당겨 실행. DB를 물리적으로 분리하면 격리가 DB 레벨에서 달성되므로, 그 안에서 다시 네임스페이스 스키마로 나누는 것은 **중복** → public으로 단순화.
- **검토한 대안**: ① 단일 인스턴스 안에서 database 레벨 분리(인스턴스는 공유) → 진짜 물리 격리 아님으로 제외. ② 스키마(네임스페이스) 유지 → 독립 DB에선 중복이라 제거. ③ public 전환 시 각 서비스 yml에서 `currentSchema`/`default_schema`만 제거하면 되어 코드 영향 작음(yml 2줄 수준).
- **트레이드오프**: 로컬 postgres 컨테이너 수 증가(7개) → docker-compose **profile을 서비스별 1:1**로 두어 "필요한 것만" 기동(`--profile db-user --profile redis-general`)으로 완화.
- **로컬 포트**: user 5432 · feed 5433 · ticketing 5434 · order 5435 · notification 5436 · aiops 5437 · chat 5438. (auth/gateway는 DB 미사용)
- **계정 / 연결 중앙화**: 이번 단계는 **인프라 뼈대**(docker-compose / db-init / .env.example)까지. DB 계정은 현재 공용(root) 유지, 서비스별 전용 계정 분리와 config-server(git repo) 연결 중앙화는 **후속 단계**.
- **관련**: 이슈 #207, `db.md`, `dev-start-guide.md`, `.env.example`.

---
*변경 시: 회의 결정 → 이 문서에 항목 추가(서기) → PR. "결정의 이유"는 항상 여기서 확인.*
