package com.fandom.feed.application;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.event.Event;
import com.fandom.feed.domain.entity.Comment;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.CommentErrorCode;
import com.fandom.feed.domain.exception.PostErrorCode;
import com.fandom.feed.domain.repository.CommentRepository;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.global.constant.ReactionSort;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.presentation.dto.response.CommentResponse;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

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

    @Mock
    private UserClient userClient;

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
    @DisplayName("게시글 댓글 목록 조회")
    class GetCommentsForPost {
        private UUID postId;
        private UUID authorId;
        private Post post;
        private List<Comment> comments;
        private UserResponse userResponse;

        @BeforeEach
        void setUp() {
            postId = UUID.randomUUID();
            authorId = UUID.randomUUID();

            post = Post.builder().authorId(authorId).content("게시글").build();
            ReflectionTestUtils.setField(post, "id", postId);

            Comment comment1 = Comment.builder().postId(postId).authorId(authorId).content("첫번째 댓글").build();
            ReflectionTestUtils.setField(comment1, "id", UUID.randomUUID());

            Comment comment2 = Comment.builder().postId(postId).authorId(authorId).content("두번째 댓글").build();
            ReflectionTestUtils.setField(comment2, "id", UUID.randomUUID());

            comments = List.of(comment1, comment2);
            userResponse = new UserResponse(authorId, "작성자");
        }

        @Test
        @DisplayName("게시글 있음 - 댓글 목록 반환")
        void getCommentsForPostInDB() {
            // given
            when(postReader.findById(postId)).thenReturn(post);
            when(commentRepository.findByCursorAndPostId(null, ReactionSort.LATEST, postId)).thenReturn(comments);
            when(userClient.getUsers(any())).thenReturn(ApiResponse.success(List.of(userResponse)));

            // when
            CursorPageResponse<CommentResponse.Detail> response = commentService.getCommentsForPost(
                    postId, null, ReactionSort.LATEST
            );

            // then
            assertThat(response.content()).hasSize(2);
            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();

            verify(postReader).findById(postId);
            verify(commentRepository).findByCursorAndPostId(null, ReactionSort.LATEST, postId);
            verify(userClient).getUsers(any());
        }

        @Test
        @DisplayName("PAGE_SIZE 초과 - hasNext = true")
        void getCommentsForPostWhenExceedsPageSize() {
            // given
            List<Comment> comments = IntStream.range(0, FeedPolicy.PAGE_SIZE + 1)
                    .mapToObj(i -> {
                        Comment c = Comment.builder().postId(postId).authorId(authorId).content("댓글 " + i).build();
                        ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
                        return c;
                    }).toList();

            when(postReader.findById(postId)).thenReturn(post);
            when(commentRepository.findByCursorAndPostId(null, ReactionSort.LATEST, postId)).thenReturn(comments);
            when(userClient.getUsers(any())).thenReturn(ApiResponse.success(List.of(userResponse)));

            // when
            CursorPageResponse<CommentResponse.Detail> response = commentService.getCommentsForPost(
                    postId, null, ReactionSort.LATEST
            );

            // then
            assertThat(response.content()).hasSize(FeedPolicy.PAGE_SIZE);
            assertThat(response.hasNext()).isTrue();
            assertThat(response.nextCursor()).isNotNull();

            verify(postReader).findById(postId);
            verify(commentRepository).findByCursorAndPostId(null, ReactionSort.LATEST, postId);
            verify(userClient).getUsers(any());
        }

        @Test
        @DisplayName("탈퇴 회원 댓글 포함 - '탈퇴한 사용자'로 표시")
        void getCommentsForPostWithdrawnAuthor() {
            // given
            Comment comments = Comment.builder().postId(postId).authorId(null).content("탈퇴 회원 댓글").build();
            ReflectionTestUtils.setField(comments, "id", UUID.randomUUID());

            when(postReader.findById(postId)).thenReturn(post);
            when(commentRepository.findByCursorAndPostId(null, ReactionSort.LATEST, postId)).thenReturn(List.of(comments));

            // when
            CursorPageResponse<CommentResponse.Detail> response = commentService.getCommentsForPost(
                    postId, null, ReactionSort.LATEST
            );

            // then
            assertThat(response.content().getFirst().author().nickname()).isEqualTo("탈퇴한 사용자");

            verify(postReader).findById(postId);
            verify(commentRepository).findByCursorAndPostId(null, ReactionSort.LATEST, postId);
            verifyNoInteractions(userClient);
        }

        @Test
        @DisplayName("게시글 없음 - 예외 발생")
        void getCommentsForPostNotInDB() {
            // given
            when(postReader.findById(postId)).thenThrow(new CustomException(PostErrorCode.POST_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> commentService.getCommentsForPost(postId, null, ReactionSort.LATEST))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PostErrorCode.POST_NOT_FOUND);

            verify(postReader).findById(postId);
            verifyNoInteractions(commentRepository, userClient);
        }
    }

    @Nested
    @DisplayName("사용자 댓글 목록 조회")
    class GetCommentsForUser {
        private UUID authorId;
        private List<Comment> comments;
        private UserResponse userResponse;

        @BeforeEach
        void setUp() {
            UUID postId = UUID.randomUUID();
            authorId = UUID.randomUUID();

            Post post = Post.builder().authorId(authorId).content("게시글").build();
            ReflectionTestUtils.setField(post, "id", postId);

            Comment comment1 = Comment.builder().postId(postId).authorId(authorId).content("첫번째 댓글").build();
            ReflectionTestUtils.setField(comment1, "id", UUID.randomUUID());

            Comment comment2 = Comment.builder().postId(postId).authorId(authorId).content("두번째 댓글").build();
            ReflectionTestUtils.setField(comment2, "id", UUID.randomUUID());

            comments = List.of(comment1, comment2);
            userResponse = new UserResponse(authorId, "작성자");
        }

        @Test
        @DisplayName("본인 - 본인 댓글 목록 반환")
        void getCommentsForUserIsMine() {
            // given
            UserIdCard idCard = UserIdCard.of(authorId, "MEMBER");
            when(commentRepository.findByCursorAndAuthorId(null, ReactionSort.LATEST, authorId)).thenReturn(comments);
            when(userClient.getUsers(any())).thenReturn(ApiResponse.success(List.of(userResponse)));

            // when
            CursorPageResponse<CommentResponse.Detail> response = commentService.getCommentsForUser(
                    null, ReactionSort.LATEST, null, idCard
            );

            // then
            assertThat(response.content()).hasSize(2);
            assertThat(response.hasNext()).isFalse();

            verify(commentRepository).findByCursorAndAuthorId(null, ReactionSort.LATEST, authorId);
            verify(userClient).getUsers(any());
        }

        @Test
        @DisplayName("MASTER 사용자 - 지정한 userId의 댓글 목록 반환")
        void getCommentsForUserIsMaster() {
            // given
            UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MASTER");
            when(commentRepository.findByCursorAndAuthorId(null, ReactionSort.LATEST, authorId)).thenReturn(comments);
            when(userClient.getUsers(any())).thenReturn(ApiResponse.success(List.of(userResponse)));

            // when
            CursorPageResponse<CommentResponse.Detail> response = commentService.getCommentsForUser(
                    null, ReactionSort.LATEST, authorId, idCard
            );

            // then
            assertThat(response.content()).hasSize(2);

            verify(commentRepository).findByCursorAndAuthorId(null, ReactionSort.LATEST, authorId);
            verify(userClient).getUsers(any());
        }

        @Test
        @DisplayName("MASTER인데 userId 미지정 - 예외 발생")
        void getCommentsForUserMasterWithoutUserId() {
            // given
            UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MASTER");

            // when & then
            assertThatThrownBy(() -> commentService.getCommentsForUser(null, ReactionSort.LATEST, null, idCard))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_INPUT_VALUE);

            verifyNoInteractions(commentRepository, userClient);
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
            UserIdCard idCard = UserIdCard.of(authorId, "MEMBER");
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            // when
            CommentResponse.Update response = commentService.updateComment(commentId, "수정된 내용", idCard);

            // then
            assertThat(response.commentId()).isEqualTo(commentId);
            assertThat(response.content()).isEqualTo("수정된 내용");
        }

        @Test
        @DisplayName("작성자 아님 - 예외 발생")
        void updateCommentNotAuthor() {
            // given
            UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> commentService.updateComment(commentId, "수정된 내용", idCard))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommentErrorCode.FORBIDDEN_COMMENT_UPDATE);
        }

        @Test
        @DisplayName("댓글 없음 - 예외 발생")
        void updateCommentNotInDB() {
            // given
            UserIdCard idCard = UserIdCard.of(authorId, "MEMBER");
            when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.updateComment(commentId, "수정된 내용", idCard))
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
            UserIdCard idCard = UserIdCard.of(authorId, "MEMBER");
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(postReader.findById(postId)).thenReturn(post);

            // when
            CommentResponse.Delete response = commentService.deleteComment(commentId, idCard);

            // then
            assertThat(response.commentId()).isEqualTo(commentId);
            verify(applicationEventPublisher).publishEvent(any(Event.CommentDeleted.class));
            verify(postUpdater).decrementCommentCount(postId);
        }

        @Test
        @DisplayName("게시글 작성자 - 댓글 삭제 후 이벤트 발행")
        void deleteCommentByPostAuthor() {
            // given
            UserIdCard idCard = UserIdCard.of(postAuthorId, "MEMBER");
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(postReader.findById(postId)).thenReturn(post);

            // when
            CommentResponse.Delete response = commentService.deleteComment(commentId, idCard);

            // then
            assertThat(response.commentId()).isEqualTo(commentId);
            verify(applicationEventPublisher).publishEvent(any(Event.CommentDeleted.class));
            verify(postUpdater).decrementCommentCount(postId);
        }

        @Test
        @DisplayName("MASTER 사용자 - 댓글 삭제 후 이벤트 발행")
        void deleteCommentByMaster() {
            // given
            UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MASTER");
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(postReader.findById(postId)).thenReturn(post);

            // when
            CommentResponse.Delete response = commentService.deleteComment(commentId, idCard);

            // then
            assertThat(response.commentId()).isEqualTo(commentId);
            verify(applicationEventPublisher).publishEvent(any(Event.CommentDeleted.class));
            verify(postUpdater).decrementCommentCount(postId);
        }

        @Test
        @DisplayName("권한 없음 - 예외 발생")
        void deleteCommentForbidden() {
            // given
            UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");
            when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
            when(postReader.findById(postId)).thenReturn(post);

            // when & then
            assertThatThrownBy(() -> commentService.deleteComment(commentId, idCard))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommentErrorCode.FORBIDDEN_COMMENT_DELETE);

            verify(applicationEventPublisher, never()).publishEvent(any());
            verify(postUpdater, never()).decrementCommentCount(any());
        }

        @Test
        @DisplayName("댓글 없음 - 예외 발생")
        void deleteCommentNotInDB() {
            // given
            UserIdCard idCard = UserIdCard.of(authorId, "MEMBER");
            when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> commentService.deleteComment(commentId, idCard))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommentErrorCode.COMMENT_NOT_FOUND);

            verify(applicationEventPublisher, never()).publishEvent(any());
            verify(postUpdater, never()).decrementCommentCount(any());
        }
    }
}