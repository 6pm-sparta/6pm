# 6pm 부하테스트 — 팀 실행 가이드 (k6)

> 주도: 하준영(관측·AIOps) · 대상: 각 서비스 담당
> **파일은 전부 `k6/` 폴더에 함께 둔다** (스크립트들이 `./common.js`를 import 하므로).

---

## 0. 파일 구성

```
k6/
 ├─ common.js                 ← 공통 헬퍼 (토큰풀·UUID·표준옵션). 다른 스크립트가 import.
 ├─ ticketing-loadtest.js     ← SLO-1·2 예매 핵심경로 (대기열→순번→좌석목록)
 ├─ feed-loadtest.js          ← SLO-5 피드 타임라인
 ├─ order-loadtest.js         ← SLO-4 결제 (큐 우회)
 ├─ notification-loadtest.js  ← 알림 조회
 ├─ user-loadtest.js          ← SLO-7 가입/로그인
```

---

## 1. 사전 준비 (한 번)

- [ ] **k6 설치**: `winget install k6 --source winget` → `k6 version`
- [ ] **인프라+관측 기동**: `docker compose --profile infra --profile o11y up -d`
- [ ] **내 서비스 기동**(IDE): config-server → eureka → gateway → **내 서비스**(+ user/auth: 토큰 발급용)
- [ ] **Prometheus 타깃 UP** 확인: http://localhost:9090/targets (내 서비스가 초록)
- [ ] **Grafana** 대시보드에서 내 서비스(job) 선택 가능한지: http://localhost:3000
- [ ] **시드 데이터**: 내 API가 필요로 하는 것(공연·좌석/게시글 등)

> ⚠️ **gateway 재시작 직후엔 60초 기다렸다가** 부하 시작. (LB 캐시가 비어 "No servers"→503 창이 있음)

---

## 2. 실행 방법 (서비스별)

**로컬은 한 머신에 다 올라가 있어서, 무리하게 PEAK를 올리면 gateway/머신이 먼저 포화**된다.
→ 아래 **권장 PEAK(현실적)** 로 시작해서, SLO가 무너지는 지점을 관찰한다.

```bash
cd k6

# 티켓팅 (SLO-1·2) — SHOW_ID는 시드된 공연 UUID
k6 run -e SHOW_ID=<uuid> -e USER_COUNT=200 -e PEAK=150 ticketing-loadtest.js

# 피드 (SLO-5)
k6 run -e USER_COUNT=200 -e PEAK=150 feed-loadtest.js

# 결제 (SLO-4) — order-service(8084) 기동 필요. 먼저 PEAK=5로 경로 확인 후 본 부하.
k6 run -e USER_COUNT=100 -e PEAK=80 order-loadtest.js

# 알림
k6 run -e USER_COUNT=200 -e PEAK=150 notification-loadtest.js

# 가입/로그인 (SLO-7)
k6 run -e PEAK=150 user-loadtest.js

# 결과 파일로 저장하려면 (발표용)
k6 run --summary-export=result_feed.json -e PEAK=150 feed-loadtest.js
```

**공통 env**: `BASE_URL`(기본 localhost:8080), `USER_COUNT`(토큰풀), `PEAK`(최대 VU), `SLEEP`(작을수록 압력↑).

---

## 3. ⭐ 겪은 문제 & 스크립트가 막는 법 (꼭 읽기)

부하테스트하며 실제로 터졌던 것들. **common.js가 대부분 자동으로 막지만, 원리를 알아두면 디버깅이 빠르다.**

| 증상 | 원인 | 스크립트가 막는 법 / 대응 |
|---|---|---|
| **setup "토큰 0개"** | 닉네임/이메일 **중복(409)** — 재실행 시 같은 값 | `common.js`가 **stamp로 매 실행 유니크** 생성 ✅ |
| **97% 실패인데 응답 빠름** | **rate limit(429)** 또는 **CircuitBreaker(503)** — 가짜 에러 | PEAK 낮추기(아래) / gateway rate·CB 확인 |
| **503 대량 (gateway)** | 고부하 시 gateway가 **~600 rps에서 포화** → 폴백 | **PEAK를 현실적으로**(150 등). 그 위는 gateway 벽 |
| **그래프가 텅 빔** | Prometheus 타깃 DOWN(서비스 미기동) | 부하 전 `9090/targets` 초록 확인 |
| **Zipkin 다운** | 고VU에서 트레이싱 폭주 | 스트레스 땐 `TRACING_SAMPLING=0.05` |
| **재시작 직후 전부 503** | LB "No servers" 창 | **재시작 후 60초 대기 → 로그인 확인 → 부하** |
| **p99가 요약에 없음** | k6 기본은 p90/p95까지 | `common.js`의 `loadOptions`가 **p99 표시** ✅ |

