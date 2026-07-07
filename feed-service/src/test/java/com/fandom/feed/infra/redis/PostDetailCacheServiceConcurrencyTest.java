package com.fandom.feed.infra.redis;

import com.fandom.feed.application.ImageService;
import com.fandom.feed.application.PostReader;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.infra.client.UserClientRetryWrapper;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.config.RedisCacheConfig;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.dto.PostDetailCache;
import com.fandom.feed.infra.s3.util.ImageUrlConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataRedisTest
@TestPropertySource(properties = {"cache.ttl.default=600", "cache.ttl.post-detail=3600"})
@Import({RedisCacheConfig.class, JacksonAutoConfiguration.class})
class PostDetailCacheServiceConcurrencyTest {
    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private PostReader postReader;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private UserClientRetryWrapper userClient;

    private PostDetailCacheService postDetailCacheService;

    @BeforeEach
    void setUp() {
        ImageUrlConverter converter = new ImageUrlConverter();
        ReflectionTestUtils.setField(converter, "baseUrl", "https://dummy.s3.example.com/");

        postDetailCacheService = new PostDetailCacheService(postReader, imageService, userClient, converter, cacheManager, null);
    }

    @Test
    @DisplayName("동시 다발 요청 시 캐시된 imageUrls가 깨지지 않아야 함")
    void concurrentBatchAccessShouldNotCorruptImageUrls() throws InterruptedException {
        // given
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        List<UUID> postIds = List.of(postId);
        List<String> expectedUrls = List.of("dc8d5942.webp", "5225ad6e.png", "a7c5856c.webp");

        Post post = mock(Post.class);
        when(post.getId()).thenReturn(postId);
        when(post.getAuthorId()).thenReturn(authorId);
        when(post.getContent()).thenReturn("테스트 게시글");
        when(post.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(post.getUpdatedAt()).thenReturn(null);

        when(postReader.findAllByIds(anyList())).thenReturn(List.of(post));
        when(imageService.findAllByPostIds(anyList())).thenReturn(Map.of(postId, expectedUrls));
        when(userClient.getUsers(anySet())).thenReturn(List.of(new UserResponse(authorId, "tester")));

        int threadCount = 200;
        int iterations = 20;
        List<String> corrupted = Collections.synchronizedList(new ArrayList<>());

        for (int iter = 0; iter < iterations; iter++) {
            Objects.requireNonNull(cacheManager.getCache(RedisKeyPrefix.POST_DETAIL)).evict(postId);

            try (ExecutorService pool = Executors.newFixedThreadPool(threadCount)) {
                CountDownLatch latch = new CountDownLatch(threadCount);

                for (int i = 0; i < threadCount; i++) {
                    int finalIter = iter;
                    pool.submit(() -> {
                        try {
                            List<PostDetailCache> result = postDetailCacheService.getPostDetailBatch(postIds);
                            PostDetailCache detail = result.getFirst();
                            if (!expectedUrls.equals(detail.imageUrls()))
                                corrupted.add("iter=" + finalIter + " got=" + detail.imageUrls());
                        } catch (Exception e) {
                            corrupted.add("iter=" + finalIter + " exception=" + e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await(10, TimeUnit.SECONDS);
                pool.shutdown();
            }
        }

        // then
        if (!corrupted.isEmpty()) {
            System.out.println("=== 재현된 오염 사례 ===");
            corrupted.forEach(System.out::println);
        }
        assertThat(corrupted).isEmpty();
    }

    @Test
    @DisplayName("여러 게시글 배치 조회를 동시에 반복해도 imageUrls가 섞이지 않아야 함")
    void concurrentBatchAccessWithManyDifferentPostsShouldNotCorrupt() throws InterruptedException {
        // given
        int postCount = 50;
        Map<UUID, List<String>> expectedByPostId = new HashMap<>();
        Map<UUID, UUID> authorByPostId = new HashMap<>();
        List<Post> posts = new ArrayList<>();

        for (int i = 0; i < postCount; i++) {
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            List<String> urls = List.of("img-" + i + "-a.webp", "img-" + i + "-b.png", "img-" + i + "-c.webp");
            expectedByPostId.put(postId, urls);
            authorByPostId.put(postId, authorId);

            Post post = mock(Post.class);
            when(post.getId()).thenReturn(postId);
            when(post.getAuthorId()).thenReturn(authorId);
            when(post.getContent()).thenReturn("게시글 " + i);
            when(post.getCreatedAt()).thenReturn(LocalDateTime.now());
            when(post.getUpdatedAt()).thenReturn(null);
            posts.add(post);
        }

        List<UUID> postIds = new ArrayList<>(expectedByPostId.keySet());

        when(postReader.findAllByIds(anyList())).thenReturn(posts);
        when(imageService.findAllByPostIds(anyList())).thenReturn(expectedByPostId);
        when(userClient.getUsers(anySet())).thenAnswer(invocation -> {
            Set<UUID> requestedAuthorIds = invocation.getArgument(0);
            return requestedAuthorIds.stream().map(id -> new UserResponse(id, "user-" + id)).toList();
        });

        int threadCount = 200;
        int iterations = 10;
        List<String> corrupted = Collections.synchronizedList(new ArrayList<>());

        for (int iter = 0; iter < iterations; iter++) {
            postIds.forEach(id -> Objects.requireNonNull(cacheManager.getCache(RedisKeyPrefix.POST_DETAIL)).evict(id));

            try (ExecutorService pool = Executors.newFixedThreadPool(threadCount)) {
                CountDownLatch latch = new CountDownLatch(threadCount);
                int finalIter = iter;

                for (int i = 0; i < threadCount; i++) {
                    pool.submit(() -> {
                        try {
                            List<PostDetailCache> result = postDetailCacheService.getPostDetailBatch(postIds);
                            for (PostDetailCache detail : result) {
                                List<String> expected = expectedByPostId.get(detail.postId());
                                if (!expected.equals(detail.imageUrls())) {
                                    corrupted.add("iter=" + finalIter
                                            + " postId=" + detail.postId()
                                            + " expected=" + expected
                                            + " got=" + detail.imageUrls());
                                }
                            }
                        } catch (Exception e) {
                            corrupted.add("iter=" + finalIter + " exception=" + e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await(15, TimeUnit.SECONDS);
                pool.shutdown();
            }
        }

        if (!corrupted.isEmpty()) {
            System.out.println("=== 재현된 오염 사례 (" + corrupted.size() + "건) ===");
            corrupted.stream().limit(20).forEach(System.out::println);
        }
        assertThat(corrupted).isEmpty();
    }
}