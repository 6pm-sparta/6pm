package com.fandom.feed.application.service;

import com.fandom.feed.domain.entity.Image;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.util.ImageUrlConverter;
import com.fandom.feed.infra.repository.ImageRepository;
import com.fandom.feed.infra.repository.PostRepository;
import com.fandom.feed.presentation.dto.response.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;
    private final ImageRepository imageRepository;
    private final ImageUrlConverter imageUrlConverter;

    @Transactional
    public PostResponse.Create create(String content, List<String> imageKeys, UUID userId) {
        Post post = Post.builder().authorId(userId).content(content).build();
        postRepository.save(post);

        saveImages(post.getId(), imageKeys);

        return PostResponse.Create.of(post, imageUrlConverter.toImageUrls(imageKeys));
    }

    /**
     * Image 객체를 생성한 후, 저장하는 메서드
     * @param postId 게시글 식별자
     * @param imageKeys S3 이미지 키
     */
    private void saveImages(UUID postId, @NonNull List<String> imageKeys) {
        if (imageKeys.isEmpty()) return;

        List<Image> images = IntStream.range(0, imageKeys.size())
                .mapToObj(i -> Image.builder()
                        .postId(postId)
                        .order(i + 1)
                        .imageKey(imageKeys.get(i))
                        .build())
                .toList();
        imageRepository.saveAll(images);
    }
}