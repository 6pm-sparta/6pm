package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    List<Comment> findLatestByPostId(@Param("cursor") UUID cursor,
                                     @Param("postId") UUID postId,
                                     Pageable pageable);

    @Query("""
        SELECT c FROM Comment c
        WHERE c.postId = :postId
          AND (:cursor IS NULL OR c.id > :cursor)
        ORDER BY c.id ASC
    """)
    List<Comment> findOldestByPostId(@Param("cursor") UUID cursor,
                                     @Param("postId") UUID postId,
                                     Pageable pageable);

    @Query("""
        SELECT c FROM Comment c
        WHERE c.authorId = :authorId
          AND (:cursor IS NULL OR c.id < :cursor)
        ORDER BY c.id DESC
    """)
    List<Comment> findLatestByAuthorId(@Param("cursor") UUID cursor,
                                       @Param("authorId") UUID authorId,
                                       Pageable pageable);

    @Query("""
        SELECT c FROM Comment c
        WHERE c.authorId = :authorId
          AND (:cursor IS NULL OR c.id > :cursor)
        ORDER BY c.id ASC
    """)
    List<Comment> findOldestByAuthorId(@Param("cursor") UUID cursor,
                                       @Param("authorId") UUID authorId,
                                       Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.authorId = null WHERE c.authorId = :authorId")
    void anonymizeByAuthorId(@Param("authorId") UUID authorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.deletedAt = CURRENT_TIMESTAMP, c.deletedBy = :userId WHERE c.postId IN :postIds")
    void softDeleteAllByPostIds(@Param("postIds") List<UUID> postIds, @Param("userId") UUID userId);
}