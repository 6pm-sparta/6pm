package com.fandom.feed.application;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.event.Event;
import com.fandom.feed.domain.entity.Comment;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.CommentErrorCode;
import com.fandom.feed.domain.exception.PostErrorCode;
import com.fandom.feed.domain.repository.CommentRepository;
import com.fandom.feed.presentation.dto.response.CommentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostReader postReader;

    @Mock
    private PostUpdater postUpdater;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private CommentService commentService;

    private UUID postId;
    private UUID postAuthorId;
    private Post post;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        postAuthorId = UUID.randomUUID();

        post = Post.builder().authorId(postAuthorId).content("게시글 내용").build();
        ReflectionTestUtils.setField(post, "id", postId);
    }

    @Nested
    @DisplayName("댓글 생성")
    class CreateComment {
        private final UUID userId = UUID.randomUUID();

        @Test
        @DisplayName("게시글 있음 - 댓글 생성 후 이벤트 발행")
        void createCommentPostInDB() {
            // given
            UUID postId = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();

            when(postReader.findById(postId)).thenReturn(post);
            when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
                Comment c = invocation.getArgument(0);
                ReflectionTestUtils.setField(c, "id", commentId);
                return c;
            });

            // when
            CommentResponse.Create response = commentService.createComment(postId, "댓글 내용", userId);

            // then
            assertThat(response.commentId()).isEqualTo(commentId);
            assertThat(response.content()).isEqualTo("댓글 내용");
            verify(commentRepository).save(any(Comment.class));
            verify(applicationEventPublisher).publishEvent(any(Event.CommentCreated.class));
            verify(postUpdater).incrementCommentCount(postId);
        }

        @Test
        @DisplayName("게시글 없음 - 예외 발생")
        void createCommentPostNotInDB() {
            // given
            when(postReader.findById(postId)).thenThrow(new CustomException(PostErrorCode.POST_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> commentService.createComment(postId, "댓글 내용", userId))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PostErrorCode.POST_NOT_FOUND);

            verify(commentRepository, never()).save(any());
            verify(applicationEventPublisher, never()).publishEvent(any());
            verify(postUpdater, never()).incrementCommentCount(any());
        }
    }

    @Nested
    @DisplayName("댓글 수정")
    class UpdateComment {
        private UUID authorId;
        private UUID commentId;
        private Comment comment;

        @BeforeEach
        void setUp() {
            authorId = UUID.randomUUID();
            commentId = UUID.randomUUID();
            comment = Comment.builder().postId(postId).authorId(authorId).content("댓글 내용").build();
            ReflectionTestUtils.setField(comment, "id", commentId);
        }

        @Test
        @DisplayName("작성자 - 정상 수정")
        void updateCommentIsAuthor() {
            // given
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            // when
            CommentResponse.Update response = commentService.updateComment(commentId, "수정된 내용", authorId);

            // then
            assertThat(response.commentId()).isEqualTo(commentId);
            assertThat(response.content()).isEqualTo("수정된 내용");
        }

        @Test
        @DisplayName("작성자 아님 - 예외 발생")
        void updateCommentNotAuthor() {
            // given
            UUID anotherUserId = UUID.randomUUID();
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> commentService.updateComment(commentId, "수정된 내용", anotherUserId))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommentErrorCode.FORBIDDEN_COMMENT_UPDATE);
        }

        @Test
        @DisplayName("댓글 없음 - 예외 발생")
        void updateCommentNotInDB() {
            // given
            when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.updateComment(commentId, "수정된 내용", authorId))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommentErrorCode.COMMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("댓글 삭제")
    class DeleteComment {
        private UUID authorId;
        private UUID commentId;
        private Comment comment;

        @BeforeEach
        void setUp() {
            authorId = UUID.randomUUID();
            commentId = UUID.randomUUID();
            comment = Comment.builder().postId(postId).authorId(authorId).content("댓글 내용").build();
            ReflectionTestUtils.setField(comment, "id", commentId);
        }

        @Test
        @DisplayName("작성자 - 댓글 삭제 후 이벤트 발행")
        void deleteCommentByAuthor() {
            // given
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(postReader.findById(postId)).thenReturn(post);

            // when
            CommentResponse.Delete response = commentService.deleteComment(commentId, authorId, false);

            // then
            assertThat(response.commentId()).isEqualTo(commentId);
            verify(applicationEventPublisher).publishEvent(any(Event.CommentDeleted.class));
            verify(postUpdater).decrementCommentCount(postId);
        }

        @Test
        @DisplayName("게시글 작성자 - 댓글 삭제 후 이벤트 발행")
        void deleteCommentByPostAuthor() {
            // given
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(postReader.findById(postId)).thenReturn(post);

            // when
            CommentResponse.Delete response = commentService.deleteComment(commentId, postAuthorId, false);

            // then
            assertThat(response.commentId()).isEqualTo(commentId);
            verify(applicationEventPublisher).publishEvent(any(Event.CommentDeleted.class));
            verify(postUpdater).decrementCommentCount(postId);
        }

        @Test
        @DisplayName("MASTER 사용자 - 댓글 삭제 후 이벤트 발행")
        void deleteCommentByMaster() {
            // given
            UUID masterId = UUID.randomUUID();
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(postReader.findById(postId)).thenReturn(post);

            // when
            CommentResponse.Delete response = commentService.deleteComment(commentId, masterId, true);

            // then
            assertThat(response.commentId()).isEqualTo(commentId);
            verify(applicationEventPublisher).publishEvent(any(Event.CommentDeleted.class));
            verify(postUpdater).decrementCommentCount(postId);
        }

        @Test
        @DisplayName("권한 없음 - 예외 발생")
        void deleteCommentForbidden() {
            // given
            UUID anotherUserId = UUID.randomUUID();
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(postReader.findById(postId)).thenReturn(post);

            // when & then
            assertThatThrownBy(() -> commentService.deleteComment(commentId, anotherUserId, false))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommentErrorCode.FORBIDDEN_COMMENT_DELETE);

            verify(applicationEventPublisher, never()).publishEvent(any());
            verify(postUpdater, never()).decrementCommentCount(any());
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 댓글이면 예외를 던진다")
        void deleteComment_commentNotFound() {
            // given
            when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.deleteComment(commentId, authorId, false))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommentErrorCode.COMMENT_NOT_FOUND);

            verify(applicationEventPublisher, never()).publishEvent(any());
            verify(postUpdater, never()).decrementCommentCount(any());
        }
    }
}