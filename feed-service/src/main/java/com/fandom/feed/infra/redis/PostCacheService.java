package com.fandom.feed.infra.redis;

import com.fandom.feed.application.PostReader;
import com.fandom.feed.domain.entity.Image;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.ImageRepository;
import com.fandom.feed.infra.util.ImageUrlConverter;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.dto.PostCache;
import com.fandom.feed.presentation.dto.response.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostCacheService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final PostReader postReader;
    private final ImageRepository imageRepository;
    private final ImageUrlConverter imageUrlConverter;
    private final UserClient userClient;

    private static final String POST_DETAIL_KEY = "post:detail:";
    
    private static final Duration POST_DETAIL_TTL = Duration.ofHours(1);

    public PostResponse.Detail getPostDetail(UUID id) {
        String key = POST_DETAIL_KEY + id;
        PostCache.Detail cachedPost = (PostCache.Detail) redisTemplate.opsForValue().get(key);

        // 캐시 미스 발생
        if (cachedPost == null) {
            Post post = postReader.findById(id);
            List<String> imageKeys = imageRepository.findAllByPostIdOrderByOrderAsc(id).stream().map(Image::getImageKey).toList();
            UserResponse author = userClient.getUser(post.getAuthorId()).getData();

            cachedPost = PostCache.Detail.of(post, imageUrlConverter.toImageUrls(imageKeys), author);
            redisTemplate.opsForValue().set(key, cachedPost, POST_DETAIL_TTL);

            return PostResponse.Detail.of(cachedPost, post.getCommentCount());
        }

        return PostResponse.Detail.of(cachedPost, 0L);
    }
}