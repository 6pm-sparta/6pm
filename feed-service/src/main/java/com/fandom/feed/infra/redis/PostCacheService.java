package com.fandom.feed.infra.redis;

import com.fandom.feed.application.ImageService;
import com.fandom.feed.application.PostReader;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.s3.util.ImageUrlConverter;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.dto.PostCache;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostCacheService {
    private final PostReader postReader;
    private final ImageService imageService;
    private final ImageUrlConverter imageUrlConverter;
    private final UserClient userClient;

    private final CacheManager cacheManager;

    /**
     * 게시글 ID로 캐시에서 게시글 상세를 조회하는 메서드<br>
     * - 캐시 미스 발생 시, DB 조회 후 캐시에 저장
     */
    @Cacheable(value = RedisKeyPrefix.POST_DETAIL, key = "#postId")
    public PostCache.Detail getPostDetail(UUID postId) {
        Post post = postReader.findById(postId);
        List<String> imageKeys = imageService.findAllByPostId(postId);
        UserResponse author = userClient.getUser(post.getAuthorId()).getData();

        return PostCache.Detail.of(post, imageUrlConverter.toImageUrls(imageKeys), author);
    }

    /**
     * 게시글 ID 목록으로 캐시에서 게시글 상세를 배치 조회하는 메서드
     * - 캐시 미스 발생 시, DB 조회 후 캐시에 저장
     */
    public List<PostCache.Detail> getPostDetailBatch(List<UUID> postIds) {
        Cache cache = cacheManager.getCache(RedisKeyPrefix.POST_DETAIL);

        // 캐시 히트/미스 분류
        Map<UUID, PostCache.Detail> cachedMap = new HashMap<>();
        List<UUID> missIds = new ArrayList<>();

        postIds.forEach(id -> {
            PostCache.Detail cached = (cache != null) ? cache.get(id, PostCache.Detail.class) : null;
            if (cached != null) cachedMap.put(id, cached);
            else missIds.add(id);
        });

        // 캐시 미스 배치 조회
        if (!missIds.isEmpty()) {
            Map<UUID, Post> postMap = postReader.findAllByIds(missIds)
                    .stream().collect(Collectors.toMap(Post::getId, Function.identity()));

            Map<UUID, List<String>> imageUrlsMap = imageService.findAllByPostIds(missIds);

            Set<UUID> authorIds = postMap.values().stream().map(Post::getAuthorId).collect(Collectors.toSet());
            Map<UUID, UserResponse> authorMap = userClient.getUsers(authorIds).getData()
                    .stream().collect(Collectors.toMap(UserResponse::userId, Function.identity()));

            // 캐시에 저장하면서 cachedMap에 추가
            missIds.forEach(id -> {
                Post post = postMap.get(id);
                PostCache.Detail detail = PostCache.Detail.of(
                        post,
                        imageUrlsMap.getOrDefault(id, List.of()),
                        authorMap.get(post.getAuthorId())
                );
                if (cache != null) cache.put(id, detail);
                cachedMap.put(id, detail);
            });
        }

        return postIds.stream().map(cachedMap::get).toList();
    }
}