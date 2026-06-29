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
import org.springframework.test.util.ReflectionTestUtils;

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
    @DisplayName("커서 기반 조회")
    class FindByCursor {
        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("cursor 없음 - 전체 게시글 대상")
        void findByCursorWithout() {
            // when
            List<Post> results = jpaPostRepository.findByCursor(null, null, null, pageable);

            // then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getId()).isGreaterThan(results.get(1).getId());
        }

        @Test
        @DisplayName("cursor 있음 - cursor 이전 게시글 대상")
        void findByCursorWith() {
            // given
            List<UUID> sortedIds = savedPosts.stream().map(Post::getId).sorted().toList();
            UUID cursor = sortedIds.get(1);

            // when
            List<Post> results = jpaPostRepository.findByCursor(cursor, null, null, pageable);

            // then
            assertThat(results).hasSize(1);
            results.forEach(post -> assertThat(post.getId()).isLessThan(cursor));
        }

        @Test
        @DisplayName("authorId 필터 조회")
        void findByCursorWithAuthorId() {
            // given
            UUID targetAuthorId = savedPosts.getFirst().getAuthorId();

            // when
            List<Post> results = jpaPostRepository.findByCursor(null, targetAuthorId, null, pageable);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getAuthorId()).isEqualTo(targetAuthorId);
        }

        @Test
        @DisplayName("keyword 필터 조회")
        void findByCursorWithKeyword() {
            // when
            List<Post> results = jpaPostRepository.findByCursor(null, null, "첫번째", pageable);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getContent()).contains("첫번째");
        }
    }

    @Nested
    @DisplayName("워밍업용 100개 조회")
    class FindByCursorForWarm {
        @Test
        @DisplayName("authorId 없음 - 전체 게시글 대상")
        void findTopForWarmWithoutAuthorId() {
            // when
            List<Post> results = jpaPostRepository.findByCursorForWarm(null, PageRequest.of(0, 100));

            // then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getId()).isGreaterThan(results.get(1).getId());
        }

        @Test
        @DisplayName("authorId 있음 - 작성자 게시글 대상")
        void findTopForWarmWithAuthorId() {
            // given
            UUID authorId = UUID.randomUUID();
            Post post = Post.builder().content("첫번째 게시글").authorId(authorId).build();
            jpaPostRepository.save(post);

            // when
            List<Post> results = jpaPostRepository.findByCursorForWarm(authorId, PageRequest.of(0, 100));

            // then
            assertThat(results).hasSize(1);
        }
    }

    @Nested
    @DisplayName("댓글 수 1 증감")
    class XxCrementCommentCount {
        private Post post;

        @BeforeEach
        void setUp() {
            post = Post.builder().authorId(UUID.randomUUID()).content("내용").build();
            ReflectionTestUtils.setField(post, "commentCount", 5L);
            jpaPostRepository.save(post);
        }

        @Test
        @DisplayName("댓글 수 1 증가")
        void incrementCommentCount() {
            // when
            jpaPostRepository.incrementCommentCount(post.getId());

            // then
            Post updated = jpaPostRepository.findById(post.getId()).orElseThrow();
            assertThat(updated.getCommentCount()).isEqualTo(6L);
        }

        @Test
        @DisplayName("댓글 수 1 감소")
        void decrementCommentCount() {
            // when
            jpaPostRepository.decrementCommentCount(post.getId());

            // then
            Post updated = jpaPostRepository.findById(post.getId()).orElseThrow();
            assertThat(updated.getCommentCount()).isEqualTo(4L);
        }

        @Test
        @DisplayName("댓글 수가 0이면 0 미만으로 내려가지 않음")
        void decrementCommentCountMinZero() {
            // given
            ReflectionTestUtils.setField(post, "commentCount", 0L);
            jpaPostRepository.save(post);

            // when
            jpaPostRepository.decrementCommentCount(post.getId());

            // then
            Post updated = jpaPostRepository.findById(post.getId()).orElseThrow();
            assertThat(updated.getCommentCount()).isEqualTo(0L);
        }
    }
}