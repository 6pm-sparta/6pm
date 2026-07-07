package com.fandom.chat_service.infra.repository;

import com.fandom.chat_service.domain.entity.ChatRoomMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatRoomMemberRepository 커스텀 쿼리 테스트")
class ChatRoomMemberRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private ChatRoomMemberJpaRepository jpaRepository;

    private final UUID ROOM = UUID.randomUUID();
    private final UUID OTHER_ROOM = UUID.randomUUID();
    private final UUID USER_A = UUID.randomUUID();
    private final UUID USER_B = UUID.randomUUID();

    private void join(UUID roomId, UUID userId) {
        jpaRepository.saveAndFlush(ChatRoomMember.builder()
                .roomId(roomId).userId(userId).nickname("닉").build());
    }

    @Test
    @DisplayName("findUserIdsByRoomId: 해당 방의 userId만 projection으로 가져온다")
    void findUserIdsByRoomId_projection() {
        join(ROOM, USER_A);
        join(ROOM, USER_B);
        join(OTHER_ROOM, USER_A); // 다른 방은 제외돼야 함

        assertThat(jpaRepository.findUserIdsByRoomId(ROOM))
                .containsExactlyInAnyOrder(USER_A, USER_B);
    }

    @Test
    @DisplayName("existsByRoomIdAndUserId: 멤버 여부를 정확히 판별한다")
    void existsByRoomIdAndUserId() {
        join(ROOM, USER_A);

        assertThat(jpaRepository.existsByRoomIdAndUserId(ROOM, USER_A)).isTrue();
        assertThat(jpaRepository.existsByRoomIdAndUserId(ROOM, USER_B)).isFalse();
    }

    @Test
    @DisplayName("deleteByRoomIdAndUserId: 단일 멤버십만 제거한다")
    void deleteByRoomIdAndUserId() {
        join(ROOM, USER_A);
        join(ROOM, USER_B);

        jpaRepository.deleteByRoomIdAndUserId(ROOM, USER_A);
        jpaRepository.flush();

        assertThat(jpaRepository.findUserIdsByRoomId(ROOM)).containsExactly(USER_B);
    }

    @Test
    @DisplayName("deleteByRoomId: 방 멤버를 일괄 제거하고 다른 방은 유지한다")
    void deleteByRoomId() {
        join(ROOM, USER_A);
        join(ROOM, USER_B);
        join(OTHER_ROOM, USER_A);

        jpaRepository.deleteByRoomId(ROOM);
        jpaRepository.flush();

        assertThat(jpaRepository.findUserIdsByRoomId(ROOM)).isEmpty();
        assertThat(jpaRepository.findUserIdsByRoomId(OTHER_ROOM)).containsExactly(USER_A);
    }

    @Test
    @DisplayName("deleteByUserId: 유저의 모든 방 멤버십을 제거한다(탈퇴)")
    void deleteByUserId() {
        join(ROOM, USER_A);
        join(OTHER_ROOM, USER_A);
        join(ROOM, USER_B);

        jpaRepository.deleteByUserId(USER_A);
        jpaRepository.flush();

        assertThat(jpaRepository.findAllByUserId(USER_A)).isEmpty();
        assertThat(jpaRepository.findUserIdsByRoomId(ROOM)).containsExactly(USER_B);
    }
}
