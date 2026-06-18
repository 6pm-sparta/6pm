package com.fandom.feed.application;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.domain.repository.ImageRepository;
import com.fandom.feed.infra.util.ImageUrlConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock
    private PostRepository postRepository;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ImageUrlConverter imageUrlConverter;

    @InjectMocks
    private PostService postService;

    @Nested
    @DisplayName("게시글 생성")
    class CreatePost {
        @Test
        @DisplayName("이미지 포함 - 이미지 저장 로직 실행")
        void createPostWithImagesTest() {
            // given
            UUID userId = UUID.randomUUID();
            String content = "테스트 게시글 내용";
            List<String> imageKeys = List.of("key1", "key2");

            // when
            postService.createPost(content, imageKeys, userId);

            // then
            verify(postRepository, times(1)).save(any(Post.class));
            verify(imageRepository, times(1)).saveAll(any());
            verify(imageUrlConverter, times(1)).toImageUrls(imageKeys);
        }

        @Test
        @DisplayName("이미지 미포함 - 이미지 저장 로직 미실행")
        void createPostWithoutImagesTest() {
            // given
            UUID userId = UUID.randomUUID();
            String content = "이미지 없는 게시글";
            List<String> imageKeys = List.of();

            // when
            postService.createPost(content, imageKeys, userId);

            // then
            verify(postRepository, times(1)).save(any(Post.class));
            verify(imageRepository, never()).saveAll(any());
        }
    }
}