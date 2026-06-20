package com.fandom.feed.infra.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.util.List;

@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

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
}