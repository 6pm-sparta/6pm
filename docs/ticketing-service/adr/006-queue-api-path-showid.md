# ADR 006 — 대기열 API 경로에 showId 포함

**날짜**: 2026-06-18
**상태**: 확정 (단, 이후 경로가 한 번 더 바뀜 — 아래 "후속 변경" 참고)

---

## 배경

여러 공연이 동시에 대기열을 운영할 수 있는데, 초기 경로(`/queue/enter` 등)는 어느 공연의 대기열인지 구분할 수 없었다.

---

## 결정

- `/queue/enter` → `/queue/shows/{showId}/enter`
- `/queue/status` → `/queue/shows/{showId}/status`
- `/queue/stream` → `/queue/shows/{showId}/stream`

---

## 이유

showId 없이는 어느 공연 대기열인지 식별이 불가능하다.

---

## 후속 변경 (원 기록 없어 코드 기준으로 소급 기록)

이후 경로가 다시 한번 바뀌어, 현재(`QueueController.java`) 기준으로는 다음과 같다.

- `POST /api/v1/tickets/shows/{showId}/queue`
- `GET /api/v1/tickets/shows/{showId}/queue/status`
- `GET /api/v1/tickets/shows/{showId}/queue/stream`

이 재변경이 언제/왜 있었는지는 기록이 남아있지 않다. 상세는 [archive/CHANGELOG.md](../archive/CHANGELOG.md) "API 경로 재변경" 항목 참고.
