package com.fandom.feed.application;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.global.constant.ReactionSort;
import com.fandom.feed.infra.redis.ReactionCacheService;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import com.fandom.feed.presentation.dto.response.PostResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {
    @Mock
    private PostReader postReader;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ReactionCacheService reactionCacheService;

    @Mock
    private PostAssembler postAssembler;

    @InjectMocks
    private LikeService likeService;

    @Test
    @DisplayName("좋아요 조회 시 좋아요한 순서대로 정렬되어 반환")
    void getLikes() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID postId1 = UUID.randomUUID();
        UUID postId2 = UUID.randomUUID();

        // 좋아요 누른 순서: post1 → post2
        Like like1 = Like.builder().postId(postId1).userId(userId).build();
        Like like2 = Like.builder().postId(postId2).userId(userId).build();
        ReflectionTestUtils.setField(like1, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(like2, "id", UUID.randomUUID());
        when(likeRepository.findByCursorAndUserId(null, ReactionSort.LATEST, userId))
                .thenReturn(List.of(like1, like2));

        Post post1 = Post.builder().authorId(UUID.randomUUID()).content("p1").build();
        Post post2 = Post.builder().authorId(UUID.randomUUID()).content("p2").build();
        ReflectionTestUtils.setField(post1, "id", postId1);
        ReflectionTestUtils.setField(post2, "id", postId2);

        // 일부러 순서를 뒤집어서 반환
        when(postReader.findAllByIds(List.of(postId1, postId2))).thenReturn(List.of(post2, post1));

        CursorPageResponse<PostResponse.Summary> dummy = CursorPageResponse.of(List.of(), null, false);
        when(postAssembler.buildDBResponse(anyList(), any(), anyBoolean(), eq(userId), eq(true))).thenReturn(dummy);

        // When
        likeService.getLikes(null, ReactionSort.LATEST, userId);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Post>> captor = ArgumentCaptor.forClass(List.class);

        verify(postAssembler).buildDBResponse(captor.capture(), any(), anyBoolean(), eq(userId), eq(true));
        assertThat(captor.getValue()).extracting(Post::getId).containsExactly(postId1, postId2);
    }

    @Test
    @DisplayName("좋아요 삭제 시 DB → Redis 순서 보장")
    void deleteLike() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(reactionCacheService.removeLike(postId, userId)).thenReturn(0L);

        // When
        likeService.deleteLike(postId, userId);

        // Then
        InOrder inOrder = inOrder(likeRepository, reactionCacheService);
        inOrder.verify(likeRepository).deleteByPostIdAndUserId(postId, userId);
        inOrder.verify(reactionCacheService).removeLike(postId, userId);
    }

    @Test
    @DisplayName("사용자 ID로 모든 좋아요 삭제")
    void deleteAllByUserId() {
        // given
        UUID userId = UUID.randomUUID();
        List<UUID> postIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        given(likeRepository.findPostIdsByUserId(userId)).willReturn(postIds);

        // when
        likeService.deleteAllByUserId(userId);

        // then
        verify(likeRepository).deleteAllByUserId(userId);
        verify(reactionCacheService).removeLikeBatch(postIds, userId);
    }
}