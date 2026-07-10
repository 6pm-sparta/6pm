# ticketing-service 부하테스트 진행 기록 (SLO-1·2·3)

> 작성: 조아영 · 최종 갱신: 2026-07-10
> 관련 이슈: #308 · 브랜치: `test/308-ticketing-loadtest`

---

## 담당 SLO

| SLO | 내용 | 담당 |
|-----|------|------|
| SLO-1 | 예매 핵심경로 성공률 ≥ 99% | 조아영 |
| SLO-2 | 예매 핵심경로 p99 ≤ 500ms | 조아영 |
| SLO-3 | 오버부킹 0% | 조아영 |

---

## k6 스크립트 목록

| 파일 | SLO | 인증 방식 | 필요 서비스 | 상태                          |
|------|-----|-----------|-------------|-----------------------------|
| `k6/ticketing-loadtest.js` | SLO-1·2 (대기열/순번/좌석조회) | gateway JWT | gateway+auth+ticketing | ✅ 완성                        |
| `k6/ticketing-direct-functional.js` | SLO-3 기능 검증 | X-Id-Card 직접 서명 | ticketing 단독 | ✅ 완성                        |
| `k6/ticketing-loadtest.js` (오버부킹 섹션) | SLO-3 동시성 | gateway JWT | gateway+auth+ticketing | ✅ 완성 (VUS=50)               |
| `k6/ticketing-scale-loadtest.js` | 스케일업 부하 | gateway JWT | gateway+auth+ticketing | ✅ 완성, 비용 문제로 인해 최적화 후 실행 필요 |

---

## 실행 결과

### SLO-1·2 — 통과 (2026-07-08)

| 지표 | 결과 | 목표 |
|------|------|------|
| 성공률 | 100% | ≥ 99% |
| p99 응답시간 | 12.36ms | ≤ 500ms |
| p95 응답시간 | 7.8ms | ≤ 300ms |

**⚠️ 주의사항**: 대기열 진입/순번조회가 Redis-only 연산이라 DB를 거치지 않음 → 실제 부하 검증이라기보단 Redis 경로 검증에 가까움.

---

### SLO-3 — ✅ 통과 (2026-07-08)

**시나리오**: VUS=50, 좌석 5개(A1~B2), 전원이 같은 좌석 동시 선점 시도

| 결과 | 건수 |
|------|------|
| hold 성공 | 1 |
| hold 거부 (SEAT_ALREADY_HELD, 409) | 49 |
| 오버부킹 | **0** |

**핵심 보장 메커니즘**: `SeatService.hold()` → Redis Lua 스크립트(`HOLD_SCRIPT`, SETNX 기반) 원자성 보장

**서버 메트릭**: `ticketing_overbooking_total` (Micrometer Counter, `SeatService.java:151` 근처 `SEAT_ALREADY_HELD` 분기)

**실행 명령어**:
```bash
k6 run -e SHOW_ID=<uuid> -e SEAT_ID=<uuid> -e VUS=50 k6/ticketing-loadtest.js
```

---

## 인프라 이슈 및 해결

### 1. Config-server PAT 이슈 (2026-07-07, 우회)
- **증상**: gateway/auth-service가 `jwt.secret` placeholder를 못 풀어 기동 실패 (`PlaceholderResolutionException`)
- **원인**: `CONFIG_GIT_USERNAME`/`CONFIG_GIT_TOKEN` PAT 문제
- **우회**: gateway 없이 ticketing-service 직접 연결 (`k6/ticketing-direct-functional.js`), k6가 HMAC 서명 직접 생성
- **상태**: gateway 복구 후 재측정 필요

### 2. QUEUE_SCHEDULER_DELAY 기본값 문제 (2026-07-08, 해결)
- **증상**: 폴링 타임아웃 안에 대기열 토큰 승격이 안 됨
- **해결**: 기동 시 `QUEUE_SCHEDULER_DELAY=2000` 설정
- **영구 해결**: 로컬 테스트 시 항상 이 값 사용

### 3. PostgreSQL 포트 충돌 (2026-07-08, 해결)
- **증상**: 로컬 `postgresql-x64-15` 서비스(5432)가 Docker `6pm-postgres-user`(5432)와 충돌 → 엉뚱한 DB 연결
- **해결**: `Stop-Service postgresql-x64-15`
- **재발 방지**: 로컬 postgres 시작 유형을 수동(Manual)으로 변경 권장

---

## Gateway 병목 규명 (2026-07-08)

고VU 환경에서 실패율 급등 원인 확정:
- **결론**: gateway가 **~600 req/s에서 포화** → 초과분 503(CircuitBreaker 폴백)으로 차단
- ticketing 서비스 자체(200 응답, 빠른 latency)는 정상 — gateway 병목으로 추정됨

| VU | 에러율 |
|----|--------|
| 1000 | ~97% |
| 150 | ~14% |
| 낮을수록 | 비례 감소 (포화 곡선) |

**개선 가설** (다만 인프라 비용 문제로 즉시 적용은 어려움)
- Gateway CircuitBreaker 임계값 완화
- 커넥션풀 상향
- 스케일아웃 (ECS Fargate replicas ↑)
- 라우트별 튜닝

**참고**:
- dev 환경(`6pm-dev-cluster`, ECS Fargate) 배포 완료 → 로컬 잡음 없이 재측정 가능
- gateway 병목 개선 (2026-07-09, 05596c3 커밋 참고)

---

## 스크립트 실행 가이드

### 선결 조건
```
docker compose --profile infra --profile o11y up -d
# IDE에서 기동: ticketing-service (QUEUE_SCHEDULER_DELAY=2000)
# 시드 데이터: 공연(SHOW_ID) + 좌석(SEAT_IDs) 확보
```

### SLO-3 오버부킹 검증
```bash
k6 run -e SHOW_ID=<uuid> -e SEAT_ID=<uuid> -e VUS=50 k6/ticketing-loadtest.js
```

### 스케일 부하 (처리량 측정)
```bash
k6 run -e SHOW_ID=<uuid> -e SEAT_IDS=<uuid1>,<uuid2> -e PEAK=300 k6/ticketing-scale-loadtest.js
```

### gateway 우회 기능 검증 (config-server PAT 이슈 시)
```bash
# ticketing-service 기동 시: -Dhmac.secret-key=6pm-fandom-sns-hmac-shared-secret-key-must-be-at-least-32-bytes-long
k6 run -e SHOW_ID=<uuid> k6/ticketing-direct-functional.js
```
