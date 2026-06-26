package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Like;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class JpaLikeRepositoryTest {
    @Autowired
    private JpaLikeRepository jpaLikeRepository;

    @Test
    @DisplayName("여러 postId로 좋아요 사용자 목록 조회")
    void findLikeUsersByPostIds() {
        // Given
        UUID postId1 = UUID.randomUUID();
        UUID postId2 = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        jpaLikeRepository.saveAll(List.of(
                Like.builder().postId(postId1).userId(userId1).build(),
                Like.builder().postId(postId1).userId(userId2).build(),
                Like.builder().postId(postId2).userId(userId1).build()
        ));

        // When
        List<Object[]> result = jpaLikeRepository.findLikeUsersByPostIds(List.of(postId1, postId2));

        // Then
        assertThat(result).hasSize(3);
    }
}