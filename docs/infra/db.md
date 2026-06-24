# 🗄️ 6pm DB 설계 (검토·보강판)

> 엔진: PostgreSQL 17 · 담당: 하준영(인프라) · 최종수정: 2026-06-24
> 한 줄: **로컬/MVP = 단일 DB + 서비스별 스키마(단순·저비용)** → **운영 = 마이그레이션·최소권한·(필요 서비스만)Read Replica**
> 대원칙: **각 서비스는 자기 스키마만 접근. 타 서비스 데이터는 직접 쿼리 금지 → API/이벤트로.**

---

## 1. 개요 & 트레이드오프 (왜 이렇게, 무엇을 감수)
- DB명 `fandom_db`(단일 인스턴스) + **서비스별 스키마**로 논리적 DB-per-service.
- **이유**: MVP/로컬은 단일 인스턴스가 단순·저비용. 코드/JPA는 스키마만 보므로 운영에서 **독립 DB로 분리해도 코드 변경 최소**.
- ⚠️ **감수하는 트레이드오프(인지하고 가는 것)**: 단일 인스턴스 = ① 공유 장애 도메인(DB 하나 죽으면 전 서비스 영향) ② 자원 경합(커넥션/CPU) ③ 서비스별 스케일·튜닝 불가. → 트래픽 커지면 12장 경로로 독립 DB 분리. **MVP에선 의도된 선택.**

## 2. 서비스별 스키마 매핑
| 서비스 | 포트 | 스키마 | 담당 |
|---|---|---|---|
| user-service | 8081 | `user_db` | #1 |
| auth-service | 8087 | `auth_db` | 인증 |
| feed-service | 8082 | `feed_db` | #2 |
| ticketing-service | 8083 | `ticket_db` | #3 |
| order-service | 8084 | `order_db` | #4 |
| notification-service | 8085 | `notify_db` | #5 |
| chat-service | 8088 | `chat_db` | 채팅 |
| aiops-service | 8086 | `aiops_db` | #6(준영) |

> eureka/config/gateway = DB 없음.

## 3. 접속 표준 (.env + application.yml)
- `.env`(팀 표준): `DB_URL`(`...?currentSchema=본인스키마`), `DB_USERNAME`, `DB_PASSWORD`.
- application.yml: `url:${DB_URL}` / `username:${DB_USERNAME}` / `password:${DB_PASSWORD}` + `hibernate.default_schema: 본인스키마`.
- **ddl-auto** ★:
  - 로컬/개발 = **`update`** (편의)
  - 운영 = **`validate`** (자동 ALTER 금지) + **마이그레이션 도구**(5장). `update`/`create`는 운영 금지(예측불가 변경·락·데이터손실 위험).
