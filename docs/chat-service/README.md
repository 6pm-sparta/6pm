# Chat Service 설계

Chat Service의 책임, 도메인 모델, 실시간 채팅 흐름, 전송 정책, Redis/이벤트 연동을 관심사별 문서로 나눈 설계 문서 모음이다.

## 문서 목록

| 문서 | 내용 |
| --- | --- |
| [architecture.md](architecture.md) | 도메인 모델, Repository 계층 구조, 삭제 정책, 예외 및 응답 정책, 설정 레퍼런스 |
| [flows.md](flows.md) | 채팅방 생성/멤버 관리, 실시간 메시지 전송, 메시지 전달(Delivery), 채팅 내역 조회 |
| [realtime.md](realtime.md) | WebSocket/STOMP 구성(브로커 프리픽스, 구독/발행 경로), 전송 제한 정책 |
| [redis.md](redis.md) | Redis 사용(멤버 캐시, presence, 슬로우/도배) |
| [events.md](events.md) | 이벤트 연동, Kafka 운영 계약 |

## 빠른 참조

- **엔드포인트/경로**: 내역 `GET /api/v1/chats/rooms/{roomId}/messages` · 방 목록 `GET /api/v1/chats/rooms` / STOMP 발행 `/app/rooms/{roomId}/messages` · 구독 `/topic/room.{roomId}`, `/user/queue/messages`, `/user/queue/errors`
- **Kafka**: 소비 `user.creator-created`·`user.followed`·`user.unfollowed`·`user.deleted` / 발행 `notification.send`
- **설정**: `chat.message-control.*`, `chat.notification.chunk-size`

## 문서 목적

이 문서는 Chat Service의 책임, 도메인 모델, 채팅방 생성/멤버 관리 흐름, 실시간 메시지 전송/조회, 전송 제한 정책, Redis 캐시/접속 상태, 이벤트 연동, repository 계층 구조를 정리한다.

## Chat Service 책임 범위

Chat Service는 크리에이터-팬 실시간 채팅 도메인을 담당한다.

주요 책임은 다음과 같다.

- 크리에이터 채팅방 자동 생성 (크리에이터 가입 이벤트 소비)
- 팔로우/언팔로우에 따른 방 멤버 관리
- 실시간 메시지 전송 (WebSocket/STOMP)
- 채팅 내역 조회 (커서 페이징)
- 전송 제한(길이/슬로우 모드/도배 억제)
- 접속 중이 아닌 사용자에 대한 푸시 알림 트리거 발행
- 회원 탈퇴 시 방/메시지 정리

방 하나는 크리에이터 1명 소유이며, 그 크리에이터를 팔로우한 팬들이 멤버가 된다.