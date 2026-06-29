package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Like;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaLikeRepository extends JpaRepository<Like, UUID> {
    List<Like> findAllByPostId(UUID postId);
    void deleteByPostIdAndUserId(UUID postId, UUID userId);

    @Query("""
        SELECT l FROM Like l
        WHERE l.userId = :userId
          AND (:cursor IS NULL OR l.id < :cursor)
        ORDER BY l.id DESC
    """)
    List<Like> findLatestByUserId(@Param("cursor") UUID cursor,
                                  @Param("userId") UUID userId,
                                  Pageable pageable);

    @Query("""
        SELECT l FROM Like l
        WHERE l.userId = :userId
          AND (:cursor IS NULL OR l.id > :cursor)
        ORDER BY l.id ASC
    """)
    List<Like> findOldestByUserId(@Param("cursor") UUID cursor,
                                  @Param("userId") UUID userId,
                                  Pageable pageable);

    void deleteAllByPostId(UUID postId);

    @Query("SELECT l.postId, l.userId FROM Like l WHERE l.postId IN :postIds")
    List<Object[]> findLikeUsersByPostIds(@Param("postIds") List<UUID> postIds);
}