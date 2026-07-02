package com.fandom.feed.application;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.event.Event;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.PostErrorCode;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.kafka.outbox.OutboxEventType;
import com.fandom.feed.infra.kafka.outbox.OutboxEventWriter;
import com.fandom.feed.infra.redis.PostDetailCacheService;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.PostListCacheService;
import com.fandom.feed.infra.redis.ReactionCacheService;
import com.fandom.feed.infra.redis.dto.PostDetailCache;
import com.fandom.feed.infra.redis.dto.ReactionInfoCache;
import com.fandom.feed.infra.s3.util.ImageUrlConverter;
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
    private final ImageService imageService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final PostRepository postRepository;
    private final PostDetailCacheService postDetailCacheService;
    private final PostListCacheService postListCacheService;
    private final ReactionCacheService reactionCacheService;
    private final ImageUrlConverter imageUrlConverter;
    private final OutboxEventWriter outboxEventWriter;
    private final UserClient userClient;

    @Transactional
    public PostResponse.Create createPost(String content, List<String> imageKeys, UUID userId) {
        Post post = Post.builder().authorId(userId).content(content).build();
        postRepository.save(post);

        UUID postId = post.getId();

        imageService.saveImages(postId, imageKeys);
        postListCacheService.addPost(postId, post.getAuthorId());

        // 알람 발행에 게시글 생성 시 닉네임 사용
        String nickname = userClient.getUser(userId).getData().nickname();
        outboxEventWriter.write(postId, OutboxEventType.POST_CREATED, new Event.PostCreated(postId, userId, nickname));

        return PostResponse.Create.of(post, imageUrlConverter.toImageUrls(imageKeys));
    }

    public PostResponse.Detail getPost(UUID postId, UUID userId) {
        PostDetailCache cachedPost = postDetailCacheService.getPostDetail(postId);
        ReactionInfoCache reactionInfo = reactionCacheService.getReactionInfo(postId, userId);
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

        outboxEventWriter.write(postId, OutboxEventType.POST_DELETED, new Event.PostDeleted(postId, userId));

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
        List<Post> posts = postRepository.findByCursorForWarm(authorId);

        posts.forEach(post -> postListCacheService.addPostForWarm(post.getId(), authorId));

        postListCacheService.expireCache(authorId);

        boolean hasMore = posts.size() > FeedPolicy.PAGE_SIZE;
        List<Post> page = hasMore ? posts.subList(0, FeedPolicy.PAGE_SIZE) : posts;

        UUID nextCursor = hasMore ? page.getLast().getId() : null;
        return postAssembler.buildDBResponse(page, nextCursor, hasMore, userId, false);
    }

    /**
     * 작성자 ID로 모든 게시글을 삭제하는 메서드
     */
    @Transactional
    public void deleteAllByAuthorId(UUID authorId) {
        List<UUID> postIds = postRepository.findAllIdsByAuthorId(authorId);

        if (postIds.isEmpty()) return;

        commentService.deleteAllByPostIds(postIds, authorId);
        likeService.deleteAllByPostIds(postIds);

        postRepository.softDeleteAllByAuthorId(authorId);

        List<String> imageKeys = imageService.findAllKeysByPostIds(postIds);
        imageService.deleteAllByPostIds(postIds);
        imageService.publishS3DeleteEvent(imageKeys);

        outboxEventWriter.writeAll(postIds, OutboxEventType.POST_DELETED, postId -> new Event.PostDeleted(postId, authorId));

        postListCacheService.removeAllByAuthorId(postIds, authorId);
        postDetailCacheService.deleteAll(postIds);
    }
}