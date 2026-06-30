package com.fandom.feed.infra.s3;

import com.fandom.feed.infra.s3.config.TestS3Config;
import com.fandom.feed.infra.s3.dto.PresignedUrlInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Import(TestS3Config.class)
class S3ServiceIntegrationTest {
    @Autowired
    private S3Service s3Service;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.0"))
            .withServices(LocalStackContainer.Service.S3);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("cloud.aws.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("cloud.aws.credentials.access-key", localStack::getAccessKey);
        registry.add("cloud.aws.credentials.secret-key", localStack::getSecretKey);
        registry.add("cloud.aws.region.static", localStack::getRegion);
    }

    @BeforeEach
    void setUp() {
        s3Client.createBucket(r -> r.bucket(bucket));
    }

    @Test
    @DisplayName("Presigned URL 발급 후 실제 업로드 성공")
    void generatePresignedUrlsAndUpload() throws Exception {
        // given
        List<PresignedUrlInfo> result = s3Service.generatePresignedUrls(List.of("image1.jpg"));
        String uploadUrl = result.getFirst().uploadUrl();

        // when
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofString("fake-image-content"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("이미지 실제 삭제")
    void deleteAll() {
        // given
        String key = "post/20240101/test.jpg";
        s3Client.putObject(r -> r.bucket(bucket).key(key), RequestBody.fromString("fake-image-content"));

        // when
        s3Service.deleteAll(List.of(key));

        // then
        assertThatThrownBy(() -> s3Client.headObject(r -> r.bucket(bucket).key(key))).isInstanceOf(NoSuchKeyException.class);
    }
}