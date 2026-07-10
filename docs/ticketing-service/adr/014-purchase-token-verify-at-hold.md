# ADR 014 — 구매 토큰 검증은 Gateway가 아니라 hold() 시점에 수행한다

**날짜**: 2026-07-10 (결정 자체는 2026-06-23, README에 흩어져 있던 기록을 소급 정리)
**상태**: 확정 (운영 중)

---

## 배경

초기 설계는 Gateway가 좌석 선택 화면 진입 시점에 `purchase-token`을 검증하는 것이었다. 실제 구현 과정에서 검증 주체와 시점이 바뀌었는데, 이 변경이 README 본문 주석으로만 남아 있었다.

---

## 결정

- 구매 토큰(`purchase-token:{showId}:{userId}`) 검증은 **`SeatService.hold()` 진입 시점**에 수행한다. 없으면 `PURCHASE_TOKEN_NOT_FOUND`(403)로 즉시 거부.
- **좌석 목록 조회(`GET .../seats`)는 토큰 없이도 가능**하다 — 검증은 Hold 시점에만 이루어진다.
- 토큰은 Hold 성공 여부와 무관하게 TTL(600초)로만 만료되며, 별도 삭제 로직은 없다.

---

## 이유

- 좌석 목록은 대기열 통과 전에도 보여줄 수 있는 읽기 전용 정보라 토큰으로 막을 필요가 없다.
- 실제로 자원을 점유하는 행위(hold)에서 검증하는 것이 우회 여지가 없다 — Gateway 검증만 있으면 서비스 직접 호출 경로가 뚫린다.
- 토큰 검증에 쓰는 showId는 클라이언트 path 값이 아니라 **DB에서 로드한 좌석의 `seat.getShowId()`**를 사용한다. path showId를 믿으면 다른 공연 토큰으로 이 공연 좌석을 선점하는 우회가 가능하기 때문.

---

## 관련

- [260623 대기열 토큰.md](../archive/260623%20대기열%20토큰.md) — 당시 결정 배경 상세
- [ticketing-system-change-log.md](../archive/ticketing-system-change-log.md) — 변경 이력
- [README §5 구간 2](../README.md#5-해피패스-플로우) — 현재 흐름 요약
