package com.fandom.feed.domain.repository;

import com.fandom.feed.domain.entity.Image;
import com.fandom.feed.infra.repository.ImageRepositoryImpl;
import com.fandom.feed.infra.repository.JpaImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ImageRepositoryImpl.class)
class ImageRepositoryTest {
    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private JpaImageRepository jpaImageRepository;

    @Test
    @DisplayName("postId로 이미지 조회 - orderIndex 오름차순")
    void findAllByPostIdOrderIndexByOrderAsc() {
        // Given
        UUID postId = UUID.randomUUID();
        Image img2 = Image.builder().postId(postId).imageKey("key2").orderIndex(2).build();
        Image img1 = Image.builder().postId(postId).imageKey("key1").orderIndex(1).build();

        jpaImageRepository.saveAll(List.of(img2, img1));

        // When
        List<Image> results = imageRepository.findAllByPostIdOrderByOrderIndexAsc(postId);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getImageKey()).isEqualTo("key1");
        assertThat(results.get(1).getImageKey()).isEqualTo("key2");
    }

    @Nested
    @DisplayName("여러 postId로 이미지 조회")
    class FindAllByPostIdInOrderByOrderIndexAsc {
        @Test
        @DisplayName("orderIndex 오름차순")
        void findAllByPostIdInOrderByOrderIndexAsc() {
            // Given
            UUID postId1 = UUID.randomUUID();
            UUID postId2 = UUID.randomUUID();
            UUID otherPostId = UUID.randomUUID();

            Image img1_2 = Image.builder().postId(postId1).imageKey("key1_2").orderIndex(2).build();
            Image img1_1 = Image.builder().postId(postId1).imageKey("key1_1").orderIndex(1).build();
            Image img2_1 = Image.builder().postId(postId2).imageKey("key2_1").orderIndex(1).build();
            Image other = Image.builder().postId(otherPostId).imageKey("other").orderIndex(1).build();

            jpaImageRepository.saveAll(List.of(img1_2, img1_1, img2_1, other));

            // When
            List<Image> results = imageRepository.findAllByPostIdInOrderByOrderIndexAsc(List.of(postId1, postId2));

            // Then
            assertThat(results).hasSize(3);
            assertThat(results).extracting(Image::getPostId)
                    .containsOnly(postId1, postId2)
                    .doesNotContain(otherPostId);

            List<Image> postId1Images = results.stream().filter(img -> img.getPostId().equals(postId1)).toList();
            assertThat(postId1Images.get(0).getImageKey()).isEqualTo("key1_1");
            assertThat(postId1Images.get(1).getImageKey()).isEqualTo("key1_2");
        }

        @Test
        @DisplayName("빈 목록 반환")
        void findAllByPostIdInOrderByOrderIndexAscReturnEmpty() {
            // Given
            UUID postId = UUID.randomUUID();

            // When
            List<Image> results = imageRepository.findAllByPostIdInOrderByOrderIndexAsc(List.of(postId));

            // Then
            assertThat(results).isEmpty();
        }
    }
}