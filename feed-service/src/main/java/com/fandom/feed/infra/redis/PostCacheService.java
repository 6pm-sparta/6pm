package com.fandom.feed.infra.redis;

import com.fandom.feed.application.PostReader;
import com.fandom.feed.domain.entity.Image;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.ImageRepository;
import com.fandom.feed.infra.redis.config.RedisKeyPrefix;
import com.fandom.feed.infra.util.ImageUrlConverter;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.dto.PostCache;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostCacheService {
    private final PostReader postReader;
    private final ImageRepository imageRepository;
    private final ImageUrlConverter imageUrlConverter;
    private final UserClient userClient;

    @Cacheable(value = RedisKeyPrefix.POST_DETAIL, key = "#id")
    public PostCache.Detail getPostDetail(UUID id) {
        Post post = postReader.findById(id);
        List<String> imageKeys = imageRepository.findAllByPostIdOrderByOrderIndexAsc(id).stream().map(Image::getImageKey).toList();
        UserResponse author = userClient.getUser(post.getAuthorId()).getData();

        return PostCache.Detail.of(post, imageUrlConverter.toImageUrls(imageKeys), author);
    }
}