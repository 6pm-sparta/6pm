package com.fandom.chat_service.infra.repository;

import com.fandom.chat_service.domain.entity.ChatMessage;
import com.fandom.chat_service.domain.entity.SenderRole;
import com.fandom.chat_service.domain.repository.ChatMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(ChatMessageRepositoryImpl.class)
@DisplayName("ChatMessageRepository 커스텀 쿼리 테스트")
class ChatMessageRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private ChatMessageRepository messageRepository;
    @Autowired
    private ChatMessageJpaRepository jpaRepository;

    private final UUID ROOM = UUID.randomUUID();
    private final UUID OTHER_ROOM = UUID.randomUUID();
    private final UUID CREATOR = UUID.randomUUID();
    private final UUID FAN_A = UUID.randomUUID();
    private final UUID FAN_B = UUID.randomUUID();

    private ChatMessage save(UUID roomId, UUID sender, SenderRole role, String content) {
        return jpaRepository.saveAndFlush(ChatMessage.builder()
                .roomId(roomId).senderId(sender).senderRole(role)
                .senderNickname("닉").content(content).build());
    }

    @Test
    @DisplayName("팬 조회: 크리에이터 메시지 + 본인 메시지만 보이고 다른 팬 메시지는 제외된다")
    void findMessagesForFan_filtersCreatorAndSelf() {
        save(ROOM, CREATOR, SenderRole.CREATOR, "공지");
        save(ROOM, FAN_A, SenderRole.MEMBER, "팬A 답장");
        save(ROOM, FAN_B, SenderRole.MEMBER, "팬B 답장"); // 보이면 안 됨

        List<ChatMessage> result = messageRepository.findMessagesForFan(ROOM, FAN_A, null, 10);

        assertThat(result).extracting(ChatMessage::getContent)
                .containsExactlyInAnyOrder("공지", "팬A 답장");
    }

    @Test
    @DisplayName("팬 조회: 커서 이후(작은 id)만 내림차순으로 가져온다")
    void findMessagesForFan_cursorPaging() {
        ChatMessage m1 = save(ROOM, CREATOR, SenderRole.CREATOR, "1");
        ChatMessage m2 = save(ROOM, CREATOR, SenderRole.CREATOR, "2");
        ChatMessage m3 = save(ROOM, CREATOR, SenderRole.CREATOR, "3"); // 최신

        // m3 커서 - 과거(m2, m1)만, 최신순
        List<ChatMessage> result = messageRepository.findMessagesForFan(ROOM, FAN_A, m3.getId(), 10);

        assertThat(result).extracting(ChatMessage::getId).containsExactly(m2.getId(), m1.getId());
    }

    @Test
    @DisplayName("크리에이터 조회: 방 전체 메시지를 최신순으로, limit 만큼만 가져온다")
    void findMessages_creatorFullWithLimit() {
        save(ROOM, CREATOR, SenderRole.CREATOR, "1");
        save(ROOM, FAN_A, SenderRole.MEMBER, "2");
        ChatMessage newest = save(ROOM, FAN_B, SenderRole.MEMBER, "3");

        List<ChatMessage> result = messageRepository.findMessages(ROOM, null, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(newest.getId()); // 최신 먼저
    }

    @Test
    @DisplayName("방 소프트 삭제: 해당 방만 조회에서 사라지고, 행은 남아있다(다른 방 영향 없음)")
    void softDeleteAllByRoomId() {
        save(ROOM, CREATOR, SenderRole.CREATOR, "삭제대상1");
        save(ROOM, FAN_A, SenderRole.MEMBER, "삭제대상2");
        save(OTHER_ROOM, CREATOR, SenderRole.CREATOR, "유지");

        messageRepository.softDeleteAllByRoomId(ROOM, CREATOR);
        jpaRepository.flush();

        // @SQLRestriction(deleted_at IS NULL) 으로 조회에서 제외
        assertThat(messageRepository.findMessages(ROOM, null, 10)).isEmpty();
        // 다른 방은 유지
        assertThat(messageRepository.findMessages(OTHER_ROOM, null, 10)).hasSize(1);
        // soft delete
        assertThat(countSoftDeletedByRoom(ROOM)).isEqualTo(2);
    }

    @Autowired
    private jakarta.persistence.EntityManager em;

    private long countSoftDeletedByRoom(UUID roomId) {
        Object n = em.createNativeQuery(
                        "SELECT count(*) FROM chat_messages WHERE room_id = :rid AND deleted_at IS NOT NULL")
                .setParameter("rid", roomId)
                .getSingleResult();
        return ((Number) n).longValue();
    }
}
