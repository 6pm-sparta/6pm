-- =====================================================================
-- 6pm-fandom-sns · PostgreSQL 초기화 (서비스별 스키마 분리)
-- =====================================================================
-- 적용 방법
--   1) 로컬(docker): postgres 컨테이너의 init 디렉터리에 마운트하면 최초 1회 자동 실행
--        volumes:
--          - ./infra/init-db.sql:/docker-entrypoint-initdb.d/01-init.sql
--      (주의: /docker-entrypoint-initdb.d 는 "데이터 볼륨이 비어 있을 때"만 실행됨.
--       이미 데이터가 있으면 `docker compose down -v` 로 볼륨 초기화 후 재기동)
--   2) RDS: 인스턴스 생성 후 psql 로 1회 실행
--        psql "host=<rds-endpoint> port=5432 dbname=fandom_db user=root" -f init-db.sql
-- =====================================================================

-- 데이터베이스는 docker-compose의 POSTGRES_DB=fandom_db 로 이미 생성됨.
-- RDS에서 수동 생성 시:  CREATE DATABASE fandom_db ENCODING 'UTF8';
-- 아래는 fandom_db 에 접속한 상태에서 실행한다고 가정.

-- ---------------------------------------------------------------------
-- 1) 서비스별 스키마 (논리적 DB-per-service)
-- ---------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS user_db;     -- User Service     (:8081)
CREATE SCHEMA IF NOT EXISTS auth_db;     -- Auth Service     (:8087)
CREATE SCHEMA IF NOT EXISTS feed_db;     -- Feed Service     (:8082)
CREATE SCHEMA IF NOT EXISTS ticket_db;   -- Ticketing Service(:8083)
CREATE SCHEMA IF NOT EXISTS order_db;    -- Order & Payment  (:8084)
CREATE SCHEMA IF NOT EXISTS notify_db;   -- Notification     (:8085)
CREATE SCHEMA IF NOT EXISTS chat_db;     -- Chat Service     (:8088)
CREATE SCHEMA IF NOT EXISTS aiops_db;    -- AIOps Service    (:8086)

-- ---------------------------------------------------------------------
-- 2) (권장) 서비스별 전용 DB 유저 — 운영 단계에서 최소권한 분리
--    로컬 MVP는 root 공용으로 써도 되지만, 분리 패턴을 미리 깔아두면 +α.
--    비밀번호는 예시. 실제 값은 Secrets로 주입(코드/스크립트에 평문 금지).
-- ---------------------------------------------------------------------
-- DO $$ BEGIN
--   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'user_svc')  THEN CREATE ROLE user_svc  LOGIN PASSWORD 'CHANGE_ME'; END IF;
--   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'feed_svc')  THEN CREATE ROLE feed_svc  LOGIN PASSWORD 'CHANGE_ME'; END IF;
--   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ticket_svc')THEN CREATE ROLE ticket_svc LOGIN PASSWORD 'CHANGE_ME'; END IF;
--   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'order_svc') THEN CREATE ROLE order_svc LOGIN PASSWORD 'CHANGE_ME'; END IF;
--   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'notify_svc')THEN CREATE ROLE notify_svc LOGIN PASSWORD 'CHANGE_ME'; END IF;
--   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'aiops_svc') THEN CREATE ROLE aiops_svc LOGIN PASSWORD 'CHANGE_ME'; END IF;
--   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'auth_svc')  THEN CREATE ROLE auth_svc  LOGIN PASSWORD 'CHANGE_ME'; END IF;
--   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'chat_svc')  THEN CREATE ROLE chat_svc  LOGIN PASSWORD 'CHANGE_ME'; END IF;
-- END $$;
-- GRANT ALL ON SCHEMA user_db   TO user_svc;
-- GRANT ALL ON SCHEMA auth_db   TO auth_svc;
-- GRANT ALL ON SCHEMA feed_db   TO feed_svc;
-- GRANT ALL ON SCHEMA ticket_db TO ticket_svc;
-- GRANT ALL ON SCHEMA order_db  TO order_svc;
-- GRANT ALL ON SCHEMA notify_db TO notify_svc;
-- GRANT ALL ON SCHEMA chat_db   TO chat_svc;
-- GRANT ALL ON SCHEMA aiops_db  TO aiops_svc;

-- ---------------------------------------------------------------------
-- 3) AIOps: 장애 분석 리포트 적재 테이블 (MTTR 추적용)
--    Phase 4 산출물. 나머지 테이블은 JPA ddl-auto=update 로 각 서비스가 생성.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS aiops_db.incident_alert_history (
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

CREATE INDEX IF NOT EXISTS idx_incident_fired_at  ON aiops_db.incident_alert_history (fired_at DESC);
CREATE INDEX IF NOT EXISTS idx_incident_severity  ON aiops_db.incident_alert_history (severity);
-- (#128) 진행 중(active) 사건을 fingerprint 로 빠르게 매칭 (firing↔resolved 짝짓기/중복 방지)
CREATE INDEX IF NOT EXISTS idx_incident_fp_active ON aiops_db.incident_alert_history (fingerprint)
    WHERE resolved_at IS NULL;

-- ---------------------------------------------------------------------
-- 확인용
--   SELECT schema_name FROM information_schema.schemata
--   WHERE schema_name IN ('user_db','auth_db','feed_db','ticket_db','order_db','notify_db','chat_db','aiops_db');
-- ---------------------------------------------------------------------
