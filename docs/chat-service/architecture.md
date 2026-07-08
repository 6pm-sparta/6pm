# Chat Service 아키텍처

## 도메인 모델

### ChatRoom

`ChatRoom`은 크리에이터별 채팅방이다.

주요 속성:

- id
- creatorId
- title

`ChatRoom`은 soft delete 대상이다. 크리에이터당 하나의 방을 가진다(`creatorId` 기준 조회). `creator_id`에 unique 제약(`uq_chat_rooms_creator_id`)을 둬 1크리에이터 1방을 DB 레벨에서 보장한다.

### ChatRoomMember

`ChatRoomMember`는 방 참여자다.

| 필드 | 의미 |
| --- | --- |
| roomId | 참여 중인 방 |
| userId | 참여자 사용자 |
| nickname | 채팅 표시용 닉네임(가입/팔로우 시점 스냅샷) |

멤버는 팔로우로 추가되고 언팔로우로 제거된다(hard delete). `(room_id, user_id)`에 unique 제약(`uq_chat_room_members_room_user`)을 둬 중복 참여를 막는다.

### ChatMessage

`ChatMessage`는 전송된 메시지다.

주요 속성:

- id
- roomId
- senderId
- senderRole (CREATOR / MEMBER)
- senderNickname (전송 시점 닉네임 스냅샷)
- content

`ChatMessage`는 soft delete 대상이다. 조회는 `id` 기준 커서 페이징(최신순)을 사용하며, `(room_id, id)` 복합 인덱스(`idx_chat_messages_room_id_id`)로 커서 조회를 지원한다. `senderNickname`은 전송 시점의 멤버 닉네임을 스냅샷한 값이라 이후 닉네임이 바뀌어도 과거 메시지에는 반영되지 않는다.

### SenderRole

메시지 작성자 구분값이다.

| 값 | 의미 |
| --- | --- |
| CREATOR | 방 소유 크리에이터 |
| MEMBER | 팔로우한 팬 |

## Repository 계층 구조

domain 포트와 infrastructure 구현체로 분리한다.

```text
domain/repository
  ChatRoomRepository
  ChatRoomMemberRepository
  ChatMessageRepository

infra/repository
  ChatRoomJpaRepository / ChatRoomRepositoryImpl
  ChatRoomMemberJpaRepository / ChatRoomMemberRepositoryImpl
  ChatMessageJpaRepository / ChatMessageRepositoryImpl
```

`@Query`, `@Modifying` 등 Spring Data JPA 세부사항은 infrastructure 계층에서 담당한다.

## 삭제 정책

| 엔티티 | 삭제 정책 | 이유 |
| --- | --- | --- |
| ChatRoom | soft delete | 방 이력 보존 |
| ChatMessage | soft delete | 메시지 이력 보존 |
| ChatRoomMember | hard delete | 재참여(재팔로우) 가능해야 함 |

방 삭제 시 메시지는 soft delete, 멤버는 hard delete로 정리한다.

## 예외 및 응답 정책

공통 응답 형식 `ApiResponse`와 `CustomException`/`ErrorCode`를 사용한다.

주요 에러 코드:

- `ROOM_NOT_FOUND` (404)
- `CHAT_ACCESS_DENIED` (403)
- `CHAT_MESSAGE_TOO_LONG` (400)
- `CHAT_SLOW_MODE` (429)
- `CHAT_DUPLICATE_MESSAGE` (429)

## 설정 레퍼런스

| 프로퍼티 | 기본값 | 설명 |
| --- | --- | --- |
| `chat.message-control.max-length` | 500 | 메시지 최대 길이(자) |
| `chat.message-control.slow-mode-seconds` | 5 | 슬로우 모드 쿨다운(초) |
| `chat.message-control.dedupe-window-seconds` | 30 | 도배 감지 윈도(초) |
| `chat.notification.chunk-size` | 500 | 오프라인 알림 발행 청크 크기 |

하드코딩: 방 멤버 캐시 TTL 3600초 + 지터 0~600초.