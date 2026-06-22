package com.fandom.feed.application;

import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.event.Event;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.global.constant.ReactionSort;
import com.fandom.feed.domain.entity.Comment;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.CommentErrorCode;
import com.fandom.feed.domain.repository.CommentRepository;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.presentation.dto.response.CommentResponse;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {
    private final PostReader postReader;
    private final PostUpdater postUpdater;
    private final CommentRepository commentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UserClient userClient;

    @Transactional
    public CommentResponse.Create createComment(UUID postId, String content, UUID userId) {
        postReader.findById(postId);

        Comment comment = Comment.builder().postId(postId).authorId(userId).content(content).build();
        commentRepository.save(comment);

        postUpdater.incrementCommentCount(postId);

        applicationEventPublisher.publishEvent(new Event.CommentCreated(postId));

        return CommentResponse.Create.from(comment);
    }

    public CursorPageResponse<CommentResponse.Detail> getCommentsForPost(UUID postId, UUID cursor, ReactionSort sort) {
        postReader.findById(postId);
        List<Comment> comments = commentRepository.findByCursorAndPostId(cursor, sort, postId);
        return buildResponse(comments);
    }

    public CursorPageResponse<CommentResponse.Detail> getCommentsForUser(
            UUID cursor, ReactionSort sort, UUID userId, boolean isMine, boolean isMaster
    ) {
        // 권한 검증
        if (!isMine && !isMaster)
            throw new CustomException(CommonErrorCode.FORBIDDEN);

        List<Comment> comments = commentRepository.findByCursorAndAuthorId(cursor, sort, userId);
        return buildResponse(comments);
    }

    @Transactional
    public CommentResponse.Update updateComment(UUID commentId, String content, UUID userId) {
        Comment comment = findById(commentId);

        // 권한 검증
        if (!userId.equals(comment.getAuthorId()))
            throw new CustomException(CommentErrorCode.FORBIDDEN_COMMENT_UPDATE);

        comment.update(content);

        return CommentResponse.Update.from(comment);
    }

    @Transactional
    public CommentResponse.Delete deleteComment(UUID commentId, UUID userId, boolean isMaster) {
        Comment comment = findById(commentId);
        Post post = postReader.findById(comment.getPostId());

        // 권한 검증
        if (!userId.equals(comment.getAuthorId()) && !userId.equals(post.getAuthorId()) && !isMaster)
            throw new CustomException(CommentErrorCode.FORBIDDEN_COMMENT_DELETE);

        comment.softDelete(userId);

        postUpdater.decrementCommentCount(post.getId());

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

    /**
     * Comment 엔티티로 응답을 구성하는 메서드
     */
    private CursorPageResponse<CommentResponse.Detail> buildResponse(List<Comment> comments) {
        boolean hasMore = comments.size() > FeedPolicy.PAGE_SIZE;
        if (hasMore) comments = comments.subList(0, FeedPolicy.PAGE_SIZE);

        Set<UUID> authorIds = comments.stream().map(Comment::getAuthorId).filter(Objects::nonNull).collect(Collectors.toSet());

        // 모두 탈퇴 시 User 서비스 호출 생략
        Map<UUID, UserResponse> userMap = authorIds.isEmpty()
                ? Map.of() : userClient.getUsers(authorIds).getData()
                             .stream().collect(Collectors.toMap(UserResponse::userId, u -> u));

        List<CommentResponse.Detail> content = comments.stream()
                .map(c -> CommentResponse.Detail.of(c, userMap.get(c.getAuthorId()))).toList();

        UUID nextCursor = hasMore ? comments.getLast().getId() : null;
        return CursorPageResponse.of(content, nextCursor, hasMore);
    }
}