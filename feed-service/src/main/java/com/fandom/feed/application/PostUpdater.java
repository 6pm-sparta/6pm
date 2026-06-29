package com.fandom.feed.application;

import com.fandom.feed.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostUpdater {
    private final PostRepository postRepository;

    public void incrementCommentCount(UUID postId) {
        postRepository.incrementCommentCount(postId);
    }

    public void decrementCommentCount(UUID postId) {
        postRepository.decrementCommentCount(postId);
    }
}