package com.fandom.chat_service.application.service;

import com.fandom.chat_service.domain.entity.ChatRoom;
import com.fandom.chat_service.domain.entity.ChatRoomMember;
import com.fandom.chat_service.domain.repository.ChatMessageRepository;
import com.fandom.chat_service.domain.repository.ChatRoomMemberRepository;
import com.fandom.chat_service.domain.repository.ChatRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatRoomCommandService 단위 테스트")
class ChatRoomCommandServiceTest {

    @Mock
    private ChatRoomRepository roomRepository;
    @Mock
    private ChatRoomMemberRepository memberRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private RoomMemberCacheService roomMemberCache;

    @InjectMocks
    private ChatRoomCommandService commandService;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID FOLLOWER_ID = UUID.randomUUID();

    private ChatRoom room() {
        ChatRoom room = ChatRoom.builder().creatorId(CREATOR_ID).title("방").build();
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return room;
    }

    // ---------- handleCreatorCreated ----------

    @Test
    @DisplayName("크리에이터 생성: 이미 방이 있으면 생성하지 않는다(멱등)")
    void handleCreatorCreated_alreadyExists_skip() {
        given(roomRepository.findByCreatorId(CREATOR_ID)).willReturn(Optional.of(room()));

        commandService.handleCreatorCreated(CREATOR_ID, "닉");

        verify(roomRepository, never()).save(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("크리에이터 생성: 신규면 방과 본인 멤버를 생성한다")
    void handleCreatorCreated_new_createsRoomAndMember() {
        given(roomRepository.findByCreatorId(CREATOR_ID)).willReturn(Optional.empty());
        given(roomRepository.save(any())).willReturn(room());

        commandService.handleCreatorCreated(CREATOR_ID, "닉");

        verify(roomRepository).save(any(ChatRoom.class));
        verify(memberRepository).save(any(ChatRoomMember.class));
    }

    // ---------- handleFollowed ----------

    @Test
    @DisplayName("팔로우: 이미 멤버면 추가하지 않는다(멱등)")
    void handleFollowed_alreadyMember_skip() {
        given(roomRepository.findByCreatorId(CREATOR_ID)).willReturn(Optional.of(room()));
        given(memberRepository.existsByRoomIdAndUserId(ROOM_ID, FOLLOWER_ID)).willReturn(true);

        commandService.handleFollowed(CREATOR_ID, FOLLOWER_ID, "팔로워");

        verify(memberRepository, never()).save(any());
        verify(roomMemberCache, never()).onMemberAdded(any(), any());
    }

    @Test
    @DisplayName("팔로우: 신규 멤버를 저장하고 캐시에 반영한다")
    void handleFollowed_new_savesAndCache() {
        given(roomRepository.findByCreatorId(CREATOR_ID)).willReturn(Optional.of(room()));
        given(memberRepository.existsByRoomIdAndUserId(ROOM_ID, FOLLOWER_ID)).willReturn(false);

        commandService.handleFollowed(CREATOR_ID, FOLLOWER_ID, "팔로워");

        verify(memberRepository).save(any(ChatRoomMember.class));
        // 트랜잭션 비활성(단위 테스트)이면 afterCommit 액션이 즉시 실행됨
        verify(roomMemberCache).onMemberAdded(ROOM_ID, FOLLOWER_ID);
    }

    // ---------- handleUnfollowed ----------

    @Test
    @DisplayName("언팔: 멤버를 삭제하고 캐시에서 제거한다")
    void handleUnfollowed_removesMemberAndCache() {
        given(roomRepository.findByCreatorId(CREATOR_ID)).willReturn(Optional.of(room()));

        commandService.handleUnfollowed(CREATOR_ID, FOLLOWER_ID);

        verify(memberRepository).deleteByRoomIdAndUserId(ROOM_ID, FOLLOWER_ID);
        verify(roomMemberCache).onMemberRemoved(ROOM_ID, FOLLOWER_ID);
    }

    // ---------- handleUserDeleted ----------

    @Test
    @DisplayName("탈퇴(크리에이터): 메시지 소프트삭제 → 멤버 삭제 → 방 소프트삭제 순서로 정리하고 캐시를 비운다")
    void handleUserDeleted_creator_cleansRoomInOrder() {
        ChatRoom room = room();
        // 탈퇴자가 멤버로 속한 다른 방
        UUID otherRoomId = UUID.randomUUID();
        ChatRoomMember membership = ChatRoomMember.builder()
                .roomId(otherRoomId).userId(CREATOR_ID).nickname("닉").build();

        given(memberRepository.findAllByUserId(CREATOR_ID)).willReturn(List.of(membership));
        given(roomRepository.findByCreatorId(CREATOR_ID)).willReturn(Optional.of(room));

        commandService.handleUserDeleted(CREATOR_ID);

        InOrder order = inOrder(messageRepository, memberRepository, roomRepository);
        order.verify(messageRepository).softDeleteAllByRoomId(ROOM_ID, CREATOR_ID);
        order.verify(memberRepository).deleteByRoomId(ROOM_ID);
        order.verify(roomRepository).save(room);

        verify(memberRepository).deleteByUserId(CREATOR_ID);
        verify(roomMemberCache).evictRoom(ROOM_ID);
        // 멤버로 속해있던 방의 캐시에서도 제거
        verify(roomMemberCache).onMemberRemoved(otherRoomId, CREATOR_ID);
    }

    @Test
    @DisplayName("탈퇴(일반 멤버): 본인 소유 방이 없으면 멤버십만 정리한다")
    void handleUserDeleted_member_onlyCleansMembership() {
        UUID userId = UUID.randomUUID();
        UUID joinedRoomId = UUID.randomUUID();
        ChatRoomMember membership = ChatRoomMember.builder()
                .roomId(joinedRoomId).userId(userId).nickname("닉").build();

        given(memberRepository.findAllByUserId(userId)).willReturn(List.of(membership));
        given(roomRepository.findByCreatorId(userId)).willReturn(Optional.empty());

        commandService.handleUserDeleted(userId);

        verify(memberRepository).deleteByUserId(userId);
        verify(roomMemberCache).onMemberRemoved(joinedRoomId, userId);
        // 소유 방 없음 -> 방 관련 정리는 호출되지 않음
        verify(messageRepository, never()).softDeleteAllByRoomId(any(), any());
        verify(roomRepository, never()).save(any());
        verify(roomMemberCache, never()).evictRoom(any());
    }
}
