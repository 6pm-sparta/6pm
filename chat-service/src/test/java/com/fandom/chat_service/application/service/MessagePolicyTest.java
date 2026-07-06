package com.fandom.chat_service.application.service;

import com.fandom.chat_service.application.port.MessageRateLimitPort;
import com.fandom.chat_service.domain.entity.ChatRoom;
import com.fandom.chat_service.domain.exception.ChatErrorCode;
import com.fandom.common.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessagePolicy 단위 테스트")
class MessagePolicyTest {

    @Mock
    private MessageRateLimitPort rateLimit;

    private MessagePolicy policy;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID FAN_ID = UUID.randomUUID();
    private static final int MAX_LENGTH = 10;

    @BeforeEach
    void setUp() {
        policy = new MessagePolicy(rateLimit, MAX_LENGTH);
    }

    private ChatRoom room() {
        ChatRoom room = ChatRoom.builder().creatorId(CREATOR_ID).title("방").build();
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return room;
    }

    private void allowAllRateLimits() {
        given(rateLimit.tryAcquireSlowMode(ROOM_ID, FAN_ID)).willReturn(true);
        given(rateLimit.isDuplicate(ROOM_ID, FAN_ID, "안녕")).willReturn(false);
    }

    @Test
    @DisplayName("길이 초과: 전원 CHAT_MESSAGE_TOO_LONG (레이트리밋 검사 안 함)")
    void tooLong() {
        assertThatThrownBy(() -> policy.check(room(), FAN_ID, "12345678901")) // 11자 > 10
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.CHAT_MESSAGE_TOO_LONG);

        verifyNoInteractions(rateLimit);
    }

    @Test
    @DisplayName("크리에이터: 길이만 통과하면 레이트리밋 검사 없이 통과")
    void creator_bypassesRateLimits() {
        assertThatCode(() -> policy.check(room(), CREATOR_ID, "공지사항")).doesNotThrowAnyException();

        verifyNoInteractions(rateLimit);
    }

    @Test
    @DisplayName("멤버: 모든 제한 통과 시 정상")
    void member_allPass() {
        allowAllRateLimits();

        assertThatCode(() -> policy.check(room(), FAN_ID, "안녕")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("멤버: 슬로우 모드 걸리면 CHAT_SLOW_MODE, 도배 검사는 안 함")
    void member_slowMode() {
        given(rateLimit.tryAcquireSlowMode(ROOM_ID, FAN_ID)).willReturn(false);

        assertThatThrownBy(() -> policy.check(room(), FAN_ID, "안녕"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.CHAT_SLOW_MODE);

        verify(rateLimit, never()).isDuplicate(ROOM_ID, FAN_ID, "안녕");
    }

    @Test
    @DisplayName("멤버: 직전과 동일 메시지면 CHAT_DUPLICATE_MESSAGE")
    void member_duplicate() {
        given(rateLimit.tryAcquireSlowMode(ROOM_ID, FAN_ID)).willReturn(true);
        given(rateLimit.isDuplicate(ROOM_ID, FAN_ID, "안녕")).willReturn(true);

        assertThatThrownBy(() -> policy.check(room(), FAN_ID, "안녕"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.CHAT_DUPLICATE_MESSAGE);
    }
}
