package com.fandom.feed.application;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.event.Event;
import com.fandom.feed.domain.entity.Comment;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.CommentErrorCode;
import com.fandom.feed.domain.repository.CommentRepository;
import com.fandom.feed.presentation.dto.response.CommentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {
    private final PostReader postReader;
    private final CommentRepository commentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public CommentResponse.Create createComment(UUID postId, String content, UUID userId) {
        postReader.findById(postId);

        Comment comment = Comment.builder().postId(postId).authorId(userId).content(content).build();
        commentRepository.save(comment);

        applicationEventPublisher.publishEvent(new Event.CommentCreated(postId));

        return CommentResponse.Create.from(comment);
    }

    @Transactional
    public CommentResponse.Delete deleteComment(UUID commentId, UUID userId, boolean isMaster) {
        Comment comment = findById(commentId);
        Post post = postReader.findById(comment.getPostId());

        // 권한 검증
        if (!userId.equals(comment.getAuthorId()) && !userId.equals(post.getAuthorId()) && !isMaster)
            throw new CustomException(CommentErrorCode.FORBIDDEN_COMMENT_DELETE);

        comment.softDelete(userId);

        applicationEventPublisher.publishEvent(new Event.CommentDeleted(post.getId()));

        return CommentResponse.Delete.from(comment);
    }

    /**
     * 댓글 ID로 댓글을 조회하는 메서드
     */
    public Comment findById(UUID commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(CommentErrorCode.COMMENT_NOT_FOUND));
    }
}