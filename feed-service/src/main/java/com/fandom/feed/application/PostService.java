package com.fandom.feed.application;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.global.constant.ReactionSort;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.PostErrorCode;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.global.constant.RedisKeyPrefix;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.PostCacheService;
import com.fandom.feed.infra.redis.PostListCacheService;
import com.fandom.feed.infra.redis.ReactionCacheService;
import com.fandom.feed.infra.util.ImageUrlConverter;
import com.fandom.feed.infra.redis.dto.PostCache;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import com.fandom.feed.presentation.dto.response.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostReader postReader;
    private final ImageService imageService;
    private final PostRepository postRepository;
    private final PostCacheService postCacheService;
    private final PostListCacheService postListCacheService;
    private final ReactionCacheService reactionCacheService;
    private final ImageUrlConverter imageUrlConverter;
    private final UserClient userClient;

    @Transactional
    public PostResponse.Create createPost(String content, List<String> imageKeys, UUID userId) {
        Post post = Post.builder().authorId(userId).content(content).build();
        postRepository.save(post);

        imageService.saveImages(post.getId(), imageKeys);

        return PostResponse.Create.of(post, imageUrlConverter.toImageUrls(imageKeys));
    }

    public PostResponse.Detail getPost(UUID postId, UUID userId) {
        PostCache.Detail cachedPost = postCacheService.getPostDetail(postId);
        PostCache.ReactionInfo reactionInfo = reactionCacheService.getReactionInfo(postId, userId);
        return PostResponse.Detail.of(cachedPost, reactionInfo);
    }

    public CursorPageResponse<PostResponse.Summary> getPosts(
            UUID cursor, ReactionSort sort, UUID authorId, String keyword, UUID userId
    ) {
        // 검색 조건이 있으면 DB 조회
        if (authorId != null || keyword != null)
            return getPostsFromDB(cursor, sort, authorId, keyword, userId);

        // 캐시가 없으면 DB 조회 후 캐시 워밍업
        if (!postListCacheService.isCacheReady(sort))
            return getPostsFromDBAndWarm(sort, userId);

        // 캐시 조회 후 5페이지 초과 시 DB 조회
        List<UUID> postIds = postListCacheService.getPostIds(sort, cursor);
        if (postIds == null)
            return getPostsFromDB(cursor, sort, null, null, userId);

        return buildCacheResponse(postIds, userId);
    }

    @Transactional
    @CacheEvict(value = RedisKeyPrefix.POST_DETAIL, key = "#postId")
    public PostResponse.Update updatePost(UUID postId, String content, List<String> imageKeys, UUID userId) {
        Post post = postReader.findById(postId);

        // 권한 검증
        if (!userId.equals(post.getAuthorId()))
            throw new CustomException(PostErrorCode.FORBIDDEN_POST_UPDATE);

        post.update(content);

        List<String> finalImageKeys = imageService.syncImages(postId, imageKeys);

        return PostResponse.Update.of(post, imageUrlConverter.toImageUrls(finalImageKeys));
    }

    @Transactional
    @CacheEvict(value = RedisKeyPrefix.POST_DETAIL, key = "#postId")
    public PostResponse.Delete deletePost(UUID postId, UUID userId, boolean isMaster) {
        Post post = postReader.findById(postId);

        // 권한 검증
        if (!userId.equals(post.getAuthorId()) && !isMaster)
            throw new CustomException(PostErrorCode.FORBIDDEN_POST_DELETE);

        List<String> imageKeys = imageService.findAllByPostId(postId);

        post.softDelete(userId);

        imageService.deleteAllByPostId(postId);
        imageService.publishS3DeleteEvent(imageKeys);

        return PostResponse.Delete.from(post);
    }

    /**
     * 캐시에서 가져온 게시글 ID 목록으로 응답을 구성하는 메서드
     */
    private CursorPageResponse<PostResponse.Summary> buildCacheResponse(List<UUID> postIds, UUID userId) {
        boolean hasMore = postIds.size() > FeedPolicy.PAGE_SIZE;
        List<UUID> pageIds = hasMore ? postIds.subList(0, FeedPolicy.PAGE_SIZE) : postIds;

        List<PostCache.Detail> posts = postCacheService.getPostDetailBatch(pageIds);
        List<PostCache.ReactionInfo> reactionInfos = reactionCacheService.getReactionInfoBatch(pageIds, userId);

        List<PostResponse.Summary> summaries = IntStream.range(0, pageIds.size())
                .mapToObj(i -> PostResponse.Summary.of(posts.get(i), reactionInfos.get(i)))
                .toList();

        UUID nextCursor = hasMore ? pageIds.getLast() : null;
        return CursorPageResponse.of(summaries, nextCursor, hasMore);
    }

    /**
     * DB에서 게시글 목록을 가져오는 메서드
     */
    private CursorPageResponse<PostResponse.Summary> getPostsFromDB(
            UUID cursor, ReactionSort sort, UUID authorId, String keyword, UUID userId
    ) {
        List<Post> posts = postRepository.findByCursor(cursor, sort, authorId, keyword);

        boolean hasMore = posts.size() > FeedPolicy.PAGE_SIZE;
        List<Post> page = hasMore ? posts.subList(0, FeedPolicy.PAGE_SIZE) : posts;

        return buildDBResponse(page, hasMore, userId);
    }

    /**
     * DB에서 게시글 100개를 가져와 캐시에 저장한 후, 첫 페이지를 반환하는 메서드
     */
    private CursorPageResponse<PostResponse.Summary> getPostsFromDBAndWarm(ReactionSort sort, UUID userId) {
        List<Post> allPosts = postRepository.findByCursorForWarm(sort);

        allPosts.forEach(post -> postListCacheService.addPost(post.getId(), sort));

        postListCacheService.expireCache(sort);

        boolean hasMore = allPosts.size() > FeedPolicy.PAGE_SIZE;
        List<Post> page = hasMore ? allPosts.subList(0, FeedPolicy.PAGE_SIZE) : allPosts;

        return buildDBResponse(page, hasMore, userId);
    }

    /**
     * Post 엔티티로 응답을 구성하는 메서드
     */
    private CursorPageResponse<PostResponse.Summary> buildDBResponse(List<Post> page, boolean hasMore, UUID userId) {
        List<UUID> postIds = page.stream().map(Post::getId).toList();

        // 배치 조회
        Map<UUID, List<String>> imageUrlsMap = imageService.findAllByPostIds(postIds);

        Set<UUID> authorIds = page.stream().map(Post::getAuthorId).collect(Collectors.toSet());
        Map<UUID, UserResponse> authorMap = userClient.getUsers(authorIds).getData()
                .stream().collect(Collectors.toMap(UserResponse::userId, Function.identity()));

        List<PostCache.ReactionInfo> reactionInfos = reactionCacheService.getReactionInfoBatch(postIds, userId);

        // DTO 조립
        List<PostCache.Detail> details = page.stream()
                .map(post -> PostCache.Detail.of(
                        post, imageUrlsMap.getOrDefault(post.getId(), List.of()), authorMap.get(post.getAuthorId())
                ))
                .toList();

        List<PostResponse.Summary> summaries = IntStream.range(0, page.size())
                .mapToObj(i -> PostResponse.Summary.of(details.get(i), reactionInfos.get(i)))
                .toList();

        UUID nextCursor = hasMore ? page.getLast().getId() : null;
        return CursorPageResponse.of(summaries, nextCursor, hasMore);
    }
}