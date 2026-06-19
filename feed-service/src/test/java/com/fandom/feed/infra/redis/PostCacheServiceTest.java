package com.fandom.feed.infra.redis;

import com.fandom.common.dto.ApiResponse;
import com.fandom.feed.application.PostReader;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.ImageRepository;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.dto.PostCache;
import com.fandom.feed.infra.util.ImageUrlConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostCacheServiceTest {
    @Mock
    private PostReader postReader;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ImageUrlConverter imageUrlConverter;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private PostCacheService postCacheService;

    @Test
    @DisplayName("게시글 상세 조회")
    void getPostDetailTest() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Post post = Post.builder().authorId(userId).content("내용").build();
        ApiResponse<UserResponse> authorResponse = ApiResponse.success(new UserResponse(userId, "닉네임"));

        when(postReader.findById(postId)).thenReturn(post);
        when(imageRepository.findAllByPostIdOrderByOrderIndexAsc(postId)).thenReturn(List.of());
        when(userClient.getUser(userId)).thenReturn(authorResponse);
        when(imageUrlConverter.toImageUrls(anyList())).thenReturn(List.of());

        // When
        PostCache.Detail result = postCacheService.getPostDetail(postId);

        // Then
        assertThat(result.postId()).isEqualTo(post.getId());
        verify(postReader).findById(postId);
        verify(imageRepository).findAllByPostIdOrderByOrderIndexAsc(postId);
        verify(userClient).getUser(userId);
        verify(imageUrlConverter).toImageUrls(anyList());
    }
}