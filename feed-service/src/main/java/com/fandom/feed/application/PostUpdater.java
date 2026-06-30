package com.fandom.feed.application;

import com.fandom.feed.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostUpdater {
    private final PostRepository postRepository;

    /**
     * 게시글의 댓글 수를 증가하는 메서드
     */
    public void incrementCommentCount(UUID postId) {
        postRepository.incrementCommentCount(postId);
    }

    /**
     * 게시글의 댓글 수를 감소하는 메서드
     */
    public void decrementCommentCount(UUID postId) {
        postRepository.decrementCommentCount(postId);
    }
}