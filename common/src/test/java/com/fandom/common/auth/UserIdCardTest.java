package com.fandom.common.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserIdCard unit tests")
class UserIdCardTest {

    @Test
    @DisplayName("isMe returns true when user id matches")
    void isMe_matches() {
        UUID userId = UUID.randomUUID();
        UserIdCard idCard = UserIdCard.of(userId, "MEMBER");

        assertThat(idCard.isMe(userId)).isTrue();
    }

    @Test
    @DisplayName("isMe returns false when user id differs")
    void isMe_differs() {
        UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");

        assertThat(idCard.isMe(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("isMe returns false when user id is null")
    void isMe_null() {
        UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");

        assertThat(idCard.isMe(null)).isFalse();
    }
}
