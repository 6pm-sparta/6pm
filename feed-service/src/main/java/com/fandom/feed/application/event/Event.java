package com.fandom.feed.application.event;

import java.util.List;
import java.util.UUID;

public class Event {
    public record S3ImageDelete(List<String> imageKeys) {}
    public record CommentCreated(UUID postId) {}
    public record CommentDeleted(UUID postId) {}
    public record CommentAllDeleted(List<UUID> postIds) {}
    public record PostCreated(UUID postId, UUID authorId) {}
}