- **HikariCP 커넥션 풀** ★: 서비스별 `maximum-pool-size`를 작게(예 5~10). ⚠️ 단일 인스턴스라 **(서비스 수 × 풀크기) < DB `max_connections`(기본 100)** 를 넘기지 말 것. (#44 알림에 `hikaricp_connections_pending` 룰 있음)

## 4. PK · 감사 · 소프트삭제 규약 (common `BaseEntity`)
- **PK** `id` = **UUIDv7**(`@PrePersist` 자동). 시간순 정렬이라 인덱스 단편화↓. (트레이드오프: 16바이트로 bigint보다 큼 — 수용)
- **감사** `createdAt`/`updatedAt` 자동(각 서비스 `@EnableJpaAuditing` 필요). `createdBy`/`updatedBy` = AuditorAware 연결 후(현재 null).
- **소프트삭제** `deletedAt` ★: 조회 시 **`deleted_at IS NULL` 필터 필수**. 권장: 엔티티에 `@SQLRestriction("deleted_at is null")` + 유니크 제약은 **부분 유니크 인덱스**(`WHERE deleted_at IS NULL`)로 → "삭제된 행이 재등록을 막는" 문제 방지.
- 시각 컬럼은 **`TIMESTAMPTZ`**(UTC 저장) 권장 — TZ 혼선 방지.

## 5. 스키마 생성 & 마이그레이션 ★
- **로컬**: `infra/init-db.sql`(docker entrypoint, 빈 볼륨 시 1회). 변경 반영은 `down -v` 후 재기동. 도메인 테이블은 `ddl-auto=update`가 생성.
- ⚠️ **운영**: `ddl-auto=update` 금지. **Flyway/Liquibase**로 버전 마이그레이션(`V1__init.sql`, `V2__add_reservation.sql`...) + `validate`로 엔티티-스키마 일치만 검증.
- 권장 시점: MVP 후반~운영 진입 시 Flyway 도입(스키마 변경 이력·롤백·협업 안전).

## 6. 보안: 서비스별 최소권한 DB 유저 ★
- 로컬: `root` 공용 OK(편의).
- 운영: 서비스별 유저(`user_svc`, `ticket_svc`...) 생성 + **자기 스키마에만 GRANT** → 서비스 경계를 DB 레벨에서 강제(버그/침해 시 타 스키마 격리). 비밀번호 = **Secrets Manager/SSM**(평문 금지). (패턴은 `init-db.sql` 주석에 준비됨)

## 7. 동시성·무결성 — 오버부킹 방어 ★★ (선착순 핵심)
좌석 더블부킹은 **2중 방어**로:
1. **Redis 분산락(1차)** — `lock:seat:{seatId}`로 동시 진입 차단(애플리케이션). 상세: `dev-start-guide.md` 8장.
2. **DB UNIQUE 제약(최후 방어)** — 예약 테이블에 `UNIQUE(concert_id, seat_id)` (취소 허용이면 활성예약만 **부분 유니크**). → **락이 뚫리거나 버그가 나도 DB가 더블부킹을 거부.**
- 재고/카운터는 **Redis 원자연산**(decr 등)으로(DB 락만으론 피크에 한계).
- **read-after-write**: 예매 직후 "내 예매 조회"는 writer로(또는 짧은 지연 허용).


## 8. Read Replica = B안 (운영 최적화, **W3+**)
| 서비스 | Replica | 근거 |
|---|---|---|
| feed | **Y** | 타임라인 조회 ≫ 쓰기(~20:1), 지연=이탈 (1순위) |
| ticketing | **Y(조회만)** | 공연·좌석 *조회*는 replica, *선점(쓰기)* 은 writer+락 |
| user | **조건부** | 로그인검증·프로필 조회량 보고 결정 |
| order/notification/aiops | **N(단일)** | 쓰기 위주·정합성·저트래픽 |

- ⚠️ **MVP는 단일 DB로 충분.** Replica 라우팅(`AbstractRoutingDataSource` 또는 `@Transactional(readOnly=true)`+라우팅)은 **구현 복잡 → W3+ 부하테스트 수치 보고 도입.** 지금은 설계만.
- 도입 시: **복제지연(lag) 모니터링**(Grafana), read-after-write는 writer.

## 9. 인덱스 전략 ★
PK 외, 각 서비스가 **조회 패턴에 맞는 인덱스**를 정의(없으면 풀스캔→느림):
- feed: `(user_id, created_at DESC)` 타임라인
- ticketing: `UNIQUE(concert_id, seat_id)`, `(concert_id)` 좌석조회
- order: `(user_id, created_at)`, 결제상태
- 공통: FK 컬럼, 자주 쓰는 WHERE/ORDER BY 컬럼. (불필요한 과다 인덱스는 쓰기 비용↑ — 균형)

## 10. AIOps 테이블 `aiops_db.incident_alert_history` (MTTR)
| 컬럼 | 용도 |
|---|---|
| id(UUID), alert_name, severity, source_service | 식별·분류 |
| fired_at, resolved_at, mttr_seconds | **MTTR 계산** |
| ai_summary, ai_root_cause, ai_guide | LLM 분석(#128) |
| raw_payload(JSONB), slack_ts | 원본/Slack 추적 |
인덱스: `fired_at DESC`, `severity`.

## 11. Redis 정책 — A안 (2개, 부하 특성 분리)
- **Redis-일반**(캐시/세션/refresh) + **Redis-티켓팅**(대기열/분산락/카운터). 로컬은 1개 공유, 운영 분리(ElastiCache). 락/대기열이 일반 캐시 성능을 갉아먹지 않게.

## 12. 운영 전환 · 확장 경로
1. 단일 `fandom_db`+스키마 → 2. **서비스별 독립 DB** 분리 → 3. **Read Replica**(B안) → 4. **최소권한 유저** → 5.(선택) **CQRS**.
- 운영 공통: **자동 백업/PITR, multi-AZ, `deletion_protection=true`, 파라미터 그룹 튜닝.**

## 13. ✅ 운영 진입 체크리스트
- [ ] `ddl-auto: update → validate` + **Flyway** 도입
- [ ] 서비스별 **최소권한 유저**, 비번 **Secrets Manager**
- [ ] HikariCP 풀 합 **< `max_connections`**
- [ ] 좌석 **UNIQUE 제약**(오버부킹 최후 방어)
- [ ] 소프트삭제 **필터/부분 유니크 인덱스**
- [ ] **백업/PITR · multi-AZ · deletion_protection**
- [ ] (트래픽 시) **Read Replica + lag 모니터링**

---
*관련: `decisions.md`(D1~D3 근거) · `dev-start-guide.md`(접속·동시성 코드) · `prod-migration.md`(전환) · `DB_REPLICA_TEMPLATE.md`(판단표) · `infra/init-db.sql`*
