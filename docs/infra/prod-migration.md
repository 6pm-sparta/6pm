# 🔄 로컬 → 운영(ECS) 전환 가이드 · 서비스별 점검 체크리스트

> 담당: 하준영(인프라) · 목적: "운영 profile로 갈 때 서비스별로 뭘 점검/변경하나" + **지금 설계에 미리 반영할 것**
> 실제 전환은 W3+(선택). 단, **구조를 알면 지금 코딩 시 미리 대비** 가능 → 이 문서가 그 구조.
> 최종 수정: 2026-06-29, 이재범(User, Auth and Gateway)

---

## 1. 인프라 매핑 (로컬 → 운영)
| 구성 | 로컬 | 운영(AWS) | 서비스 영향 |
|---|---|---|---|
| DB | docker postgres **서비스별 인스턴스**(5432~5438) | **RDS** PostgreSQL 서비스별(Writer+ReadReplica) | 접속 주소만 바뀜 (이미 인스턴스 분리됨) |
| Redis | docker redis **2개**(6379/6380) | **ElastiCache** (일반/티켓팅 분리) | 접속 주소만 바뀜 (이미 분리됨) |
| Kafka | docker `localhost:9092` | **MSK** | bootstrap 주소 |
| 서비스 실행 | IntelliJ/로컬 | **ECS Fargate** (컨테이너) | 이미지화·헬스체크 |
| 설정/시크릿 | `.env` | **SSM Parameter Store / Secrets Manager** | 주입 방식 |
| 로그 | 파일→Loki | CloudWatch(ECS awslogs) | **형식 동일(ECS JSON)** |
| 진입 | gateway 직접 | **ALB → gateway** | - |

> 코드(IaC): `/terraform`. 설계 근거: `decisions.md` D6, D11.
> ℹ️ 2026-06-29(#207): DB/Redis는 로컬에서 이미 서비스별 인스턴스·Redis 2개로 분리 완료. 운영 전환 시 연결 주소만 환경별로 교체.

## 2. 전환 시 공통 변경 (모든 서비스)
- **profile 분리**: `application-prod.yml` 추가 → DB/Redis/Kafka **엔드포인트, eureka/config 주소**만 다르게. 로컬은 `application.yml`.
- **접속정보 외부화**: 코드/yml에 `localhost`·비밀번호 **하드코딩 금지** → 환경변수/Secrets. (로컬도 이미 `.env` 패턴이라 그대로 이어짐)
- **헬스체크**: `/actuator/health` (ALB가 이 경로로 헬스체크) — 이미 노출 중.
- **로깅**: 형식은 그대로(ECS JSON), 운영은 stdout→CloudWatch로 수집(파일 마운트 불필요).

## 3. ⭐ 지금(설계 단계) 미리 반영할 것 — 서비스 공통
"운영 전환을 쉽게" 하려면 지금부터 이렇게 코딩:
- ✅ **모든 접속정보는 설정값(env/yml)으로** — `localhost` 직접 박지 않기
- ✅ **서비스 간 호출은 서비스명(eureka/Feign)** 으로 — IP/포트 하드코딩 금지
- ✅ **이벤트에 `eventId`(멱등성)·`occurredAt`** 포함 (재처리/순서 대비) — `dev-start-guide.md` 6장
- ✅ **상태 비저장(stateless)** — 세션/대기열 상태는 Redis로 (ECS는 컨테이너가 늘었다 줄어듦)

## 4. 🎟️ ticketing 설계 시 미리 반영할 것 (조아영님)
운영 구조를 반영해 **지금 설계에 넣어두면 W3 전환이 매끄러움**:
- **조회/쓰기 분리 대비(Read Replica)**: 좌석·공연 *조회*는 reader, *선점/예매(쓰기)* 는 writer로 가는 걸 전제로 설계. (단순 구현은 단일 DataSource로 두되, 조회/명령 메서드를 분리해두면 나중에 라우팅만 붙이면 됨)
- **분산락은 "티켓팅 Redis 인스턴스" 가정**: 운영에서 일반 Redis와 분리되므로, 락/대기열은 별도 RedisConnectionFactory(또는 RedissonClient)로 잡을 수 있게 추상화.
- **재고/카운터는 Redis(원자연산)** 로 — DB 락만으로는 선착순 피크에 한계.
- **이벤트(예매 성공/취소)는 토픽 규약 준수** + `eventId`로 멱등 — 결제/알림이 중복 처리 안 되게.
- **read-after-write 주의**: 예매 직후 "내 예매 조회"는 writer로(또는 짧은 지연 허용).

## 5. 운영 전환 절차 요약 (W3+, 참고)
1. Terraform `plan/apply`로 인프라 생성(VPC/RDS/ElastiCache/MSK/ECS/ALB)
2. 시크릿을 Secrets Manager/SSM에 등록
3. 서비스 이미지 빌드 → ECR push → ECS 배포(CodeDeploy/Actions)
4. `application-prod.yml`로 엔드포인트 전환
5. 헬스체크·로그(CloudWatch)·메트릭 확인

---
*관련: `decisions.md`(왜), `/terraform`(IaC), `dev-start-guide.md`(개발 셋업), `db.md`(replica 정책).*
