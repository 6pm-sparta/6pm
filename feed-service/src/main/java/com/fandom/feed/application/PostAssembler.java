package com.fandom.feed.application;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.client.UserClientRetryWrapper;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.PostDetailCacheService;
import com.fandom.feed.infra.redis.ReactionCacheService;
import com.fandom.feed.infra.redis.dto.PostDetailCache;
import com.fandom.feed.infra.redis.dto.ReactionInfoCache;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import com.fandom.feed.presentation.dto.response.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class PostAssembler {
    private final ImageService imageService;
    private final PostDetailCacheService postDetailCacheService;
    private final ReactionCacheService reactionCacheService;
    private final UserClientRetryWrapper userClient;

    /** 게시글 목록 캐시에서 가져온 ID 목록으로 응답을 구성하는 메서드 */
    public CursorPageResponse<PostResponse.Summary> buildCacheResponse(List<UUID> postIds, UUID userId) {
        boolean hasNext = postIds.size() > FeedPolicy.PAGE_SIZE;
        List<UUID> pageIds = hasNext ? postIds.subList(0, FeedPolicy.PAGE_SIZE) : postIds;

        List<PostDetailCache> posts = postDetailCacheService.getPostDetailBatch(pageIds);
        List<ReactionInfoCache> reactionInfos = reactionCacheService.getReactionInfoBatch(pageIds, userId, false);

        List<PostResponse.Summary> summaries = IntStream.range(0, pageIds.size())
                .mapToObj(i -> PostResponse.Summary.of(posts.get(i), reactionInfos.get(i)))
                .toList();

        UUID nextCursor = hasNext ? pageIds.getLast() : null;
        return CursorPageResponse.of(summaries, nextCursor, hasNext);
    }

    /** Post 엔티티로 응답을 구성하는 메서드 */
    public CursorPageResponse<PostResponse.Summary> buildDBResponse(
            List<Post> page, UUID nextCursor, boolean hasNext, UUID userId, boolean isLiked
    ) {
        if (page.isEmpty())
            return CursorPageResponse.of(List.of(), null, false);

        List<UUID> postIds = page.stream().map(Post::getId).toList();

        // 배치 조회
        Map<UUID, List<String>> imageUrlsMap = imageService.findAllByPostIds(postIds);

        Set<UUID> authorIds = page.stream().map(Post::getAuthorId).collect(Collectors.toSet());
        Map<UUID, UserResponse> authorMap = userClient.getUsers(authorIds)
                .stream().collect(Collectors.toMap(UserResponse::userId, Function.identity()));

        List<ReactionInfoCache> reactionInfos = reactionCacheService.getReactionInfoBatch(postIds, userId, isLiked);

        // DTO 조립
        List<PostDetailCache> details = page.stream()
                .map(post -> PostDetailCache.of(
                        post, imageUrlsMap.getOrDefault(post.getId(), List.of()), authorMap.get(post.getAuthorId())
                ))
                .toList();

        List<PostResponse.Summary> summaries = IntStream.range(0, page.size())
                .mapToObj(i -> PostResponse.Summary.of(details.get(i), reactionInfos.get(i)))
                .toList();

        return CursorPageResponse.of(summaries, nextCursor, hasNext);
    }
}
