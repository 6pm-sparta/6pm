package com.fandom.feed.infra.s3;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.infra.s3.dto.PresignedUrlInfo;
import com.fandom.feed.infra.s3.exception.S3ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.path.post}")
    private String postPath;

    @Value("${cloud.aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry;

    /**
     * S3 Presigned URL을 생성하는 메서드
     */
    @Retryable(
            retryFor = {SdkClientException.class, S3Exception.class},
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public List<PresignedUrlInfo> generatePresignedUrls(List<String> imageNames) {
        return imageNames.stream()
                .map(imageName -> {
                    String imageKey = generateImageKey(imageName);
                    String uploadUrl = generateUploadUrl(imageKey);
                    return PresignedUrlInfo.of(imageName, uploadUrl, imageKey);
                })
                .toList();
    }

    @Recover
    public List<PresignedUrlInfo> recoverGeneratePresignedUrls(Exception e, List<String> imageNames) {
        log.error("[S3] Presigned URL 발급 최종 실패 - imageNames: {}, error: {}", imageNames, e.getMessage(), e);
        throw new CustomException(S3ErrorCode.PRESIGNED_URL_GENERATION_FAILED);
    }

    /**
     * 이미지 키 목록으로 S3 이미지를 삭제하는 메서드
     */
    @Retryable(
            retryFor = {SdkClientException.class, S3Exception.class},
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void deleteAll(List<String> imageKeys) {
        if (imageKeys.isEmpty()) return;

        List<ObjectIdentifier> objects = imageKeys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();

        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objects).build())
                .build();

        s3Client.deleteObjects(request);
    }

    @Recover
    public void recoverDeleteAll(Exception e, List<String> imageKeys) {
        log.error("[S3] 이미지 삭제 최종 실패 - imageKeys: {}, error: {}", imageKeys, e.getMessage(), e);
    }

    /**
     * post/yyyyMMdd/uuid.ext 형태로 이미지 키를 생성하는 메서드
     */
    private String generateImageKey(String imageName) {
        String ext = imageName.substring(imageName.lastIndexOf("."));
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.join("/", postPath, date, UUID.randomUUID() + ext);
    }

    /**
     * S3 업로드용 Presigned URL을 생성하는 메서드
     */
    private String generateUploadUrl(String imageKey) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignedUrlExpiry))
                .putObjectRequest(r -> r.bucket(bucket).key(imageKey))
                .build();
        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }
}