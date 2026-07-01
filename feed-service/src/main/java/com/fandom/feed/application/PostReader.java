package com.fandom.feed.application;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.PostErrorCode;
import com.fandom.feed.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostReader {
    private final PostRepository postRepository;

    /**
     * 게시글 ID로 게시글을 조회하는 메서드
     */
    public Post findById(UUID postId) {
        return postRepository.findById(postId).orElseThrow(() -> new CustomException(PostErrorCode.POST_NOT_FOUND));
    }

    /**
     * 게시글 ID 목록으로 게시글을 조회하는 메서드
     */
    public List<Post> findAllByIds(List<UUID> postIds) {
        return postRepository.findAllById(postIds);
    }
}