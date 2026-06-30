-- =====================================================================
-- 6pm-fandom-sns · AIOps DB 초기화 (DB-per-service / public 스키마)
-- =====================================================================
-- postgres-aiops 컨테이너(POSTGRES_DB=aiops_db)의 init 디렉터리에 마운트되어
-- 최초 1회(데이터 볼륨이 비어 있을 때) 자동 실행된다.
--   docker-compose: ./db-init/aiops:/docker-entrypoint-initdb.d
--   (이미 데이터가 있으면 docker compose down -v 로 볼륨 초기화 후 재기동)
--
-- DB-per-service 전환에 따라 PgSQL 스키마(논리 격리)는 사용하지 않는다.
-- aiops_db 는 독립 데이터베이스이며, 객체는 public 스키마에 생성한다.
-- 나머지 테이블은 각 서비스 JPA(ddl-auto)가 생성하지만,
-- incident_alert_history 는 JPA 엔티티가 아니라 SQL 로 선반영한다(MTTR 추적용).
-- =====================================================================

CREATE TABLE IF NOT EXISTS incident_alert_history (
    id              UUID         PRIMARY KEY,
    alert_name      VARCHAR(200) NOT NULL,            -- Alertmanager rule 이름
    severity        VARCHAR(20)  NOT NULL,            -- critical / warning / info
    source_service  VARCHAR(100),                     -- 장애 발생 서비스
    fingerprint     VARCHAR(255),                     -- (#128) Alertmanager 알림 시리즈 고유키(중복/해소 매칭)
    fired_at        TIMESTAMPTZ  NOT NULL,            -- 알림 발생 시각
    resolved_at     TIMESTAMPTZ,                      -- 해소 시각 (null=진행중)
    mttr_seconds    BIGINT,                           -- resolved_at - fired_at (초)
    ai_summary      TEXT,                             -- LLM 1. 에러 요약
    ai_root_cause   TEXT,                             -- LLM 2. 원인 추정
    ai_guide        TEXT,                             -- LLM 3. 개선 가이드
    raw_payload     JSONB,                            -- 원본 Prometheus 알림 JSON
    slack_ts        VARCHAR(50),                      -- Slack 메시지 타임스탬프
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_incident_fired_at  ON incident_alert_history (fired_at DESC);
CREATE INDEX IF NOT EXISTS idx_incident_severity  ON incident_alert_history (severity);
-- (#128) 진행 중(active) 사건을 fingerprint 로 빠르게 매칭 (firing↔resolved 짝짓기/중복 방지)
CREATE INDEX IF NOT EXISTS idx_incident_fp_active ON incident_alert_history (fingerprint)
    WHERE resolved_at IS NULL;
