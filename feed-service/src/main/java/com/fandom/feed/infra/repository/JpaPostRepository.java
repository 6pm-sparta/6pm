package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaPostRepository extends JpaRepository<Post, UUID> {
    @Query("""
        SELECT p FROM Post p
        WHERE (:cursor IS NULL OR p.id < :cursor)
          AND (:authorId IS NULL OR p.authorId = :authorId)
          AND (:keyword IS NULL OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY p.id DESC
    """)
    List<Post> findByCursor(@Param("cursor") UUID cursor,
                            @Param("authorId") UUID authorId,
                            @Param("keyword") String keyword,
                            Pageable pageable);

    @Query("""
        SELECT p FROM Post p
        WHERE (:authorId IS NULL OR p.authorId = :authorId)
        ORDER BY p.id DESC
    """)
    List<Post> findByCursorForWarm(@Param("authorId") UUID authorId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
    void incrementCommentCount(@Param("postId") UUID postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = GREATEST(p.commentCount - 1, 0) WHERE p.id = :postId")
    void decrementCommentCount(@Param("postId") UUID postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.deletedAt = CURRENT_TIMESTAMP, p.deletedBy = :authorId WHERE p.authorId = :authorId")
    void softDeleteAllByAuthorId(@Param("authorId") UUID authorId);

    @Query("SELECT p.id FROM Post p WHERE p.authorId = :authorId")
    List<UUID> findAllIdsByAuthorId(@Param("authorId") UUID authorId);
}