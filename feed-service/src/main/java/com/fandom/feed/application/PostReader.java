package com.fandom.feed.application;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.PostErrorCode;
import com.fandom.feed.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostReader {
    private final PostRepository postRepository;

    public Post findById(UUID id) {
        return postRepository.findById(id).orElseThrow(() -> new CustomException(PostErrorCode.POST_NOT_FOUND));
    }
}