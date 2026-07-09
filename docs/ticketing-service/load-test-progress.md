# ticketing/order 부하테스트 진행상황 (SLO-1·2·3·4)

> 작성일: 2026-07-07 · 상태: 스크립트 작성 완료, 실행 검증은 인프라 이슈로 대기 중
> 관련 이슈: #308 · 브랜치: `test/308-ticketing-loadtest`
>
> **⚠️ 2026-07-09 기준 stale**: 아래 "다음 단계"는 이후 상당 부분 진행됨 — SLO-3 실행 검증 완료(2026-07-08),
> `ticketing_overbooking_total` 카운터 구현 완료, gateway ~600 req/s 포화 병목 규명 완료, dev ECS 환경도
> 배포 완료. 최신 진행상황은 project_ticketing_slo_loadtest 세션 기록 참고 — 이 문서는 스크립트 작성
> 당시 스냅샷으로 남겨두고 별도 갱신은 안 함.

## 목적

SLO 정의서 기준 예매 핵심경로(SLO-1·2)·오버부킹(SLO-3)·결제(SLO-4)의 k6 스크립트를 완성하고,
baseline 측정 및 병목 지점을 식별한다.

## 현재 막힌 지점 — config-server PAT 이슈

로컬에서 config-server가 `6pm-config` 깃 레포(git backend)를 못 읽어와서, `CONFIG_GIT_USERNAME`/
`CONFIG_GIT_TOKEN` 관련 PAT 문제로 추정됨. 이 때문에 gateway-service/auth-service가 `jwt.secret`
placeholder를 못 풀어서 기동 자체가 실패(`PlaceholderResolutionException`).

→ **gateway/auth 없이 ticketing-service/order-service를 직접 검증하는 방식으로 우회**했다.
gateway가 하던 일(JWT 검증 → `UserIdCard` 생성 → `X-Id-Card`/`X-Id-Card-Signature` 헤더 서명,
`HmacUtils.sign()` 참고)을 k6가 대신 수행한다(`k6/crypto`의 `hmac('sha256', secret, json, 'base64')`).
ticketing/order 두 서비스 다 `@CurrentIdCard`만 보고 JWT는 직접 안 보기 때문에 성립하는 우회다.

## SLO별 스크립트 현황

| SLO | 대상 | 파일 | 인증 방식 | 필요 서비스 | 성격 | 상태 |
| --- | --- | --- | --- | --- | --- | --- |
| SLO-1·2 (예매 핵심경로) | ticketing | `reservation.js`, `stress.js`, `ticketing-loadtest.js` | gateway JWT (`Authorization: Bearer`) | gateway+auth+user+ticketing | 부하(ramping-vus, PEAK까지) | 기존 완성, gateway 복구 전엔 실행 불가 |
| SLO-3 (오버부킹) | ticketing | `ticketing-direct-functional.js` | X-Id-Card 직접 서명 | **ticketing 단독** | 기능 검증(1 VU 1 iteration, Postman "05.대기열-토큰발급" 1~6번 재현) | 신규 작성·커밋 완료, 실행 미검증 |
| SLO-4 (결제) | order | `order-loadtest.js` | gateway JWT | gateway+auth+user+order | 부하(ramping-vus) | 기존 완성, gateway 복구 전엔 실행 불가 |
| SLO-4 (결제, 우회) | order | `order-direct-loadtest.js` | X-Id-Card 직접 서명 | **order 단독** | 부하(ramping-vus) — 로직은 `order-loadtest.js`와 동일, 인증만 교체 | 신규 작성 완료, 실행 미검증 |

## 알려진 갭

- **SLO-3 진짜 동시성 검증 아직 없음**: `ticketing-direct-functional.js`는 A 성공 → B 재선점 시도 실패를
  **순차**로 확인하는 수준이다. "같은 좌석에 여러 VU가 정확히 같은 시점에 hold" 하는 시나리오는 별도
  스크립트가 더 필요하다.
- ~~오버부킹 카운터 미구현~~ **(해소, 2026-07-08)**: 서버 측 `ticketing_overbooking_total`(Micrometer
  Counter)이 `SeatService.hold()`의 `SEAT_ALREADY_HELD` 분기에 구현 완료됨.
- **`X-Id-Card` 우회 경로는 gateway 자체의 영향(라우팅/CB/rate-limit)은 검증 못 함**: gateway가 복구되면
  기존 `*-loadtest.js`(gateway 경유 버전)로 다시 측정해서 이번 우회 버전과 비교해야 한다.

## 다음 단계

1. config-server PAT 이슈 확인(담당 확인 필요) → gateway 복구
2. 동시성 hold(같은 seatId, 여러 VU 동시) 스크립트 추가
3. `ticketing_overbooking_total` 카운터 구현 후 정식 SLO-3 검증
4. gateway 복구 후 기존 스크립트로 재측정, 우회 버전과 결과 비교

## 참고

- 관련 Postman 컬렉션: `postman/collections/05. 대기열-토큰발급`, `09. 동시성 충돌`
- HMAC 서명 로직 원본: `common/src/main/java/com/fandom/common/auth/HmacUtils.java`
- 팀 부하테스트 가이드: `docs/infra/README-부하테스트-팀가이드.md`
