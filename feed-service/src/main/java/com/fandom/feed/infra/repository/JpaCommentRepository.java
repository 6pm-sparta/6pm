package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaCommentRepository extends JpaRepository<Comment, UUID> {
    @Query("""
        SELECT c FROM Comment c
        WHERE c.postId = :postId
          AND (:cursor IS NULL OR c.id < :cursor)
        ORDER BY c.id DESC
    """)
    List<Comment> findLatestByPostId(@Param("postId") UUID postId,
                                     @Param("cursor") UUID cursor,
                                     Pageable pageable);

    @Query("""
        SELECT c FROM Comment c
        WHERE c.postId = :postId
          AND (:cursor IS NULL OR c.id > :cursor)
        ORDER BY c.id ASC
    """)
    List<Comment> findOldestByPostId(@Param("postId") UUID postId,
                                     @Param("cursor") UUID cursor,
                                     Pageable pageable);

    @Query("""
        SELECT c FROM Comment c
        WHERE c.authorId = :authorId
          AND (:cursor IS NULL OR c.id < :cursor)
        ORDER BY c.id DESC
    """)
    List<Comment> findLatestByAuthorId(@Param("authorId") UUID authorId,
                                       @Param("cursor") UUID cursor,
                                       Pageable pageable);

    @Query("""
        SELECT c FROM Comment c
        WHERE c.authorId = :authorId
          AND (:cursor IS NULL OR c.id > :cursor)
        ORDER BY c.id ASC
    """)
    List<Comment> findOldestByAuthorId(@Param("authorId") UUID authorId,
                                       @Param("cursor") UUID cursor,
                                       Pageable pageable);
}