> **핵심 마인드셋**: 부하테스트는 "머신을 터뜨리는 게 아니라 **SLO가 무너지기 시작하는 지점**을 찾는 것". 그리고 **가짜 에러(429/503/409)를 먼저 걷어내야 진짜 병목이 보인다.**

---

## 4. 관측 — 무엇을 보나 (Grafana, 내 서비스 job 선택)

| 패널 | 움직이면 = 병목 |
|---|---|
| p99 / 엔드포인트별 p99 | 어느 API가 먼저 치솟나 |
| **HikariCP pending** | 0 초과 = **DB 커넥션풀 고갈**(1순위) |
| CPU / Heap / GC | 포화 신호 |
| 상태코드별 RPS | **어느 서비스가 뭘 반환하나**(200/4xx/5xx). ※ 실패 시 **gateway job도 꼭 확인**(요청이 거기서 걸러질 수 있음) |

+ Zipkin: 느린 요청의 어느 구간이 느린지.

---

## 5. 결과 기록 & 정리

- **공유 시트**에 자기 행 기록: `날짜 / 담당 / 서비스 / 시나리오 / VU / RPS / p95 / p99 / 에러율 / 병목 / 개선가설`
  - 뽑는 곳(k6 요약): `http_reqs`=RPS, `http_req_duration` p95/p99, `http_req_failed`=에러율
- **결과 JSON(`result_*.json`)은 git에 커밋 X** → `.gitignore`에 `k6/result_*.json`
- **테스트 유저 누적 정리**: 측정 세션 전/후 `docker compose down -v`(DB 초기화) 또는 정리 쿼리

---

## 6. 스케줄 (충돌 방지)

공유 환경이면 **한 번에 한 명만** 부하(동시에 돌리면 결과 오염). 시간 슬롯 배정 또는 각자 로컬(단, 절대치 비교 X, **개선 전/후 상대 비교**).

---

## 7. 자주 나오는 실무 팁

- **IntelliJ 빨간줄**: `import "k6/http"`, `__ENV`, `__VU` 등의 빨간줄은 **IntelliJ가 k6를 몰라서** 그런 것. **k6는 정상 실행**되니 무시.
- **파일 위치**: 모든 `*.js`는 `k6/` 한 폴더에. `./common.js` import 때문에 흩어지면 안 됨.
- **order 먼저 소량 검증**: `PEAK=5`로 `주문생성 2xx / 결제 2xx` 뜨는지 보고 본 부하.

---

## 8. 특수 서비스 (chat / cs)

- **chat**: WebSocket 기반이라 HTTP k6가 아니라 **`k6/ws`** 로 별도 작성 필요(핸드셰이크→메시지). 필요 시 담당과 별도 설계.
- **cs**: LLM(RAG) 응답이라 **느리고 비용**이 커서 일반 부하 대상 아님. 소량 동시성 확인 정도만.
- **aiops**: 내부 알림 웹훅이라 사용자 부하 대상 아님(장애 유발로 트리거하는 용도).

---

## 9. SLO ↔ 스크립트 ↔ 담당 (요약)

| SLO | 스크립트 | 담당 |
|---|---|---|
| SLO-1·2 예매 p99·성공률 | ticketing-loadtest.js | ticketing + 준영 |
| SLO-3 오버부킹 0 | (동시성 + 커스텀 카운터, 별도) | ticketing(조아영) + 준영 |
| SLO-4 결제 | order-loadtest.js | order |
| SLO-5 피드 p99 | feed-loadtest.js | feed |
| SLO-6 가용성 | (부하 중 `up` 관찰) | 준영 |
| SLO-7 로그인 | user-loadtest.js | user/auth |

> 원칙: **내 서비스 SLO는 내가 측정, 병목도 내가 개선. 재측정·취합은 준영.**
