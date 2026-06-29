package com.fandom.feed.application;

import com.fandom.feed.application.event.Event;
import com.fandom.feed.domain.entity.Image;
import com.fandom.feed.domain.repository.ImageRepository;
import com.fandom.feed.infra.s3.util.ImageUrlConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {
    @InjectMocks
    private ImageService imageService;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ImageUrlConverter imageUrlConverter;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Nested
    @DisplayName("게시글 ID로 이미지 키 목록 조회")
    class FindAllByPostId {
        @Test
        @DisplayName("이미지 있음 - imageKeys 목록 반환")
        void findAllByPostIdImagesInDB() {
            // given
            UUID postId = UUID.randomUUID();
            List<Image> images = List.of(
                    Image.builder().postId(postId).orderIndex(0).imageKey("key1").build(),
                    Image.builder().postId(postId).orderIndex(1).imageKey("key2").build()
            );

            when(imageRepository.findAllByPostIdOrderByOrderIndexAsc(postId)).thenReturn(images);

            // when
            List<String> result = imageService.findAllByPostId(postId);

            // then
            assertThat(result).containsExactly("key1", "key2");
        }

        @Test
        @DisplayName("이미지 없음 - 빈 목록 반환")
        void findAllByPostIdImagesNotInDB() {
            // given
            UUID postId = UUID.randomUUID();
            when(imageRepository.findAllByPostIdOrderByOrderIndexAsc(postId)).thenReturn(List.of());

            // when
            List<String> result = imageService.findAllByPostId(postId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("게시글 ID 목록으로 이미지 URL 목록 조회")
    class FindAllByPostIds {
        @Test
        @DisplayName("이미지 있음 - postId 기준 URL 목록 Map 반환")
        void findAllByPostIdsImagesInDB() {
            // given
            UUID postId1 = UUID.randomUUID();
            UUID postId2 = UUID.randomUUID();
            List<Image> images = List.of(
                    Image.builder().postId(postId1).orderIndex(0).imageKey("key1").build(),
                    Image.builder().postId(postId1).orderIndex(1).imageKey("key2").build(),
                    Image.builder().postId(postId2).orderIndex(0).imageKey("key3").build()
            );

            when(imageRepository.findAllByPostIdInOrderByOrderIndexAsc(List.of(postId1, postId2))).thenReturn(images);
            when(imageUrlConverter.toImageUrl("key1")).thenReturn("https://example.com/key1");
            when(imageUrlConverter.toImageUrl("key2")).thenReturn("https://example.com/key2");
            when(imageUrlConverter.toImageUrl("key3")).thenReturn("https://example.com/key3");

            // when
            Map<UUID, List<String>> result = imageService.findAllByPostIds(List.of(postId1, postId2));

            // then
            assertThat(result.get(postId1)).containsExactly("https://example.com/key1", "https://example.com/key2");
            assertThat(result.get(postId2)).containsExactly("https://example.com/key3");
        }

        @Test
        @DisplayName("이미지 없음 - 빈 Map 반환")
        void findAllByPostIdsImagesNotInDB() {
            // given
            when(imageRepository.findAllByPostIdInOrderByOrderIndexAsc(any())).thenReturn(List.of());

            // when
            Map<UUID, List<String>> result = imageService.findAllByPostIds(List.of(UUID.randomUUID()));

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("이미지 저장")
    class SaveImages {
        @Test
        @DisplayName("imageKeys 있음 - orderIndex 순서대로 저장")
        void saveImagesWithImageKeys() {
            // given
            UUID postId = UUID.randomUUID();
            List<String> imageKeys = List.of("key1", "key2", "key3");

            // when
            imageService.saveImages(postId, imageKeys);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
            verify(imageRepository).saveAll(captor.capture());

            List<Image> saved = captor.getValue();
            assertThat(saved).hasSize(3);
            assertThat(saved.get(0).getImageKey()).isEqualTo("key1");
            assertThat(saved.get(0).getOrderIndex()).isEqualTo(0);
            assertThat(saved.get(1).getImageKey()).isEqualTo("key2");
            assertThat(saved.get(1).getOrderIndex()).isEqualTo(1);
            assertThat(saved.get(2).getImageKey()).isEqualTo("key3");
            assertThat(saved.get(2).getOrderIndex()).isEqualTo(2);
        }

        @Test
        @DisplayName("imageKeys 없음 - 저장 작업 없음")
        void saveImagesWithoutImageKeys() {
            // given
            UUID postId = UUID.randomUUID();

            // when
            imageService.saveImages(postId, List.of());

            // then
            verifyNoInteractions(imageRepository);
        }
    }

    @Nested
    @DisplayName("이미지 동기화")
    class SyncImages {
        @Test
        @DisplayName("이미지 변경 없음 - 기존 imageKeys 반환")
        void syncImagesNoChange() {
            // given
            UUID postId = UUID.randomUUID();
            List<String> imageKeys = List.of("key1", "key2");
            List<Image> existingImages = List.of(
                    Image.builder().postId(postId).orderIndex(0).imageKey("key1").build(),
                    Image.builder().postId(postId).orderIndex(1).imageKey("key2").build()
            );

            when(imageRepository.findAllByPostIdOrderByOrderIndexAsc(postId)).thenReturn(existingImages);

            // when
            List<String> result = imageService.syncImages(postId, imageKeys);

            // then
            assertThat(result).containsExactly("key1", "key2");
            verify(imageRepository, never()).deleteAllByPostIdIn(any());
            verify(imageRepository, never()).saveAll(any());
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        @DisplayName("이미지 변경 있음 - 전체 교체 후 제외된 이미지 S3 삭제 이벤트 발행")
        void syncImagesWithChange() {
            // given
            UUID postId = UUID.randomUUID();
            List<String> newImageKeys = List.of("key2", "key3");
            List<Image> existingImages = List.of(
                    Image.builder().postId(postId).orderIndex(0).imageKey("key1").build(),
                    Image.builder().postId(postId).orderIndex(1).imageKey("key2").build()
            );

            when(imageRepository.findAllByPostIdOrderByOrderIndexAsc(postId)).thenReturn(existingImages);

            // when
            List<String> result = imageService.syncImages(postId, newImageKeys);

            // then
            assertThat(result).containsExactly("key2", "key3");
            verify(imageRepository).deleteAllByPostIdIn(List.of(postId));
            verify(imageRepository).saveAll(any());
            verify(applicationEventPublisher).publishEvent(new Event.S3ImageDelete(List.of("key1")));
        }

        @Test
        @DisplayName("모든 이미지 변경 - 전체 교체 후 전체 이미지 S3 삭제 이벤트 발행")
        void syncImagesRemoveAll() {
            // given
            UUID postId = UUID.randomUUID();
            List<Image> existingImages = List.of(
                    Image.builder().postId(postId).orderIndex(0).imageKey("key1").build()
            );

            when(imageRepository.findAllByPostIdOrderByOrderIndexAsc(postId)).thenReturn(existingImages);

            // when
            List<String> result = imageService.syncImages(postId, List.of());

            // then
            assertThat(result).isEmpty();
            verify(imageRepository).deleteAllByPostIdIn(List.of(postId));
            verify(imageRepository, never()).saveAll(any());
            verify(applicationEventPublisher).publishEvent(new Event.S3ImageDelete(List.of("key1")));
        }

        @Test
        @DisplayName("기존 이미지 없음 - 새 이미지만 저장")
        void syncImagesAddNew() {
            // given
            UUID postId = UUID.randomUUID();
            List<String> newImageKeys = List.of("key1", "key2");

            when(imageRepository.findAllByPostIdOrderByOrderIndexAsc(postId)).thenReturn(List.of());

            // when
            List<String> result = imageService.syncImages(postId, newImageKeys);

            // then
            assertThat(result).containsExactly("key1", "key2");
            verify(imageRepository).deleteAllByPostIdIn(List.of(postId));
            verify(imageRepository).saveAll(any());
            verifyNoInteractions(applicationEventPublisher);
        }
    }
}