package com.fandom.chat_service.application.service;

import com.fandom.chat_service.application.port.ChatNotificationPort;
import com.fandom.chat_service.application.port.OnlineStatePort;
import com.fandom.chat_service.domain.entity.ChatRoom;
import com.fandom.chat_service.domain.entity.SenderRole;
import com.fandom.chat_service.presentation.dto.response.MessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageDeliveryService 단위 테스트")
class MessageDeliveryServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private OnlineStatePort onlineState;
    @Mock
    private RoomMemberCacheService roomMemberCache;
    @Mock
    private ChatNotificationPort chatNotificationPort;

    @InjectMocks
    private MessageDeliveryService deliveryService;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();

    private ChatRoom room() {
        ChatRoom room = ChatRoom.builder().creatorId(CREATOR_ID).title("방제목").build();
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return room;
    }

    @Test
    @DisplayName("크리에이터 메시지: 방 토픽 1회 발행 + 오프라인 팬에게만 알림")
    void deliver_creator_broadcastAndNotifyOffline() {
        UUID onlineFan = UUID.randomUUID();
        UUID offlineFan = UUID.randomUUID();
        MessageResponse msg = new MessageResponse(
                UUID.randomUUID(), CREATOR_ID, SenderRole.CREATOR, "크리에이터", "공지");

        given(roomMemberCache.getFans(ROOM_ID, CREATOR_ID)).willReturn(Set.of(onlineFan, offlineFan));
        given(onlineState.filterOnline(any())).willReturn(List.of(onlineFan));

        deliveryService.deliver(room(), msg);

        // 방 토픽으로 1회 브로드캐스트
        verify(messagingTemplate).convertAndSend("/topic/room." + ROOM_ID, msg);

        // 오프라인 팬만 알림 대상
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatNotificationPort).notifyNewMessage(eq(msg.id()), eq("방제목"), eq("공지"), captor.capture());
        assertThat(captor.getValue()).containsExactly(offlineFan);
    }

    @Test
    @DisplayName("크리에이터 메시지: 팬이 없으면 알림을 발행하지 않는다")
    void deliver_creator_noFans_noNotify() {
        MessageResponse msg = new MessageResponse(
                UUID.randomUUID(), CREATOR_ID, SenderRole.CREATOR, "크리에이터", "공지");
        given(roomMemberCache.getFans(ROOM_ID, CREATOR_ID)).willReturn(Set.of());

        deliveryService.deliver(room(), msg);

        verify(messagingTemplate).convertAndSend("/topic/room." + ROOM_ID, msg);
        verify(chatNotificationPort, never()).notifyNewMessage(any(), any(), any(), anyList());
    }

    @Test
    @DisplayName("팬 메시지: 본인에게 에코 + 온라인 크리에이터에게 전달")
    void deliver_member_echoAndReplyToOnlineCreator() {
        UUID fanId = UUID.randomUUID();
        MessageResponse msg = new MessageResponse(
                UUID.randomUUID(), fanId, SenderRole.MEMBER, "팬", "답장");
        given(onlineState.isOnline(CREATOR_ID)).willReturn(true);

        deliveryService.deliver(room(), msg);

        verify(messagingTemplate).convertAndSendToUser(fanId.toString(), "/queue/messages", msg);
        verify(messagingTemplate).convertAndSendToUser(CREATOR_ID.toString(), "/queue/messages", msg);
    }

    @Test
    @DisplayName("팬 메시지: 크리에이터가 오프라인이면 크리에이터 전달은 생략")
    void deliver_member_offlineCreator_noReply() {
        UUID fanId = UUID.randomUUID();
        MessageResponse msg = new MessageResponse(
                UUID.randomUUID(), fanId, SenderRole.MEMBER, "팬", "답장");
        given(onlineState.isOnline(CREATOR_ID)).willReturn(false);

        deliveryService.deliver(room(), msg);

        verify(messagingTemplate).convertAndSendToUser(fanId.toString(), "/queue/messages", msg);
        verify(messagingTemplate, never()).convertAndSendToUser(eq(CREATOR_ID.toString()), any(), any());
    }

    @Test
    @DisplayName("전달 중 예외가 나도 밖으로 던지지 않는다")
    void deliver_swallowsException() {
        MessageResponse msg = new MessageResponse(
                UUID.randomUUID(), CREATOR_ID, SenderRole.CREATOR, "크리에이터", "공지");
        doThrow(new RuntimeException("boom"))
                .when(messagingTemplate).convertAndSend(any(String.class), any(Object.class));

        assertThatCode(() -> deliveryService.deliver(room(), msg)).doesNotThrowAnyException();
    }
}
