package com.fandom.feed.application;

import com.fandom.feed.domain.entity.Image;
import com.fandom.feed.domain.repository.ImageRepository;
import com.fandom.feed.infra.s3.event.S3ImageDeleteEvent;
import com.fandom.feed.infra.util.ImageUrlConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ImageService {
    private final ImageRepository imageRepository;
    private final ImageUrlConverter imageUrlConverter;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 게시글 ID로 이미지 목록을 조회하는 메서드
     */
    public List<String> findAllByPostId(UUID postId) {
        return imageRepository.findAllByPostIdOrderByOrderIndexAsc(postId).stream().map(Image::getImageKey).toList();
    }

    /**
     * 게시글 ID 목록으로 이미지 목록을 조회한 후, Map으로 반환하는 메서드
     */
    public Map<UUID, List<String>> findAllByPostIds(List<UUID> postIds) {
        return imageRepository.findAllByPostIdInOrderByOrderIndexAsc(postIds)
                .stream()
                .collect(Collectors.groupingBy(
                        Image::getPostId,
                        Collectors.mapping(
                                image -> imageUrlConverter.toImageUrl(image.getImageKey()),
                                Collectors.toList()
                        )
                ));
    }

    /**
     * Image 객체를 생성한 후, 저장하는 메서드
     */
    public void saveImages(UUID postId, List<String> imageKeys) {
        if (imageKeys.isEmpty()) return;

        List<Image> images = IntStream.range(0, imageKeys.size())
                .mapToObj(i -> Image.builder()
                        .postId(postId)
                        .orderIndex(i)
                        .imageKey(imageKeys.get(i))
                        .build())
                .toList();
        imageRepository.saveAll(images);
    }

    /**
     * 기존 이미지 키와 요청 이미지 키를 비교해 동기화하는 메서드<br>
     * - 순서와 값이 모두 일치하면 기존 이미지 키 반환<br>
     * - 하나라도 다르면 전체 교체 후 S3 삭제 이벤트 발행
     */
    public List<String> syncImages(UUID postId, List<String> newImageKeys) {
        List<String> existingImageKeys = findAllByPostId(postId);

        // 변경 여부 확인
        if (existingImageKeys.equals(newImageKeys)) return existingImageKeys;

        // 삭제 대상 구분
        List<String> ImageKeysToDelete = existingImageKeys.stream()
                .filter(key -> !newImageKeys.contains(key)).toList();

        deleteAllByPostId(postId);
        saveImages(postId, newImageKeys);
        publishS3DeleteEvent(ImageKeysToDelete);

        return newImageKeys;
    }

    /**
     * 게시글 ID로 이미지 목록을 삭제하는 메서드
     */
    public void deleteAllByPostId(UUID postId) {
        imageRepository.deleteAllByPostId(postId);
    }

    /**
     * S3 삭제 이벤트를 발행하는 메서드
     */
    public void publishS3DeleteEvent(List<String> ImageKeysToDelete) {
        if (!ImageKeysToDelete.isEmpty())
            applicationEventPublisher.publishEvent(new S3ImageDeleteEvent(ImageKeysToDelete));
    }
}