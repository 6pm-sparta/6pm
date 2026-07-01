package com.fandom.feed.infra.s3;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.infra.s3.exception.S3ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@EnableRetry
@ExtendWith(SpringExtension.class)
@Import(S3Service.class)
@TestPropertySource(properties = {
        "cloud.aws.s3.bucket=test-bucket",
        "cloud.aws.s3.path.post=posts",
        "cloud.aws.s3.presigned-url-expiry=300"
})
class S3ServiceTest {
    @Autowired
    private S3Service s3Service;

    @MockitoBean
    private S3Presigner s3Presigner;

    @MockitoBean
    private S3Client s3Client;

    @Test
    @DisplayName("Presigned URL 발급 최종 실패 시 예외 발생")
    void generatePresignedUrlsFail() {
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willThrow(SdkClientException.create("connection error"));

        assertThatThrownBy(() -> s3Service.generatePresignedUrls(List.of("image1.jpg")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(S3ErrorCode.PRESIGNED_URL_GENERATION_FAILED));
    }
}