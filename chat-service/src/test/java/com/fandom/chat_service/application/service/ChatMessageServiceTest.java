package com.fandom.chat_service.application.service;

import com.fandom.chat_service.domain.entity.ChatMessage;
import com.fandom.chat_service.domain.entity.ChatRoom;
import com.fandom.chat_service.domain.entity.ChatRoomMember;
import com.fandom.chat_service.domain.entity.SenderRole;
import com.fandom.chat_service.domain.exception.ChatErrorCode;
import com.fandom.chat_service.domain.repository.ChatMessageRepository;
import com.fandom.chat_service.domain.repository.ChatRoomMemberRepository;
import com.fandom.chat_service.domain.repository.ChatRoomRepository;
import com.fandom.chat_service.presentation.dto.response.MessageListResponse;
import com.fandom.chat_service.presentation.dto.response.MessageResponse;
import com.fandom.common.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatMessageService 단위 테스트")
class ChatMessageServiceTest {

    @Mock
    private ChatRoomRepository roomRepository;
    @Mock
    private ChatRoomMemberRepository memberRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private MessageDeliveryService messageDeliveryService;
    @Mock
    private MessagePolicy messagePolicy;

    @InjectMocks
    private ChatMessageService messageService;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID FAN_ID = UUID.randomUUID();

    @BeforeEach
    void initTx() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTx() {
        TransactionSynchronizationManager.clear();
    }

    private ChatRoom room(UUID creatorId) {
        ChatRoom room = ChatRoom.builder().creatorId(creatorId).title("방").build();
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return room;
    }

    private ChatRoomMember member(UUID userId, String nickname) {
        return ChatRoomMember.builder().roomId(ROOM_ID).userId(userId).nickname(nickname).build();
    }

    private ChatMessage message(UUID id, UUID senderId, SenderRole role) {
        ChatMessage m = ChatMessage.builder()
                .roomId(ROOM_ID).senderId(senderId).senderRole(role)
                .senderNickname("닉").content("내용").build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private void fireAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    // ---------- send ----------

    @Test
    @DisplayName("send: 크리에이터가 보내면 role=CREATOR로 저장하고 커밋 후 전달한다")
    void send_creator() {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(CREATOR_ID)));
        given(memberRepository.findByRoomIdAndUserId(ROOM_ID, CREATOR_ID))
                .willReturn(Optional.of(member(CREATOR_ID, "크리에이터")));
        given(messageRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        messageService.send(ROOM_ID, CREATOR_ID, "공지");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getSenderRole()).isEqualTo(SenderRole.CREATOR);
        assertThat(captor.getValue().getSenderNickname()).isEqualTo("크리에이터");

        verify(messageDeliveryService, never()).deliver(any(), any());
        fireAfterCommit();
        verify(messageDeliveryService).deliver(any(ChatRoom.class), any(MessageResponse.class));
    }

    @Test
    @DisplayName("send: 일반 멤버가 보내면 role=MEMBER로 저장한다")
    void send_member() {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(CREATOR_ID)));
        given(memberRepository.findByRoomIdAndUserId(ROOM_ID, FAN_ID))
                .willReturn(Optional.of(member(FAN_ID, "팬")));
        given(messageRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        messageService.send(ROOM_ID, FAN_ID, "답장");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getSenderRole()).isEqualTo(SenderRole.MEMBER);
    }

    @Test
    @DisplayName("send: 멤버가 아니면 CHAT_ACCESS_DENIED")
    void send_notMember_denied() {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(CREATOR_ID)));
        given(memberRepository.findByRoomIdAndUserId(ROOM_ID, FAN_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.send(ROOM_ID, FAN_ID, "내용"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.CHAT_ACCESS_DENIED);

        verify(messageRepository, never()).save(any());
    }

    // ---------- getMessages ----------

    @Test
    @DisplayName("getMessages: 방이 없으면 ROOM_NOT_FOUND")
    void getMessages_roomNotFound() {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessages(ROOM_ID, FAN_ID, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("getMessages: 멤버가 아니면 CHAT_ACCESS_DENIED")
    void getMessages_notMember_denied() {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(CREATOR_ID)));
        given(memberRepository.existsByRoomIdAndUserId(ROOM_ID, FAN_ID)).willReturn(false);

        assertThatThrownBy(() -> messageService.getMessages(ROOM_ID, FAN_ID, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.CHAT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("getMessages: 크리에이터는 전체 메시지 쿼리를 사용한다")
    void getMessages_creator_usesFullQuery() {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(CREATOR_ID)));
        given(memberRepository.existsByRoomIdAndUserId(ROOM_ID, CREATOR_ID)).willReturn(true);
        given(messageRepository.findMessages(eq(ROOM_ID), any(), eq(21)))
                .willReturn(List.of(message(UUID.randomUUID(), CREATOR_ID, SenderRole.CREATOR)));

        MessageListResponse res = messageService.getMessages(ROOM_ID, CREATOR_ID, null, 20);

        assertThat(res.messages()).hasSize(1);
        assertThat(res.hasNext()).isFalse();
        verify(messageRepository, never()).findMessagesForFan(any(), any(), any(), eq(21));
    }

    @Test
    @DisplayName("getMessages: 팬은 팬 전용 쿼리를 사용하고 hasNext/커서를 계산한다")
    void getMessages_fan_usesFanQueryAndPaging() {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(CREATOR_ID)));
        given(memberRepository.existsByRoomIdAndUserId(ROOM_ID, FAN_ID)).willReturn(true);

        UUID lastId = UUID.randomUUID();
        // limit=1 → limit+1=2건 반환되면 hasNext=true, 페이지는 1건
        given(messageRepository.findMessagesForFan(eq(ROOM_ID), eq(FAN_ID), any(), eq(2)))
                .willReturn(List.of(
                        message(lastId, FAN_ID, SenderRole.MEMBER),
                        message(UUID.randomUUID(), CREATOR_ID, SenderRole.CREATOR)));

        MessageListResponse res = messageService.getMessages(ROOM_ID, FAN_ID, null, 1);

        assertThat(res.messages()).hasSize(1);
        assertThat(res.hasNext()).isTrue();
        assertThat(res.nextCursor()).isEqualTo(lastId);
        verify(messageRepository, never()).findMessages(any(), any(), anyInt());
    }
}
