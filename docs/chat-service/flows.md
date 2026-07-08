# Chat Service 흐름

## 채팅방 생성 및 멤버 관리

Chat Service는 사용자 도메인 이벤트를 소비해 방과 멤버를 관리한다.

| 이벤트 | 처리 |
| --- | --- |
| `user.creator-created` | 크리에이터 방 생성(멱등: 이미 있으면 skip), 크리에이터 본인 멤버 등록 |
| `user.followed` | 방 멤버 추가(멱등), 멤버 캐시 반영 |
| `user.unfollowed` | 방 멤버 제거, 멤버 캐시에서 제거 |
| `user.deleted` | 소유 방 메시지 soft delete → 멤버 삭제 → 방 soft delete, 본인 멤버십 정리, 캐시 정리 |

멤버 추가/제거 시 방 멤버 캐시 동기화는 트랜잭션 커밋 이후에 수행한다.

## 실시간 메시지 전송

전송은 WebSocket/STOMP로 처리한다.

```text
Client (STOMP)
  -> ChatStompController (@MessageMapping)
  -> ChatMessageService.send
  -> 전송 정책 검증
  -> 메시지 저장
  -> (커밋 후) MessageDeliveryService.deliver
```

처리 흐름:

1. 방 존재 확인
2. 멤버십 검증(비멤버는 접근 거부)
3. 전송 정책 검증(길이/슬로우/도배)
4. 역할 판정(방 소유자면 CREATOR, 아니면 MEMBER)
5. 메시지 저장(닉네임 스냅샷 포함)
6. 커밋 후 전달(afterCommit)

외부 전달(브로드캐스트/알림 발행)은 트랜잭션 커밋 이후에 실행해, DB 트랜잭션이 외부 I/O를 붙잡지 않도록 한다.

REST 엔드포인트(`POST /api/v1/chats/rooms/{roomId}/messages`)도 있으나 이는 테스트용이며, 실시간 전송은 STOMP로 대체되었다.

## 메시지 전달(Delivery)

`MessageDeliveryService`는 저장된 메시지를 참여자에게 전달한다. 보낸 사람의 역할에 따라 전달 경로가 다르다.

- **크리에이터 메시지**: 방 토픽(`/topic/room.{roomId}`)으로 1회 브로드캐스트한다. 접속 중이 아닌 팬에게는 푸시 알림 발행을 위해 Kafka로 알림 전송 메시지를 발행한다.
- **팬 메시지**: 브로드캐스트하지 않는다. 보낸 팬 본인에게 에코하고(`/user/queue/messages`), 크리에이터가 접속 중이면 크리에이터 개인 큐로만 전달한다. 이로써 flows.md의 채팅 내역 조회의 "팬은 크리에이터 메시지와 본인 메시지만 본다" 정책이 전달 단계에서도 유지된다.
- 알림 대상(오프라인 팬)이 많을 경우 대상 목록을 청크 단위로 나눠 발행한다(`chat.notification.chunk-size`, 기본 500). 청킹은 어댑터에서 수행하고 청크마다 `notification.send`를 1건씩 발행한다.

접속 상태는 Redis 기반 presence로 판단한다(STOMP 연결/해제 시 갱신).

## 채팅 내역 조회

조회는 커서 페이징(최신순, `limit + 1`로 hasNext 판정)을 사용한다. 커서는 직전 페이지 마지막 메시지의 `id`이고, size는 미지정 시 20 / 최대 100으로 clamp된다.

역할별로 보이는 범위가 다르다.

| 요청자 | 조회 범위 |
| --- | --- |
| CREATOR | 방 전체 메시지 |
| MEMBER(팬) | 크리에이터 메시지 + 본인 메시지 (다른 팬 메시지 제외) |

대표 API:

- `GET /api/v1/chats/rooms/{roomId}/messages`
- `GET /api/v1/chats/rooms`  (참여 중인 방 목록)

비멤버 조회는 접근 거부로 처리한다.
