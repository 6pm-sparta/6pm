package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Comment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DataJpaTest
class JpaCommentRepositoryTest {
    @Autowired
    private JpaCommentRepository jpaCommentRepository;

    private List<Comment> comments;
    private UUID postId;
    private UUID authorId;

    @BeforeEach
    void setUp() throws InterruptedException {
        postId = UUID.randomUUID();
        authorId = UUID.randomUUID();

        Comment comment1 = Comment.builder().postId(postId).authorId(authorId).content("첫번째 댓글").build();
        jpaCommentRepository.save(comment1);
        Thread.sleep(10);

        Comment comment2 = Comment.builder().postId(postId).authorId(UUID.randomUUID()).content("두번째 댓글").build();
        jpaCommentRepository.save(comment2);
        Thread.sleep(10);

        Comment comment3 = Comment.builder().postId(postId).authorId(UUID.randomUUID()).content("세번째 댓글").build();
        jpaCommentRepository.save(comment3);

        comments = List.of(comment1, comment2, comment3);
    }

    @Nested
    @DisplayName("게시글 댓글 최신순 조회")
    class FindLatestByPostId {
        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("cursor 없음 - 전체 댓글 대상")
        void findLatestByPostIdWithoutCursor() {
            // when
            List<Comment> results = jpaCommentRepository.findLatestByPostId(null, postId, pageable);

            // then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getId()).isGreaterThan(results.get(1).getId());
        }

        @Test
        @DisplayName("cursor 있음 - cursor 이전 댓글 대상")
        void findLatestByPostIdWithCursor() {
            // given
            List<UUID> sortedIds = comments.stream().map(Comment::getId).sorted().toList();
            UUID cursor = sortedIds.get(1);

            // when
            List<Comment> results = jpaCommentRepository.findLatestByPostId(cursor, postId, pageable);

            // then
            assertThat(results).hasSize(1);
            results.forEach(comment -> assertThat(comment.getId()).isLessThan(cursor));
        }

        @Test
        @DisplayName("다른 게시글 댓글은 조회되지 않음")
        void findLatestByPostIdNotIncludeOtherPost() {
            // given
            UUID anotherPostId = UUID.randomUUID();

            // when
            List<Comment> results = jpaCommentRepository.findLatestByPostId(anotherPostId, null, pageable);

            // then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("게시글 댓글 오래된순 조회")
    class FindOldestByPostId {
        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("cursor 없음 - 전체 댓글 대상")
        void findOldestByPostIdWithoutCursor() {
            // when
            List<Comment> results = jpaCommentRepository.findOldestByPostId(null, postId, pageable);

            // then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getId()).isLessThan(results.get(1).getId());
        }

        @Test
        @DisplayName("cursor 있음 - cursor 이후 댓글 대상")
        void findOldestByPostIdWithCursor() {
            // given
            List<UUID> sortedIds = comments.stream().map(Comment::getId).sorted().toList();
            UUID cursor = sortedIds.get(1);

            // when
            List<Comment> results = jpaCommentRepository.findOldestByPostId(cursor, postId, pageable);

            // then
            assertThat(results).hasSize(1);
            results.forEach(comment -> assertThat(comment.getId()).isGreaterThan(cursor));
        }
    }

    @Nested
    @DisplayName("사용자 댓글 최신순 조회")
    class FindLatestByAuthorId {
        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("cursor 없음 - 전체 댓글 대상")
        void findLatestByAuthorIdWithoutCursor() {
            // when
            List<Comment> results = jpaCommentRepository.findLatestByAuthorId(null, authorId, pageable);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getAuthorId()).isEqualTo(authorId);
        }

        @Test
        @DisplayName("cursor 있음 - cursor 이전 댓글 대상")
        void findLatestByAuthorIdWithCursor() {
            // given
            Comment comment4 = Comment.builder().postId(postId).authorId(authorId).content("네번째 댓글").build();
            jpaCommentRepository.save(comment4);
            UUID cursor = comment4.getId();

            // when
            List<Comment> results = jpaCommentRepository.findLatestByAuthorId(cursor, authorId, pageable);

            // then
            assertThat(results).hasSize(1);
            results.forEach(comment -> assertThat(comment.getId()).isLessThan(cursor));
        }

        @Test
        @DisplayName("다른 작성자 댓글은 조회되지 않음")
        void findLatestByAuthorIdNotIncludeOtherAuthor() {
            // given
            UUID anotherAuthorId = UUID.randomUUID();

            // when
            List<Comment> results = jpaCommentRepository.findLatestByAuthorId(anotherAuthorId, null, pageable);

            // then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("사용자 댓글 오래된순 조회")
    class FindOldestByAuthorId {
        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("cursor 없음 - 전체 댓글 대상")
        void findOldestByAuthorIdWithoutCursor() {
            // when
            List<Comment> results = jpaCommentRepository.findOldestByAuthorId(null, authorId, pageable);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getAuthorId()).isEqualTo(authorId);
        }

        @Test
        @DisplayName("cursor 있음 - cursor 이후 댓글 대상")
        void findOldestByAuthorIdWithCursor() {
            // given
            Comment comment4 = Comment.builder().postId(postId).authorId(authorId).content("네번째 댓글").build();
            jpaCommentRepository.save(comment4);

            List<UUID> sortedIds = Stream.of(comments.getFirst().getId(), comment4.getId()).sorted().toList();
            UUID cursor = sortedIds.getFirst();

            // when
            List<Comment> results = jpaCommentRepository.findOldestByAuthorId(cursor, authorId, pageable);

            // then
            assertThat(results).hasSize(1);
            results.forEach(comment -> assertThat(comment.getId()).isGreaterThan(cursor));
        }
    }

    @Test
    @DisplayName("postId 목록에 해당하는 댓글 전체 삭제")
    void softDeleteAllByPostIds() {
        // given
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Comment c1 = Comment.builder().postId(postId).content("댓글1").build();
        Comment c2 = Comment.builder().postId(postId).content("댓글2").build();
        Comment other = Comment.builder().postId(UUID.randomUUID()).content("다른 게시글").build();
        jpaCommentRepository.saveAll(List.of(c1, c2, other));

        // when
        jpaCommentRepository.softDeleteAllByPostIds(List.of(postId), userId);

        // then
        List<Comment> result = jpaCommentRepository.findAll();

        assertThat(result).filteredOn(c -> c.getPostId().equals(postId))
                .allSatisfy(c -> {
                    assertThat(c.getDeletedBy()).isEqualTo(userId);
                    assertThat(c.getDeletedAt()).isNotNull();
                });

        assertThat(result).filteredOn(c -> !c.getPostId().equals(postId))
                .allSatisfy(c -> assertThat(c.getDeletedAt()).isNull());
    }
}