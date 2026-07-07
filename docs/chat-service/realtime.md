# Chat Service 실시간 (WebSocket / STOMP)

## WebSocket / STOMP 구성

- 핸드셰이크 단계에서 IdCard로 사용자 인증을 수행한다.
- 구독 경로에 대한 인가 인터셉터로 방 접근 권한을 검증한다.
- STOMP 예외는 사용자 개인 큐(`/queue/errors`)로 전달한다.

### 브로커 프리픽스

| 프리픽스 | 방향 | 용도 |
| --- | --- | --- |
| `/app` | client → server | 서버 핸들러(`@MessageMapping`)로 라우팅 |
| `/topic` | server → client | 방 단위 브로드캐스트 구독 |
| `/queue` | server → client | 개인 큐 구독 |
| `/user` | server → client | 개인 큐 라우팅 프리픽스(`/user/queue/*`를 해당 사용자에게만 전달) |

`/queue`, `/topic`은 내장 SimpleBroker가 처리한다.

### 구독 / 발행 경로

발행(클라이언트 → 서버):

| 목적지 | 용도 |
| --- | --- |
| `/app/rooms/{roomId}/messages` | 메시지 전송(`ChatStompController.send`) |

구독(서버 → 클라이언트): 클라이언트는 방 입장 시 아래 3개를 구독한다.

| 목적지 | 받는 내용 | 구독 주체 |
| --- | --- | --- |
| `/topic/room.{roomId}` | 크리에이터 메시지 브로드캐스트 | 방 멤버(구독 시 멤버십 검증) |
| `/user/queue/messages` | 개인 전달분 — 팬: 본인이 보낸 메시지 에코 / 크리에이터: 팬 답장 | 각 사용자 |
| `/user/queue/errors` | 전송 정책 위반 등 개인 오류 | 각 사용자 |

방 토픽(`/topic/room.{roomId}`) 구독은 `TopicSubscriptionAuthInterceptor`가 방 멤버십을 검증하고, 비멤버 구독은 거부한다. 개인 큐(`/user/queue/*`)는 `setUserDestinationPrefix("/user")`로 사용자별로 격리된다.

## 전송 제한 정책 (Message Control)

메시지 전송 시 `MessagePolicy`가 저장 직전에 정책을 검증한다. 위반 시 `CustomException`을 던지고, STOMP 예외 핸들러가 `/queue/errors`로 사유를 전달한다.

| 제한 | 내용 | 기본값 | 적용 대상 |
| --- | --- | --- | --- |
| 길이 제한 | 메시지 최대 길이 초과 차단 | 500자 | 전원 |
| 슬로우 모드 | 전송 간 쿨다운 | 5초에 1개 | MEMBER |
| 도배 억제 | 직전과 동일한 내용 반복 차단 | 30초 내 동일 내용 | MEMBER |

- CREATOR는 슬로우/도배 예외이며 길이 제한만 적용한다.
- 검증 순서: 길이 → 슬로우 → 도배 (앞 단계에서 걸리면 뒤 검사는 하지 않는다).
- 슬로우/도배는 Redis 키(`chat:slow:*`, `chat:last:*`)와 TTL로 구현한다.
- 모든 값은 `chat.message-control.*` 설정으로 재빌드 없이 조정한다.

관련 에러 코드: `CHAT_MESSAGE_TOO_LONG`(400), `CHAT_SLOW_MODE`(429), `CHAT_DUPLICATE_MESSAGE`(429).
