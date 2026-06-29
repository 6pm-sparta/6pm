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

    @Test
    @DisplayName("isMember는 role이 MEMBER일 때만 true")
    void isMember() {
        assertThat(UserIdCard.of(UUID.randomUUID(), "MEMBER").isMember()).isTrue();
        assertThat(UserIdCard.of(UUID.randomUUID(), "CREATOR").isMember()).isFalse();
    }

    @Test
    @DisplayName("isCreator는 role이 CREATOR일 때만 true")
    void isCreator() {
        assertThat(UserIdCard.of(UUID.randomUUID(), "CREATOR").isCreator()).isTrue();
        assertThat(UserIdCard.of(UUID.randomUUID(), "MEMBER").isCreator()).isFalse();
    }

    @Test
    @DisplayName("isMaster는 role이 MASTER일 때만 true")
    void isMaster() {
        assertThat(UserIdCard.of(UUID.randomUUID(), "MASTER").isMaster()).isTrue();
        assertThat(UserIdCard.of(UUID.randomUUID(), "MEMBER").isMaster()).isFalse();
    }

    @Test
    @DisplayName("role이 null이어도 role 판별 메서드는 NPE 없이 false")
    void roleChecks_nullSafe() {
        UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), null);

        assertThat(idCard.isMember()).isFalse();
        assertThat(idCard.isCreator()).isFalse();
        assertThat(idCard.isMaster()).isFalse();
    }
}
