package com.fandom.feed.domain.repository;

import com.fandom.feed.domain.entity.Image;
import com.fandom.feed.infra.repository.ImageRepositoryImpl;
import com.fandom.feed.infra.repository.JpaImageRepository;
import org.junit.jupiter.api.DisplayName;
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
}