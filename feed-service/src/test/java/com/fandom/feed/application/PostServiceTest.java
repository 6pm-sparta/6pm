package com.fandom.feed.application;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.domain.repository.ImageRepository;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.PostCacheService;
import com.fandom.feed.infra.redis.PostReactionService;
import com.fandom.feed.infra.redis.dto.PostCache;
import com.fandom.feed.infra.util.ImageUrlConverter;
import com.fandom.feed.presentation.dto.response.PostResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock
    private PostRepository postRepository;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ImageUrlConverter imageUrlConverter;

    @Mock
    private PostCacheService postCacheService;

    @Mock
    private PostReactionService postReactionService;

    @InjectMocks
    private PostService postService;

    @Nested
    @DisplayName("게시글 생성")
    class CreatePost {
        @Test
        @DisplayName("이미지 포함 - 이미지 저장 로직 실행")
        void createPostWithImagesTest() {
            // given
            UUID userId = UUID.randomUUID();
            String content = "테스트 게시글 내용";
            List<String> imageKeys = List.of("key1", "key2");

            // when
            postService.createPost(content, imageKeys, userId);

            // then
            verify(postRepository, times(1)).save(any(Post.class));
            verify(imageRepository, times(1)).saveAll(any());
            verify(imageUrlConverter, times(1)).toImageUrls(imageKeys);
        }

        @Test
        @DisplayName("이미지 미포함 - 이미지 저장 로직 미실행")
        void createPostWithoutImagesTest() {
            // given
            UUID userId = UUID.randomUUID();
            String content = "이미지 없는 게시글";
            List<String> imageKeys = List.of();

            // when
            postService.createPost(content, imageKeys, userId);

            // then
            verify(postRepository, times(1)).save(any(Post.class));
            verify(imageRepository, never()).saveAll(any());
        }
    }

    @Test
    @DisplayName("게시글 상세 조회 - 게시글 상세 + 리액션 정보")
    void getPostTest() {
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
}