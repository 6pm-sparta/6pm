package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Post;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class JpaPostRepositoryTest {
    @Autowired
    private JpaPostRepository jpaPostRepository;

    private List<Post> savedPosts;

    @BeforeEach
    void setUp() throws InterruptedException {
        Post post1 = Post.builder().content("첫번째 게시글").authorId(UUID.randomUUID()).build();
        jpaPostRepository.save(post1);
        Thread.sleep(10);

        Post post2 = Post.builder().content("두번째 게시글").authorId(UUID.randomUUID()).build();
        jpaPostRepository.save(post2);
        Thread.sleep(10);

        Post post3 = Post.builder().content("세번째 게시글").authorId(UUID.randomUUID()).build();
        jpaPostRepository.save(post3);

        savedPosts = List.of(post1, post2, post3);
    }

    @Nested
    @DisplayName("최신순 조회")
    class FindLatest {
        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("cursor 없음 - 전체 게시글 대상")
        void findLatestWithoutCursor() {
            // when
            List<Post> results = jpaPostRepository.findLatest(null, null, null, pageable);

            // then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getId()).isGreaterThan(results.get(1).getId());
        }

        @Test
        @DisplayName("cursor 있음 - cursor 이전 게시글 대상")
        void findLatestWithCursor() {
            // given
            List<UUID> sortedIds = savedPosts.stream().map(Post::getId).sorted().toList();
            UUID cursor = sortedIds.get(1);

            // when
            List<Post> results = jpaPostRepository.findLatest(cursor, null, null, pageable);

            // then
            assertThat(results).hasSize(1);
            results.forEach(post -> assertThat(post.getId()).isLessThan(cursor));
        }

        @Test
        @DisplayName("authorId 필터 조회")
        void findLatestWithAuthorId() {
            // given
            UUID targetAuthorId = savedPosts.getFirst().getAuthorId();

            // when
            List<Post> results = jpaPostRepository.findLatest(null, targetAuthorId, null, pageable);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getAuthorId()).isEqualTo(targetAuthorId);
        }

        @Test
        @DisplayName("keyword 필터 조회")
        void findLatestWithKeyword() {
            // when
            List<Post> results = jpaPostRepository.findLatest(null, null, "첫번째", pageable);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getContent()).contains("첫번째");
        }
    }

    @Nested
    @DisplayName("오래된순 조회")
    class FindOldest {
        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("cursor 없음 - 전체 게시글 대상")
        void findOldestWithoutCursor() {
            // when
            List<Post> results = jpaPostRepository.findOldest(null, null, null, pageable);

            // then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getId()).isLessThan(results.get(1).getId());
        }

        @Test
        @DisplayName("cursor 있음 - cursor 이후 게시글 대상")
        void findOldestWithCursor() {
            // given
            List<UUID> sortedIds = savedPosts.stream().map(Post::getId).sorted().toList();
            UUID cursor = sortedIds.get(1);

            // when
            List<Post> results = jpaPostRepository.findOldest(cursor, null, null, pageable);

            // then
            assertThat(results).hasSize(1);
            results.forEach(post -> assertThat(post.getId()).isGreaterThan(cursor));
        }
    }

    @Test
    @DisplayName("워밍업용 최신순 100개 조회")
    void findTopForWarm() {
        // when
        List<Post> results = jpaPostRepository.findTopForWarm(PageRequest.of(0, 100));

        // then
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getId()).isGreaterThan(results.get(1).getId());
    }

    @Test
    @DisplayName("워밍업용 오래된순 100개 조회")
    void findBottomForWarm() {
        // when
        List<Post> results = jpaPostRepository.findBottomForWarm(PageRequest.of(0, 100));

        // then
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getId()).isLessThan(results.get(1).getId());
    }
}