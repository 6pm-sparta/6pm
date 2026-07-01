package com.fandom.feed.application;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.event.Event;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.PostErrorCode;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.kafka.outbox.OutboxEventType;
import com.fandom.feed.infra.kafka.outbox.OutboxEventWriter;
import com.fandom.feed.infra.redis.PostDetailCacheService;
import com.fandom.feed.infra.redis.PostListCacheService;
import com.fandom.feed.infra.redis.ReactionCacheService;
import com.fandom.feed.infra.redis.dto.PostDetailCache;
import com.fandom.feed.infra.redis.dto.ReactionInfoCache;
import com.fandom.feed.infra.s3.util.ImageUrlConverter;
import com.fandom.feed.presentation.dto.response.PostResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock
    private PostRepository postRepository;

    @Mock
    private PostReader postReader;

    @Mock
    private PostAssembler postAssembler;

    @Mock
    private ImageService imageService;

    @Mock
    private LikeService likeService;

    @Mock
    private CommentService commentService;

    @Mock
    private ImageUrlConverter imageUrlConverter;

    @Mock
    private PostDetailCacheService postDetailCacheService;

    @Mock
    private ReactionCacheService reactionCacheService;

    @Mock
    private PostListCacheService postListCacheService;

    @Mock
    UserClient userClient;

    @Mock
    OutboxEventWriter outboxEventWriter;

    @InjectMocks
    private PostService postService;

    @Nested
    @DisplayName("게시글 생성")
    class CreatePost {
        @BeforeEach
        void setUp() {
            when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
                Post p = invocation.getArgument(0);
                ReflectionTestUtils.invokeMethod(p, "assignId");
                return p;
            });
            when(userClient.getUser(any(UUID.class)))
                    .thenReturn(ApiResponse.success(new UserResponse(UUID.randomUUID(), "닉네임")));
        }

        @Test
        @DisplayName("이미지 있음 - 이미지 저장 로직 실행")
        void createPostWithImages() {
            // given
            UUID userId = UUID.randomUUID();
            String content = "이미지 있는 게시글";
            List<String> imageKeys = List.of("key1", "key2");

            // when
            postService.createPost(content, imageKeys, userId);

            // then
            verify(postRepository).save(any(Post.class));
            verify(imageService).saveImages(any(UUID.class), eq(imageKeys));
            verify(imageUrlConverter).toImageUrls(imageKeys);
            verify(postListCacheService).addPost(any(), any());
            verify(outboxEventWriter).write(any(UUID.class), eq(OutboxEventType.POST_CREATED), any(Event.PostCreated.class));
        }

        @Test
        @DisplayName("이미지 없음 - 이미지 저장 로직 미실행")
        void createPostWithoutImages() {
            // given
            UUID userId = UUID.randomUUID();
            String content = "이미지 없는 게시글";
            List<String> imageKeys = List.of();

            // when
            postService.createPost(content, imageKeys, userId);

            // then
            verify(postRepository).save(any(Post.class));
            verify(imageService).saveImages(any(), eq(List.of()));
            verify(postListCacheService).addPost(any(), any());
            verify(outboxEventWriter).write(any(UUID.class), eq(OutboxEventType.POST_CREATED), any(Event.PostCreated.class));
        }

        @Test
        @DisplayName("닉네임 조회 결과를 outbox 이벤트에 전달")
        void createPostUsesNicknameFromUserClient() {
            // given
            UUID userId = UUID.randomUUID();
            String content = "닉네임 조회 확인 게시글";
            List<String> imageKeys = List.of();

            ArgumentCaptor<Event.PostCreated> captor = ArgumentCaptor.forClass(Event.PostCreated.class);

            // when
            postService.createPost(content, imageKeys, userId);

            // then
            verify(outboxEventWriter).write(any(UUID.class), eq(OutboxEventType.POST_CREATED), captor.capture());
            assertThat(captor.getValue().nickname()).isEqualTo("닉네임");
        }
    }

    @Test
    @DisplayName("게시글 상세 조회 - 게시글 정보 + 리액션 정보")
    void getPost() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        LocalDateTime at = LocalDateTime.now();
        UserResponse author = new UserResponse(userId, "닉네임");

        PostDetailCache cachedPost = new PostDetailCache(postId, author, "내용", List.of(), at, at);
        ReactionInfoCache reactionInfo = new ReactionInfoCache(10L, 5L, true);

        when(postDetailCacheService.getPostDetail(postId)).thenReturn(cachedPost);
        when(reactionCacheService.getReactionInfo(postId, userId)).thenReturn(reactionInfo);

        // When
        PostResponse.Detail result = postService.getPost(postId, userId);

        // Then
        assertThat(result.commentCount()).isEqualTo(10L);
        assertThat(result.likeCount()).isEqualTo(5L);
        assertThat(result.liked()).isTrue();

        verify(postDetailCacheService).getPostDetail(postId);
    }

    @Nested
    @DisplayName("게시글 목록 조회")
    class GetPosts {
        @Test
        @DisplayName("검색어 있음 - DB 조회")
        void getPostsWithKeyword() {
            // given
            when(postRepository.findByCursor(any(), any(), any())).thenReturn(List.of());

            // when
            postService.getPosts(null, null, "검색어", null);

            // then
            verify(postRepository).findByCursor(any(), any(), eq("검색어"));
            verify(postAssembler).buildDBResponse(any(), any(), anyBoolean(), any(), anyBoolean());
        }

        @Test
        @DisplayName("캐시 준비 안됨 - DB 조회 후 캐시 워밍업")
        void getPostsCacheNotReady() {
            // given
            when(postListCacheService.isCacheReady(null)).thenReturn(false);
            when(postRepository.findByCursorForWarm(null)).thenReturn(List.of());

            // when
            postService.getPosts(null, null, null, null);

            // then
            verify(postRepository).findByCursorForWarm(null);
            verify(postListCacheService, never()).addPost(any(), isNull());
            verify(postListCacheService).expireCache(null);
            verify(postAssembler).buildDBResponse(any(), any(), anyBoolean(), any(), anyBoolean());
        }

        @Test
        @DisplayName("캐시에 cursor 없음 - DB 조회")
        void getPostsCursorNotInCache() {
            // given
            UUID cursor = UUID.randomUUID();

            when(postListCacheService.isCacheReady(null)).thenReturn(true);
            when(postListCacheService.getPostIds(null, cursor)).thenReturn(null);
            when(postRepository.findByCursor(eq(cursor), isNull(), isNull())).thenReturn(List.of());

            // when
            postService.getPosts(cursor, null, null, null);

            // then
            verify(postRepository).findByCursor(eq(cursor), isNull(), isNull());
            verify(postAssembler).buildDBResponse(any(), any(), anyBoolean(), any(), anyBoolean());
        }

        @Test
        @DisplayName("캐시 준비됨 - 캐시 조회")
        void getPostsCacheReady() {
            // given
            UUID postId = UUID.randomUUID();

            when(postListCacheService.isCacheReady(null)).thenReturn(true);
            when(postListCacheService.getPostIds(null, null)).thenReturn(List.of(postId));

            // when
            postService.getPosts(null, null, null, null);

            // then
            verify(postListCacheService).getPostIds(null, null);
            verify(postAssembler).buildCacheResponse(any(), any());
        }
    }

    @Nested
    @DisplayName("DB에서 게시글 목록 조회")
    class GetPostsFromDB {
        @Test
        @DisplayName("PAGE_SIZE 이하 - hasMore = false, nextCursor = null")
        void noHasMoreWhenUnderPageSize() {
            // given
            Post post = mockPost();
            when(postRepository.findByCursor(any(), any(), any())).thenReturn(List.of(post));

            // when
            postService.getPosts(null, null, "검색어", null);

            // then
            verify(postAssembler).buildDBResponse(any(), isNull(), eq(false), any(), anyBoolean());
        }

        @Test
        @DisplayName("PAGE_SIZE 초과 - hasMore = true, nextCursor = 마지막 postId")
        void hasMoreWhenExceedsPageSize() {
            // given
            List<Post> posts = IntStream.range(0, FeedPolicy.PAGE_SIZE + 1).mapToObj(i -> mockPost()).toList();
            UUID expectedCursor = posts.get(FeedPolicy.PAGE_SIZE - 1).getId();
            when(postRepository.findByCursor(any(), any(), any())).thenReturn(posts);

            // when
            postService.getPosts(null, null, "검색어", null);

            // then
            verify(postAssembler).buildDBResponse(any(), eq(expectedCursor), eq(true), any(), anyBoolean());
        }
    }

    @Test
    @DisplayName("DB에서 게시글 목록 조회 후 캐시 워밍업")
    void getPostsFromDBAndWarm() {
        // given
        UUID postId = UUID.randomUUID();
        Post post = mock(Post.class);

        when(post.getId()).thenReturn(postId);
        when(postListCacheService.isCacheReady(null)).thenReturn(false);
        when(postRepository.findByCursorForWarm(null)).thenReturn(List.of(post));

        // when
        postService.getPosts(null, null, null, null);

        // then
        verify(postListCacheService).addPostForWarm(postId, null);
        verify(postListCacheService).expireCache(null);
        verify(postAssembler).buildDBResponse(any(), any(), anyBoolean(), any(), anyBoolean());
    }

    @Nested
    @DisplayName("게시글 수정")
    class UpdatePost {
        private final UUID userId = UUID.randomUUID();
        private final UserIdCard idCard = UserIdCard.of(userId, "CREATOR");

        @Test
        @DisplayName("작성자 아님 - 예외 발생")
        void updatePostNotAuthor() {
            // given
            UUID postId = UUID.randomUUID();
            UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "CREATOR");
            Post post = Post.builder().authorId(UUID.randomUUID()).content("기존 내용").build();

            when(postReader.findById(postId)).thenReturn(post);

            // when & then
            assertThatThrownBy(() -> postService.updatePost(postId, "새 내용", List.of(), idCard))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PostErrorCode.FORBIDDEN_POST_UPDATE);
        }

        @Test
        @DisplayName("이미지 변경 있음 - syncImages가 변경된 imageKeys 반환")
        void updatePostWithImageChange() {
            // given
            UUID postId = UUID.randomUUID();
            List<String> newImageKeys = List.of("key1", "key2");
            Post post = Post.builder().authorId(userId).content("기존 내용").build();

            when(postReader.findById(postId)).thenReturn(post);
            when(imageService.syncImages(postId, newImageKeys)).thenReturn(newImageKeys);
            when(imageUrlConverter.toImageUrls(newImageKeys)).thenReturn(List.of("url1", "url2"));

            // when
            postService.updatePost(postId, "새 내용", newImageKeys, idCard);

            // then
            verify(imageService).syncImages(postId, newImageKeys);
            verify(imageUrlConverter).toImageUrls(newImageKeys);
        }

        @Test
        @DisplayName("이미지 변경 없음 - syncImages가 기존 imageKeys 반환")
        void updatePostWithoutImageChange() {
            // given
            UUID postId = UUID.randomUUID();
            List<String> existingImageKeys = List.of("key1", "key2");
            Post post = Post.builder().authorId(userId).content("기존 내용").build();

            when(postReader.findById(postId)).thenReturn(post);
            when(imageService.syncImages(postId, existingImageKeys)).thenReturn(existingImageKeys);
            when(imageUrlConverter.toImageUrls(existingImageKeys)).thenReturn(List.of("url1", "url2"));

            // when
            postService.updatePost(postId, "새 내용", existingImageKeys, idCard);

            // then
            verify(imageService).syncImages(postId, existingImageKeys);
            verify(imageUrlConverter).toImageUrls(existingImageKeys);
        }
    }

    @Nested
    @DisplayName("게시글 삭제")
    class DeletePost {
        private final UUID userId = UUID.randomUUID();
        private final UserIdCard idCard = UserIdCard.of(userId, "CREATOR");

        @Test
        @DisplayName("작성자 아님 - 예외 발생")
        void deletePostNotAuthor() {
            // given
            UUID postId = UUID.randomUUID();
            UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "CREATOR");
            Post post = Post.builder().authorId(UUID.randomUUID()).content("내용").build();

            when(postReader.findById(postId)).thenReturn(post);

            // when & then
            assertThatThrownBy(() -> postService.deletePost(postId, idCard))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PostErrorCode.FORBIDDEN_POST_DELETE);
        }

        @Test
        @DisplayName("이미지 있음 - soft delete 및 이미지 삭제 처리")
        void deletePostImagesInDB() {
            // given
            UUID postId = UUID.randomUUID();
            List<String> imageKeys = List.of("key1", "key2");
            Post post = Post.builder().authorId(userId).content("내용").build();

            when(postReader.findById(postId)).thenReturn(post);
            when(imageService.findAllByPostId(postId)).thenReturn(imageKeys);

            // when
            postService.deletePost(postId, idCard);

            // then
            assertThat(post.isDeleted()).isTrue();
            verify(likeService).deleteAllByPostId(postId);
            verify(commentService).deleteAllByPostId(postId, userId);
            verify(imageService).deleteAllByPostId(postId);
            verify(imageService).publishS3DeleteEvent(imageKeys);
            verify(postListCacheService).removePost(postId, userId);
        }

        @Test
        @DisplayName("이미지 없음 - S3 삭제 이벤트 발행 안 함")
        void deletePostImagesNotInDB() {
            // given
            UUID postId = UUID.randomUUID();
            Post post = Post.builder().authorId(userId).content("내용").build();

            when(postReader.findById(postId)).thenReturn(post);
            when(imageService.findAllByPostId(postId)).thenReturn(List.of());

            // when
            postService.deletePost(postId, idCard);

            // then
            verify(likeService).deleteAllByPostId(postId);
            verify(commentService).deleteAllByPostId(postId, userId);
            verify(imageService).deleteAllByPostId(postId);
            verify(imageService).publishS3DeleteEvent(List.of());
            verify(postListCacheService).removePost(postId, userId);
        }

        @Test
        @DisplayName("MASTER 사용자 - 정상 삭제")
        void deletePostIsMaster() {
            // given
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MASTER");
            Post post = Post.builder().authorId(authorId).content("내용").build();

            when(postReader.findById(postId)).thenReturn(post);
            when(imageService.findAllByPostId(postId)).thenReturn(List.of());

            // when
            postService.deletePost(postId, idCard);

            // then
            verify(likeService).deleteAllByPostId(postId);
            verify(commentService).deleteAllByPostId(postId, idCard.getUserId());
            verify(imageService).deleteAllByPostId(postId);
            verify(imageService).publishS3DeleteEvent(List.of());
            verify(postListCacheService).removePost(postId, authorId);
        }
    }

    @Nested
    @DisplayName("작성자 ID로 모든 게시글 삭제")
    class DeleteAllByAuthorId {
        private UUID authorId;
        private List<UUID> postIds;
        private List<String> imageKeys;

        @BeforeEach
        void setUp() {
            authorId = UUID.randomUUID();
            postIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            imageKeys = List.of("posts/20240101/uuid1.jpg", "posts/20240101/uuid2.jpg");
        }

        @Test
        @DisplayName("게시글 있음 - 관련 데이터 일괄 삭제")
        void deleteAllByAuthorIdWithPost() {
            // given
            given(postRepository.findAllIdsByAuthorId(authorId)).willReturn(postIds);
            given(imageService.findAllKeysByPostIds(postIds)).willReturn(imageKeys);

            // when
            postService.deleteAllByAuthorId(authorId);

            // then
            then(commentService).should().deleteAllByPostIds(postIds, authorId);
            then(likeService).should().deleteAllByPostIds(postIds);
            then(postRepository).should().softDeleteAllByAuthorId(authorId);
            then(imageService).should().deleteAllByPostIds(postIds);
            then(imageService).should().publishS3DeleteEvent(imageKeys);
            then(postListCacheService).should().removeAllByAuthorId(postIds, authorId);
            then(postDetailCacheService).should().deleteAll(postIds);
        }

        @Test
        @DisplayName("게시글 없음 - 아무것도 실행하지 않음")
        void deleteAllByAuthorIdWhenEmpty() {
            // given
            given(postRepository.findAllIdsByAuthorId(authorId)).willReturn(List.of());

            // when
            postService.deleteAllByAuthorId(authorId);

            // then
            then(commentService).shouldHaveNoInteractions();
            then(likeService).shouldHaveNoInteractions();
            then(postRepository).should(never()).softDeleteAllByAuthorId(any());
            then(imageService).shouldHaveNoInteractions();
            then(postListCacheService).shouldHaveNoInteractions();
            then(postDetailCacheService).shouldHaveNoInteractions();
        }
    }

    private Post mockPost() {
        Post post = mock(Post.class);
        lenient().when(post.getId()).thenReturn(UUID.randomUUID());
        lenient().when(post.getAuthorId()).thenReturn(UUID.randomUUID());
        return post;
    }
}