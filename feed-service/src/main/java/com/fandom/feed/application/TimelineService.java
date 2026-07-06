package com.fandom.feed.application;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.infra.util.UuidV7TimestampExtractor;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.client.UserClientRetryWrapper;
import com.fandom.feed.infra.client.dto.FollowingResponse;
import com.fandom.feed.infra.redis.LargeFollowingCacheService;
import com.fandom.feed.infra.redis.PostListCacheService;
import com.fandom.feed.infra.redis.TimelineCacheService;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import com.fandom.feed.presentation.dto.response.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimelineService {
    private final PostAssembler postAssembler;
    private final PostRepository postRepository;
    private final TimelineCacheService timelineCacheService;
    private final LargeFollowingCacheService largeFollowingCacheService;
    private final PostListCacheService postListCacheService;
    private final UserClientRetryWrapper userClient;

    public CursorPageResponse<PostResponse.Summary> getTimeline(UUID userId, UUID cursor) {
        return timelineCacheService.exists(userId)
                ? getFromCacheHit(userId, cursor)
                : getFromCacheMiss(userId, cursor);
    }

    /** 캐시에서 게시글 ID를 가져와 대형 크리에이터 게시글과 병합하는 메서드 */
    private CursorPageResponse<PostResponse.Summary> getFromCacheHit(UUID userId, UUID cursor) {
        // 일반 크리에이터 게시글 ID 조회
        List<UUID> smallAuthorPostIds = timelineCacheService.getPostIds(userId, cursor);

        // 대형 크리에이터 게시글 ID 조회
        List<UUID> largeAuthorIds = resolveLargeFollowingIds(userId);
        List<UUID> largeAuthorPostIds = fetchLargeFollowingPostIds(largeAuthorIds, cursor);

        // 게시글 ID 병합
        List<UUID> mergedIds = mergeSorted(
                (smallAuthorPostIds != null) ? smallAuthorPostIds : List.of(),
                largeAuthorPostIds,
                Function.identity()
        );

        return postAssembler.buildCacheResponse(mergedIds, userId);
    }

    /** DB에서 모든 크리에이터의 게시글을 가져와 병합하는 메서드 */
    private CursorPageResponse<PostResponse.Summary> getFromCacheMiss(UUID userId, UUID cursor) {
        List<FollowingResponse> allFollowingIds = fetchAllFollowingIds(userId);

        // 일반/대형 크리에이터 분류
        List<UUID> largeAuthorIds = allFollowingIds.stream()
                .filter(FollowingResponse::isLarge)
                .map(FollowingResponse::authorId)
                .toList();
        List<UUID> smallAuthorIds = allFollowingIds.stream()
                .filter(f -> !f.isLarge())
                .map(FollowingResponse::authorId)
                .toList();

        // 일반 크리에이터: 게시글 조회 후, 캐시에 추가
        List<Post> smallAuthorPosts = postRepository.findByAuthorIdInForWarm(smallAuthorIds);
        List<UUID> smallAuthorPostIds = smallAuthorPosts.stream().map(Post::getId).toList();
        timelineCacheService.addPostsForWarm(userId, smallAuthorPostIds);

        // 대형 크리에이터: 캐시 확인 후, 없으면 DB 조회
        largeFollowingCacheService.addLargeFollowing(userId, largeAuthorIds);
        List<Post> largeAuthorPosts = fetchLargeAuthorPostEntities(largeAuthorIds, cursor);

        // 게시글 병합
        List<Post> merged = mergeSorted(smallAuthorPosts, largeAuthorPosts, Post::getId);
        boolean hasNext = merged.size() > FeedPolicy.PAGE_SIZE;
        List<Post> page = hasNext ? merged.subList(0, FeedPolicy.PAGE_SIZE) : merged;

        UUID nextCursor = hasNext ? page.getLast().getId() : null;
        return postAssembler.buildDBResponse(page, nextCursor, hasNext, userId, false);
    }

    /**
     * 대형 크리에이터 팔로잉 목록 캐시에서 팔로잉 ID를 조회하는 메서드<br>
     * - 캐시 미스 발생 시, User 서비스 조회 후 캐시에 저장
     */
    private List<UUID> resolveLargeFollowingIds(UUID userId) {
        if (largeFollowingCacheService.exists(userId))
            return largeFollowingCacheService.getLargeFollowingIds(userId);

        List<UUID> largeAuthorIds = fetchLargeFollowingIds(userId);
        largeFollowingCacheService.addLargeFollowing(userId, largeAuthorIds);
        return largeAuthorIds;
    }

    /**
     * 대형 크리에이터 게시글 ID 목록을 조회하는 메서드
     * - 캐시 확인 후 없으면 DB 조회
     */
    private List<UUID> fetchLargeFollowingPostIds(List<UUID> followingIds, UUID cursor) {
        if (followingIds.isEmpty()) return List.of();

        Map<UUID, List<UUID>> batchResult = postListCacheService.getPostIdsBatch(followingIds, cursor);

        // 캐시 미스 ID 수집
        List<UUID> result = new ArrayList<>();
        List<UUID> missIds = new ArrayList<>();
        batchResult.forEach((authorId, ids) -> {
            if (ids != null) result.addAll(ids);
            else missIds.add(authorId);
        });

        // DB 조회
        if (!missIds.isEmpty())
            result.addAll(postRepository.findIdsByCursorAndAuthorIdIn(cursor, missIds));

        return result;
    }

    /** User 서비스에서 대형 크리에이터 팔로잉 목록을 조회하는 메서드 */
    private List<UUID> fetchLargeFollowingIds(UUID userId) {
        List<UUID> result = new ArrayList<>();
        UUID nextCursor = null;
        do {
            CursorPageResponse<UUID> page = userClient.getLargeFollowingIds(userId, nextCursor);
            result.addAll(page.content());
            nextCursor = page.hasNext() ? page.nextCursor() : null;
        } while (nextCursor != null);
        return result;
    }

    /** User 서비스에서 모든 크리에이터 팔로잉 목록을 조회하는 메서드 */
    private List<FollowingResponse> fetchAllFollowingIds(UUID userId) {
        List<FollowingResponse> result = new ArrayList<>();
        UUID nextCursor = null;
        do {
            CursorPageResponse<FollowingResponse> page = userClient.getFollowingIds(userId, nextCursor);
            result.addAll(page.content());
            nextCursor = page.hasNext() ? page.nextCursor() : null;
        } while (nextCursor != null);
        return result;
    }

    /** 대형 크리에이터의 게시글을 조회하는 메서드 (feed:posts 캐시 우선, 미스 시 DB) */
    private List<Post> fetchLargeAuthorPostEntities(List<UUID> largeAuthorIds, UUID cursor) {
        if (largeAuthorIds.isEmpty()) return List.of();

        Map<UUID, List<UUID>> batchResult = postListCacheService.getPostIdsBatch(largeAuthorIds, cursor);

        List<UUID> cachedPostIds = new ArrayList<>();
        List<UUID> needDbFetch = new ArrayList<>();

        batchResult.forEach((authorId, ids) -> {
            if (ids != null) cachedPostIds.addAll(ids);
            else needDbFetch.add(authorId);
        });

        List<Post> result = new ArrayList<>();

        // 캐시에서 얻은 ID는 엔티티로 재조회 (응답 조립에 엔티티가 필요하므로)
        if (!cachedPostIds.isEmpty()) {
            result.addAll(postRepository.findAllById(cachedPostIds));
        }

        // 캐시 미스 작가는 DB에서 바로 엔티티로 조회
        if (!needDbFetch.isEmpty()) {
            result.addAll(postRepository.findByCursorAndAuthorIdIn(cursor, needDbFetch));
        }

        return result;
    }

    /**
     * 모든 크리에이터의 게시글을 병합해 첫 페이지만 반환하는 메서드<br>
     * - 다음 페이지 존재 여부를 구분하기 위한 정보 포함
     */
    private <T> List<T> mergeSorted(List<T> smallAuthorPosts, List<T> largeAuthorPosts, Function<T, UUID> idExtractor) {
        List<T> merged = Stream.concat(smallAuthorPosts.stream(), largeAuthorPosts.stream())
                .collect(Collectors.toMap(idExtractor, Function.identity(), (t1, t2) -> t1, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparingLong((T t) -> UuidV7TimestampExtractor.extract(idExtractor.apply(t))).reversed())
                .toList();

        return merged.size() > FeedPolicy.PAGE_SIZE + 1
                ? merged.subList(0, FeedPolicy.PAGE_SIZE + 1)
                : merged;
    }
}