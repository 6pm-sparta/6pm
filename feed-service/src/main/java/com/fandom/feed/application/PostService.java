package com.fandom.feed.application;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.PostErrorCode;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.global.constant.RedisKeyPrefix;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostReader postReader;
    private final PostAssembler postAssembler;
    private final PostRepository postRepository;
    private final ImageService imageService;
    private final PostCacheService postCacheService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final PostListCacheService postListCacheService;
    private final ReactionCacheService reactionCacheService;
    private final ImageUrlConverter imageUrlConverter;

    @Transactional
    public PostResponse.Create createPost(String content, List<String> imageKeys, UUID userId) {
        Post post = Post.builder().authorId(userId).content(content).build();
        postRepository.save(post);

        imageService.saveImages(post.getId(), imageKeys);

        postListCacheService.addPost(post.getId(), post.getAuthorId());

        return PostResponse.Create.of(post, imageUrlConverter.toImageUrls(imageKeys));
    }

    public PostResponse.Detail getPost(UUID postId, UUID userId) {
        PostCache.Detail cachedPost = postCacheService.getPostDetail(postId);
        PostCache.ReactionInfo reactionInfo = reactionCacheService.getReactionInfo(postId, userId);
        return PostResponse.Detail.of(cachedPost, reactionInfo);
    }

    public CursorPageResponse<PostResponse.Summary> getPosts(UUID cursor, UUID authorId, String keyword, UUID userId) {
        // 검색어 있으면 DB 조회
        if (keyword != null)
            return getPostsFromDB(cursor, authorId, keyword, userId);

        // 캐시 없으면 DB 조회 후 캐시 워밍업
        if (!postListCacheService.isCacheReady(authorId))
            return getPostsFromDBAndWarm(authorId, userId);

        // 캐시 조회 후 5페이지 초과 시 DB 조회
        List<UUID> postIds = postListCacheService.getPostIds(authorId, cursor);
        if (postIds == null)
            return getPostsFromDB(cursor, null, null, userId);

        return postAssembler.buildCacheResponse(postIds, userId);
    }

    @Transactional
    @CacheEvict(value = RedisKeyPrefix.POST_DETAIL, key = "#postId")
    public PostResponse.Update updatePost(UUID postId, String content, List<String> imageKeys, UserIdCard idCard) {
        Post post = postReader.findById(postId);

        // 권한 검증
        if (!idCard.isMe(post.getAuthorId()))
            throw new CustomException(PostErrorCode.FORBIDDEN_POST_UPDATE);

        post.update(content);

        List<String> finalImageKeys = imageService.syncImages(postId, imageKeys);

        return PostResponse.Update.of(post, imageUrlConverter.toImageUrls(finalImageKeys));
    }

    @Transactional
    @CacheEvict(value = RedisKeyPrefix.POST_DETAIL, key = "#postId")
    public PostResponse.Delete deletePost(UUID postId, UserIdCard idCard) {
        Post post = postReader.findById(postId);
        UUID userId = idCard.getUserId();

        // 권한 검증
        if (!idCard.isMe(post.getAuthorId()) && !idCard.isMaster())
            throw new CustomException(PostErrorCode.FORBIDDEN_POST_DELETE);

        commentService.deleteAllByPostId(postId, userId);
        likeService.deleteAllByPostId(postId);

        post.softDelete(userId);

        List<String> imageKeys = imageService.findAllByPostId(postId);

        imageService.deleteAllByPostId(postId);
        imageService.publishS3DeleteEvent(imageKeys);

        postListCacheService.removePost(postId, post.getAuthorId());

        return PostResponse.Delete.from(post);
    }

    /**
     * DB에서 게시글 목록을 가져오는 메서드
     */
    private CursorPageResponse<PostResponse.Summary> getPostsFromDB(UUID cursor, UUID authorId, String keyword, UUID userId) {
        List<Post> posts = postRepository.findByCursor(cursor, authorId, keyword);

        boolean hasMore = posts.size() > FeedPolicy.PAGE_SIZE;
        List<Post> page = hasMore ? posts.subList(0, FeedPolicy.PAGE_SIZE) : posts;

        UUID nextCursor = hasMore ? page.getLast().getId() : null;
        return postAssembler.buildDBResponse(page, nextCursor, hasMore, userId, false);
    }

    /**
     * DB에서 게시글 100개를 가져와 캐시에 저장한 후, 첫 페이지를 반환하는 메서드
     */
    private CursorPageResponse<PostResponse.Summary> getPostsFromDBAndWarm(UUID authorId, UUID userId) {
        List<Post> allPosts = postRepository.findByCursorForWarm(authorId);

        allPosts.forEach(post -> postListCacheService.addPostForWarm(post.getId(), authorId));

        postListCacheService.expireCache(authorId);

        boolean hasMore = allPosts.size() > FeedPolicy.PAGE_SIZE;
        List<Post> page = hasMore ? allPosts.subList(0, FeedPolicy.PAGE_SIZE) : allPosts;

        UUID nextCursor = hasMore ? page.getLast().getId() : null;
        return postAssembler.buildDBResponse(page, nextCursor, hasMore, userId, false);
    }
}