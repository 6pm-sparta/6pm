# API

## 기기 토큰 및 알림 설정

`UserNotificationTokenService`가 담당한다.

| 기능 | 설명 |
| --- | --- |
| 등록 | 같은 토큰이면 소유자/타입 갱신, 없으면 신규 저장 |
| 삭제 | 로그아웃/기기 해제 시 hard delete |
| 설정 변경 | `notified`(수신 on/off) 토글 |
| 설정 조회 | 현재 수신 설정 조회 |

발송 시에는 `notified = true`인 토큰만 대상으로 한다.

대표 API:

- `POST /api/v1/notifications/tokens`
- `DELETE /api/v1/notifications/tokens/{id}`
- `GET /api/v1/notifications/tokens/{id}/settings`
- `PATCH /api/v1/notifications/tokens/{id}/settings`

## 보관함(Inbox)

`NotificationInboxService`가 담당한다.

| 기능 | 설명 |
| --- | --- |
| 조회 | 커서 페이징(최신순, `limit + 1`로 hasNext 판정, size 최대 100 clamp) |
| 읽음 처리 | 소유권 검증 후 `read = true` |
| 비우기 | 사용자 알림 일괄 soft delete |

대표 API:

- `GET /api/v1/notifications`
- `PATCH /api/v1/notifications/{id}/read`
- `DELETE /api/v1/notifications`

본인 소유가 아닌 알림 접근은 거부한다.
