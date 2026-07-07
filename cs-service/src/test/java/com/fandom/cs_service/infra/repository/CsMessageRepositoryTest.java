package com.fandom.cs_service.infra.repository;

import com.fandom.cs_service.domain.entity.CsMessage;
import com.fandom.cs_service.domain.entity.SenderRole;
import com.fandom.cs_service.domain.repository.CsMessageRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(CsMessageRepositoryImpl.class)
@DisplayName("CsMessageRepository 커스텀 쿼리 테스트")
class CsMessageRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private CsMessageRepository messageRepository;
    @Autowired
    private CsMessageJpaRepository jpaRepository;
    @Autowired
    private EntityManager em;

    private final UUID USER_A = UUID.randomUUID();
    private final UUID USER_B = UUID.randomUUID();

    private CsMessage save(UUID userId, SenderRole role, String content) {
        return jpaRepository.saveAndFlush(CsMessage.builder()
                .userId(userId).senderRole(role).content(content).build());
    }

    @Test
    @DisplayName("조회: 해당 유저 메시지를 최신순으로, limit 만큼만 가져온다")
    void findMessages_latestDescWithLimit() {
        save(USER_A, SenderRole.USER, "1");
        save(USER_A, SenderRole.AI, "2");
        CsMessage newest = save(USER_A, SenderRole.USER, "3");

        List<CsMessage> result = messageRepository.findMessages(USER_A, null, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(newest.getId()); // 최신 먼저
    }

    @Test
    @DisplayName("조회: 커서 이후(작은 id)만 내림차순으로 가져온다")
    void findMessages_cursorPaging() {
        CsMessage m1 = save(USER_A, SenderRole.USER, "1");
        CsMessage m2 = save(USER_A, SenderRole.AI, "2");
        CsMessage m3 = save(USER_A, SenderRole.USER, "3"); // 최신

        List<CsMessage> result = messageRepository.findMessages(USER_A, m3.getId(), 10);

        assertThat(result).extracting(CsMessage::getId).containsExactly(m2.getId(), m1.getId());
    }

    @Test
    @DisplayName("조회: 다른 유저의 메시지는 제외된다")
    void findMessages_excludesOtherUser() {
        save(USER_A, SenderRole.USER, "A의 메시지");
        save(USER_B, SenderRole.USER, "B의 메시지"); // 보이면 안 됨

        List<CsMessage> result = messageRepository.findMessages(USER_A, null, 10);

        assertThat(result).extracting(CsMessage::getContent).containsExactly("A의 메시지");
    }

    @Test
    @DisplayName("소프트 삭제: 해당 유저만 조회에서 사라지고 행은 남으며, 다른 유저는 영향 없다")
    void softDeleteAllByUserId() {
        save(USER_A, SenderRole.USER, "삭제대상1");
        save(USER_A, SenderRole.AI, "삭제대상2");
        save(USER_B, SenderRole.USER, "유지");

        messageRepository.softDeleteAllByUserId(USER_A);
        jpaRepository.flush();
        em.clear();

        // @SQLRestriction(deleted_at IS NULL) 으로 조회에서 제외
        assertThat(messageRepository.findMessages(USER_A, null, 10)).isEmpty();
        // 다른 유저는 유지
        assertThat(messageRepository.findMessages(USER_B, null, 10)).hasSize(1);
        // soft delete
        assertThat(countSoftDeleted(USER_A)).isEqualTo(2);
    }

    private long countSoftDeleted(UUID userId) {
        Object n = em.createNativeQuery(
                        "SELECT count(*) FROM cs_messages WHERE user_id = :uid AND deleted_at IS NOT NULL")
                .setParameter("uid", userId)
                .getSingleResult();
        return ((Number) n).longValue();
    }
}
