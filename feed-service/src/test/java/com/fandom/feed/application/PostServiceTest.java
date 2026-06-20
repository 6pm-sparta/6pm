package com.fandom.feed.application;

import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.policy.PostSort;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.PostErrorCode;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.PostCacheService;
import com.fandom.feed.infra.redis.PostListCacheService;
import com.fandom.feed.infra.redis.PostReactionService;
import com.fandom.feed.infra.redis.dto.PostCache;
import com.fandom.feed.infra.util.ImageUrlConverter;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import com.fandom.feed.presentation.dto.response.PostResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.fandom.feed.application.policy.PostPolicy.PAGE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock
    private PostRepository postRepository;

    @Mock
    private PostReader postReader;

    @Mock
    private ImageService imageService;

    @Mock
    private ImageUrlConverter imageUrlConverter;

    @Mock
    private PostCacheService postCacheService;

    @Mock
    private PostReactionService postReactionService;

    @Mock
    PostListCacheService postListCacheService;

    @Mock
    private UserClient userClient;

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

        PostCache.Detail cachedPost = new PostCache.Detail(postId, author, "내용", List.of(), at, at);
        PostCache.ReactionInfo reactionInfo = new PostCache.ReactionInfo(10L, 5L, true);

        when(postCacheService.getPostDetail(postId)).thenReturn(cachedPost);
        when(postReactionService.getReactionInfo(postId, userId)).thenReturn(reactionInfo);

        // When
        PostResponse.Detail result = postService.getPost(postId, userId);

        // Then
        assertThat(result.commentCount()).isEqualTo(10L);
        assertThat(result.likeCount()).isEqualTo(5L);
        assertThat(result.liked()).isTrue();

        verify(postCacheService).getPostDetail(postId);
    }

    @Nested
    @DisplayName("게시글 목록 조회")
    class GetPosts {
        @Test
        @DisplayName("검색 조건 있음 - DB 조회")
        void getPostsWithFilter() {
            // given
            UUID authorId = UUID.randomUUID();
            ApiResponse<List<UserResponse>> apiResponse = mock();

            when(postRepository.findByCursor(any(), any(), eq(authorId), any())).thenReturn(List.of());
            when(userClient.getUsers(any())).thenReturn(apiResponse);
            when(apiResponse.getData()).thenReturn(List.of());
            when(imageService.findAllByPostIds(any())).thenReturn(Map.of());
            when(postReactionService.getReactionInfoBatch(any(), any())).thenReturn(List.of());

            // when
            postService.getPosts(null, PostSort.LATEST, authorId, null, null);

            // then
            verify(postRepository).findByCursor(any(), any(), eq(authorId), any());
            verifyNoInteractions(postListCacheService);
        }

        @Test
        @DisplayName("캐시 준비 안됨 - DB 조회 후 캐시 워밍업")
        void getPostsCacheNotReady() {
            // given
            ApiResponse<List<UserResponse>> apiResponse = mock();

            when(postListCacheService.isCacheReady(PostSort.LATEST)).thenReturn(false);
            when(postRepository.findByCursorForWarm(PostSort.LATEST)).thenReturn(List.of());
            when(userClient.getUsers(any())).thenReturn(apiResponse);
            when(apiResponse.getData()).thenReturn(List.of());
            when(imageService.findAllByPostIds(any())).thenReturn(Map.of());
            when(postReactionService.getReactionInfoBatch(any(), any())).thenReturn(List.of());

            // when
            postService.getPosts(null, PostSort.LATEST, null, null, null);

            // then
            verify(postRepository).findByCursorForWarm(PostSort.LATEST);
            verify(postListCacheService, never()).getPostIds(any(), any());
            verify(postListCacheService).expireCache(PostSort.LATEST);
        }

        @Test
        @DisplayName("캐시에 cursor 없음 - DB 조회")
        void getPostsCursorNotInCache() {
            // given
            UUID cursor = UUID.randomUUID();
            ApiResponse<List<UserResponse>> apiResponse = mock();

            when(postListCacheService.isCacheReady(PostSort.LATEST)).thenReturn(true);
            when(postListCacheService.getPostIds(PostSort.LATEST, cursor)).thenReturn(null);
            when(postRepository.findByCursor(eq(cursor), any(), isNull(), isNull())).thenReturn(List.of());
            when(userClient.getUsers(any())).thenReturn(apiResponse);
            when(apiResponse.getData()).thenReturn(List.of());
            when(imageService.findAllByPostIds(any())).thenReturn(Map.of());
            when(postReactionService.getReactionInfoBatch(any(), any())).thenReturn(List.of());

            // when
            postService.getPosts(cursor, PostSort.LATEST, null, null, null);

            // then
            verify(postRepository).findByCursor(eq(cursor), any(), isNull(), isNull());
        }

        @Test
        @DisplayName("캐시 준비됨 - 캐시 조회")
        void getPostsCacheReady() {
            // given
            UUID postId = UUID.randomUUID();
            when(postListCacheService.isCacheReady(PostSort.LATEST)).thenReturn(true);
            when(postListCacheService.getPostIds(PostSort.LATEST, null)).thenReturn(List.of(postId));
            when(postCacheService.getPostDetailBatch(List.of(postId))).thenReturn(List.of(mock(PostCache.Detail.class)));
            when(postReactionService.getReactionInfoBatch(any(), any())).thenReturn(List.of(mock(PostCache.ReactionInfo.class)));

            // when
            postService.getPosts(null, PostSort.LATEST, null, null, null);

            // then
            verify(postListCacheService).getPostIds(PostSort.LATEST, null);
            verifyNoInteractions(postRepository);
        }
    }

    @Nested
    @DisplayName("캐시에서 가져온 postId 목록으로 응답 구성")
    class BuildCacheResponse {
        @Test
        @DisplayName("PAGE_SIZE 이하 - hasMore = false, nextCursor = null")
        void noHasMoreWhenUnderPageSize() {
            // given
            UUID postId = UUID.randomUUID();
            when(postListCacheService.isCacheReady(PostSort.LATEST)).thenReturn(true);
            when(postListCacheService.getPostIds(PostSort.LATEST, null)).thenReturn(List.of(postId));
            when(postCacheService.getPostDetailBatch(any())).thenReturn(List.of(mock(PostCache.Detail.class)));
            when(postReactionService.getReactionInfoBatch(any(), any())).thenReturn(List.of(mock(PostCache.ReactionInfo.class)));

            // when
            CursorPageResponse<PostResponse.Summary> result = postService.getPosts(
                    null, PostSort.LATEST, null, null, null
            );

            // then
            assertThat(result.hasMore()).isFalse();
            assertThat(result.nextCursor()).isNull();
            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("PAGE_SIZE 초과 - hasMore = true, nextCursor = 마지막 postId")
        void hasMoreWhenExceedsPageSize() {
            // given
            List<UUID> postIds = IntStream.range(0, PAGE_SIZE + 1).mapToObj(i -> UUID.randomUUID()).toList();

            when(postListCacheService.isCacheReady(PostSort.LATEST)).thenReturn(true);
            when(postListCacheService.getPostIds(PostSort.LATEST, null)).thenReturn(postIds);
            when(postCacheService.getPostDetailBatch(any())).thenReturn(IntStream.range(0, PAGE_SIZE)
                    .mapToObj(i -> mock(PostCache.Detail.class)).toList());
            when(postReactionService.getReactionInfoBatch(any(), any())).thenReturn(IntStream.range(0, PAGE_SIZE)
                    .mapToObj(i -> mock(PostCache.ReactionInfo.class)).toList());

            // when
            CursorPageResponse<PostResponse.Summary> result = postService.getPosts(
                    null, PostSort.LATEST, null, null, null
            );

            // then
            assertThat(result.hasMore()).isTrue();
            assertThat(result.nextCursor()).isEqualTo(postIds.get(PAGE_SIZE - 1)); // 20번째
            assertThat(result.content()).hasSize(PAGE_SIZE);
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
            ApiResponse<List<UserResponse>> apiResponse = mock();

            when(postRepository.findByCursor(any(), any(), any(), any())).thenReturn(List.of(post));
            when(imageService.findAllByPostIds(any())).thenReturn(Map.of());
            when(userClient.getUsers(any())).thenReturn(apiResponse);
            when(apiResponse.getData()).thenReturn(List.of());
            when(postReactionService.getReactionInfoBatch(any(), any())).thenReturn(List.of(mock(PostCache.ReactionInfo.class)));

            // when
            CursorPageResponse<PostResponse.Summary> result = postService.getPosts(
                    null, PostSort.LATEST, UUID.randomUUID(), null, null
            );

            // then
            assertThat(result.hasMore()).isFalse();
            assertThat(result.nextCursor()).isNull();
            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("PAGE_SIZE 초과 - hasMore = true, nextCursor = 마지막 postId")
        void hasMoreWhenExceedsPageSize() {
            // given
            List<Post> posts = IntStream.range(0, PAGE_SIZE + 1).mapToObj(i -> mockPost()).toList();
            ApiResponse<List<UserResponse>> apiResponse = mock();

            when(postRepository.findByCursor(any(), any(), any(), any())).thenReturn(posts);
            when(imageService.findAllByPostIds(any())).thenReturn(Map.of());
            when(userClient.getUsers(any())).thenReturn(apiResponse);
            when(userClient.getUsers(any()).getData()).thenReturn(List.of());
            when(postReactionService.getReactionInfoBatch(any(), any())).thenReturn(IntStream.range(0, PAGE_SIZE)
                    .mapToObj(i -> mock(PostCache.ReactionInfo.class)).toList());

            // when
            CursorPageResponse<PostResponse.Summary> result = postService.getPosts(
                    null, PostSort.LATEST, UUID.randomUUID(), null, null
            );

            // then
            assertThat(result.hasMore()).isTrue();
            assertThat(result.nextCursor()).isEqualTo(posts.get(PAGE_SIZE - 1).getId());
            assertThat(result.content()).hasSize(PAGE_SIZE);
        }
    }

    @Test
    @DisplayName("DB에서 게시글 목록 조회 후 캐시 워밍업")
    void getPostsFromDBAndWarm () {
        // given
        UUID postId = UUID.randomUUID();
        Post post = mock(Post.class);
        ApiResponse<List<UserResponse>> apiResponse = mock();

        when(post.getId()).thenReturn(postId);
        when(post.getAuthorId()).thenReturn(UUID.randomUUID());
        when(postRepository.findByCursorForWarm(PostSort.LATEST)).thenReturn(List.of(post));
        when(imageService.findAllByPostIds(any())).thenReturn(Map.of());
        when(userClient.getUsers(any())).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(List.of());
        when(postReactionService.getReactionInfoBatch(any(), any())).thenReturn(List.of(mock(PostCache.ReactionInfo.class)));

        // when
        postService.getPosts(null, PostSort.LATEST, null, null, null);

        // then
        verify(postListCacheService).addPost(postId, PostSort.LATEST);
    }

    @Nested
    @DisplayName("Post 엔티티로 응답 구성")
    class BuildDBResponse {
        @Test
        @DisplayName("image, author, reaction 배치 조회 각 1번씩")
        void buildDBResponse() {
            // given
            List<Post> posts = List.of(mockPost(), mockPost(), mockPost());
            List<UUID> postIds = posts.stream().map(Post::getId).toList();
            ApiResponse<List<UserResponse>> apiResponse = mock();

            when(postRepository.findByCursor(any(), any(), any(), any())).thenReturn(posts);
            when(imageService.findAllByPostIds(postIds)).thenReturn(Map.of());
            when(userClient.getUsers(any())).thenReturn(apiResponse);
            when(apiResponse.getData()).thenReturn(List.of());
            when(postReactionService.getReactionInfoBatch(postIds, null))
                    .thenReturn(List.of(
                            mock(PostCache.ReactionInfo.class),
                            mock(PostCache.ReactionInfo.class),
                            mock(PostCache.ReactionInfo.class)
                    ));

            // when
            postService.getPosts(null, PostSort.LATEST, UUID.randomUUID(), null, null);

            // then
            verify(imageService, times(1)).findAllByPostIds(any());
            verify(userClient, times(1)).getUsers(any());
            verify(postReactionService, times(1)).getReactionInfoBatch(any(), any());
        }

        @Test
        @DisplayName("PAGE_SIZE 초과 - hasMore = true, nextCursor 설정")
        void buildDBResponseWhenExceedsPageSize() {
            // given
            List<Post> posts = IntStream.range(0, PAGE_SIZE + 1).mapToObj(i -> mockPost()).toList();
            ApiResponse<List<UserResponse>> apiResponse = mock();

            when(postRepository.findByCursor(any(), any(), any(), any())).thenReturn(posts);
            when(imageService.findAllByPostIds(any())).thenReturn(Map.of());
            when(userClient.getUsers(any())).thenReturn(apiResponse);
            when(apiResponse.getData()).thenReturn(List.of());
            when(postReactionService.getReactionInfoBatch(any(), any())).thenReturn(IntStream.range(0, PAGE_SIZE)
                    .mapToObj(i -> mock(PostCache.ReactionInfo.class)).toList());

            // when
            CursorPageResponse<PostResponse.Summary> result = postService.getPosts(
                    null, PostSort.LATEST, UUID.randomUUID(), null, null
            );

            // then
            assertThat(result.hasMore()).isTrue();
            assertThat(result.nextCursor()).isNotNull();
            assertThat(result.content()).hasSize(PAGE_SIZE);
        }
    }

    @Nested
    @DisplayName("게시글 수정")
    class UpdatePost {
        @Test
        @DisplayName("작성자 아님 - 예외 발생")
        void updatePostNotAuthor() {
            // given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Post post = Post.builder().authorId(UUID.randomUUID()).content("기존 내용").build();

            when(postReader.findById(postId)).thenReturn(post);

            // when & then
            assertThatThrownBy(() -> postService.updatePost(postId, "새 내용", List.of(), userId))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PostErrorCode.FORBIDDEN_POST_UPDATE);
        }

        @Test
        @DisplayName("이미지 변경 있음 - syncImages가 변경된 imageKeys 반환")
        void updatePostWithImageChange() {
            // given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            List<String> newImageKeys = List.of("key1", "key2");
            Post post = Post.builder().authorId(userId).content("기존 내용").build();

            when(postReader.findById(postId)).thenReturn(post);
            when(imageService.syncImages(postId, newImageKeys)).thenReturn(newImageKeys);
            when(imageUrlConverter.toImageUrls(newImageKeys)).thenReturn(List.of("url1", "url2"));

            // when
            postService.updatePost(postId, "새 내용", newImageKeys, userId);

            // then
            verify(imageService).syncImages(postId, newImageKeys);
            verify(imageUrlConverter).toImageUrls(newImageKeys);
        }

        @Test
        @DisplayName("이미지 변경 없음 - syncImages가 기존 imageKeys 반환")
        void updatePostWithoutImageChange() {
            // given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            List<String> existingImageKeys = List.of("key1", "key2");
            Post post = Post.builder().authorId(userId).content("기존 내용").build();

            when(postReader.findById(postId)).thenReturn(post);
            when(imageService.syncImages(postId, existingImageKeys)).thenReturn(existingImageKeys);
            when(imageUrlConverter.toImageUrls(existingImageKeys)).thenReturn(List.of("url1", "url2"));

            // when
            postService.updatePost(postId, "새 내용", existingImageKeys, userId);

            // then
            verify(imageService).syncImages(postId, existingImageKeys);
            verify(imageUrlConverter).toImageUrls(existingImageKeys);
        }
    }

    @Nested
    @DisplayName("게시글 삭제")
    class DeletePost {
        @Test
        @DisplayName("작성자 아님 - 예외 발생")
        void deletePostNotAuthor() {
            // given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Post post = Post.builder().authorId(UUID.randomUUID()).content("내용").build();

            when(postReader.findById(postId)).thenReturn(post);

            // when & then
            assertThatThrownBy(() -> postService.deletePost(postId, userId))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PostErrorCode.FORBIDDEN_POST_DELETE);
        }

        @Test
        @DisplayName("이미지 있음 - soft delete 및 이미지 삭제 처리")
        void deletePostImagesInDB() {
            // given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            List<String> imageKeys = List.of("key1", "key2");
            Post post = Post.builder().authorId(userId).content("내용").build();

            when(postReader.findById(postId)).thenReturn(post);
            when(imageService.findAllByPostId(postId)).thenReturn(imageKeys);

            // when
            postService.deletePost(postId, userId);

            // then
            assertThat(post.isDeleted()).isTrue();
            verify(imageService).deleteAllByPostId(postId);
            verify(imageService).publishS3DeleteEvent(imageKeys);
        }

        @Test
        @DisplayName("이미지 없음 - S3 삭제 이벤트 발행 안 함")
        void deletePostImagesNotInDB() {
            // given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Post post = Post.builder().authorId(userId).content("내용").build();

            when(postReader.findById(postId)).thenReturn(post);
            when(imageService.findAllByPostId(postId)).thenReturn(List.of());

            // when
            postService.deletePost(postId, userId);

            // then
            verify(imageService).deleteAllByPostId(postId);
            verify(imageService).publishS3DeleteEvent(List.of());
        }
    }

    private Post mockPost() {
        Post post = mock(Post.class);
        lenient().when(post.getId()).thenReturn(UUID.randomUUID());
        lenient().when(post.getAuthorId()).thenReturn(UUID.randomUUID());
        return post;
    }
}