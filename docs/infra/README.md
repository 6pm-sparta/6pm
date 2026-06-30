# 📚 6pm 문서 인덱스 (docs/)

> **이 폴더가 설계·결정·규약의 단일 출처(Single Source of Truth)입니다.**
> 원칙: **구현 세부 = 코드**, **설계·결정·규약 = `docs/`**, **결정 내용 = 회의 후 문서에 기록(서기)**.
> 흩어진 노션 내용은 이리로 모읍니다. 빠진 게 있으면 인프라 담당(하준영)에게 → 바로 채웁니다.

## 문서 목록
| 문서 | 무엇을 보나 |
|---|---|
| **`decisions.md`** | ⭐ **모든 인프라 결정의 "왜"**(DB/Redis/Kafka/배포/관측/규약). 의사결정 근거는 여기서. |
| **`dev-start-guide.md`** | 개발 시작(로컬 실행·.env·build.gradle·application.yml·공통규약·서비스별 셋업·ticketing 분산락) |
| **`db.md`** (= DB_DESIGN) | DB 설계: **서비스별 독립 DB 인스턴스 + public 스키마**, Replica B안, 접속 표준 |
| **`logging-standard.md`** | 로그 표준(ECS JSON·레벨·3종·DO/DON'T) — 전 서비스 공통 |
| **`alert-scenarios.md`** | 서비스별 위험 시나리오·감지/알림 기준 (각 담당 작성, ~6/29) |
| **`prod-migration.md`** | 로컬→운영(ECS) 전환 시 서비스별 점검·변경 + **지금 설계에 미리 반영할 것** |
| **(예정) `observability.md`** | 관측(모니터링) 개요 + 부하테스트 계획 |

## 자주 찾는 것 (질문→위치)
- **결정의 이유가 궁금** → `decisions.md`
- **내 서비스 어떻게 시작?** → `dev-start-guide.md`
- **모니터링은 어디서 확인?** → 구현됨: Grafana(3000)/Prometheus(9090)/Loki/Zipkin(9411)/Alertmanager(9093). 설정 코드: **`infra/prometheus.yml`·`alert-rules.yml`·`alertmanager.yml`·`loki-config.yml`·`promtail-config.yml`**, Grafana 데이터소스: `infra/grafana/provisioning/`. 표준: `logging-standard.md`.
- **부하테스트 설계는?** → **W3 예정**. 감지/알림 대상 시나리오는 `alert-scenarios.md`에 먼저 취합 중(오버부킹 등). 부하테스트로 그 임계치를 실측·확정 → Prometheus 룰/AIOps로 구현.
- **운영 전환 시 바뀌는 것?** → `prod-migration.md`
- **운영 인프라 IaC** → `/terraform` (코드) + `decisions.md` D6

## 운영 규칙 (팀 합의 제안)
1. **코드 기준**: 동작/구현의 진실은 코드.
2. **docs/ 기준**: 설계·결정·규약의 진실은 이 폴더(노션은 회의록/임시용).
3. **결정 기록**: 회의에서 정한 결정은 `decisions.md`(또는 해당 문서)에 **서기가 즉시 기록** → PR로 반영.
