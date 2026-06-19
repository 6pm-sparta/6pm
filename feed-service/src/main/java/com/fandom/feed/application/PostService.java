package com.fandom.feed.application;

import com.fandom.feed.application.policy.PostSort;
import com.fandom.feed.domain.entity.Image;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.domain.repository.ImageRepository;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.PostCacheService;
import com.fandom.feed.infra.redis.PostListCacheService;
import com.fandom.feed.infra.util.ImageUrlConverter;
import com.fandom.feed.infra.redis.PostReactionService;
import com.fandom.feed.infra.redis.dto.PostCache;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import com.fandom.feed.presentation.dto.response.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.fandom.feed.application.policy.PostPolicy.PAGE_SIZE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;
    private final ImageRepository imageRepository;
    private final PostCacheService postCacheService;
    private final PostListCacheService postListCacheService;
    private final PostReactionService postReactionService;
    private final ImageUrlConverter imageUrlConverter;
    private final UserClient userClient;

    @Transactional
    public PostResponse.Create createPost(String content, List<String> imageKeys, UUID userId) {
        Post post = Post.builder().authorId(userId).content(content).build();
        postRepository.save(post);

        saveImages(post.getId(), imageKeys);

        return PostResponse.Create.of(post, imageUrlConverter.toImageUrls(imageKeys));
    }

    public PostResponse.Detail getPost(UUID postId, UUID userId) {
        PostCache.Detail cachedPost = postCacheService.getPostDetail(postId);
        PostCache.ReactionInfo reactionInfo = postReactionService.getReactionInfo(postId, userId);
        return PostResponse.Detail.of(cachedPost, reactionInfo);
    }

    public CursorPageResponse<PostResponse.Summary> getPosts(
            UUID cursor, PostSort sort, UUID authorId, String keyword, UUID userId
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

    /**
     * 캐시에서 가져온 게시글 ID 목록으로 응답을 구성하는 메서드
     */
    private CursorPageResponse<PostResponse.Summary> buildCacheResponse(List<UUID> postIds, UUID userId) {
        boolean hasMore = postIds.size() > PAGE_SIZE;
        List<UUID> pageIds = hasMore ? postIds.subList(0, PAGE_SIZE) : postIds;

        List<PostCache.Detail> posts = postCacheService.getPostDetailBatch(pageIds);
        List<PostCache.ReactionInfo> reactionInfos = postReactionService.getReactionInfoBatch(pageIds, userId);

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
            UUID cursor, PostSort sort, UUID authorId, String keyword, UUID userId
    ) {
        List<Post> posts = postRepository.findByCursor(cursor, sort, authorId, keyword);

        boolean hasMore = posts.size() > PAGE_SIZE;
        List<Post> page = hasMore ? posts.subList(0, PAGE_SIZE) : posts;

        return buildDBResponse(page, hasMore, userId);
    }

    /**
     * DB에서 게시글 100개를 가져와 캐시에 저장한 뒤, 첫 페이지를 반환하는 메서드
     */
    private CursorPageResponse<PostResponse.Summary> getPostsFromDBAndWarm(PostSort sort, UUID userId) {
        List<Post> allPosts = postRepository.findByCursorForWarm(sort);

        allPosts.forEach(post -> postListCacheService.addPost(post.getId(), post.getCreatedAt()));

        boolean hasMore = allPosts.size() > PAGE_SIZE;
        List<Post> page = hasMore ? allPosts.subList(0, PAGE_SIZE) : allPosts;

        return buildDBResponse(page, hasMore, userId);
    }

    /**
     * Post 엔티티로 응답을 구성하는 메서드
     */
    private CursorPageResponse<PostResponse.Summary> buildDBResponse(List<Post> page, boolean hasMore, UUID userId) {
        List<UUID> postIds = page.stream().map(Post::getId).toList();

        // 1. 배치 조회
        Map<UUID, List<String>> imageUrlsMap = imageRepository
                .findAllByPostIdInOrderByOrderIndexAsc(postIds)
                .stream()
                .collect(Collectors.groupingBy(
                        Image::getPostId,
                        Collectors.mapping(
                                image -> imageUrlConverter.toImageUrl(image.getImageKey()),
                                Collectors.toList()
                        )
                ));

        Set<UUID> authorIds = page.stream().map(Post::getAuthorId).collect(Collectors.toSet());
        Map<UUID, UserResponse> authorMap = userClient.getUsers(authorIds).getData()
                .stream().collect(Collectors.toMap(UserResponse::userId, Function.identity()));

        List<PostCache.ReactionInfo> reactionInfos = postReactionService.getReactionInfoBatch(postIds, userId);

        // 2. DTO 조립
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

    /**
     * Image 객체를 생성한 후, 저장하는 메서드
     */
    private void saveImages(UUID postId, List<String> imageKeys) {
        if (imageKeys.isEmpty()) return;

        List<Image> images = IntStream.range(0, imageKeys.size())
                .mapToObj(i -> Image.builder()
                        .postId(postId)
                        .orderIndex(i + 1)
                        .imageKey(imageKeys.get(i))
                        .build())
                .toList();
        imageRepository.saveAll(images);
    }
}