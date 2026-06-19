package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<Post> findLatest(@Param("cursor") UUID cursor,
                          @Param("authorId") UUID authorId,
                          @Param("keyword") String keyword,
                          Pageable pageable);

    @Query("""
        SELECT p FROM Post p
        WHERE (:cursor IS NULL OR p.id > :cursor)
          AND (:authorId IS NULL OR p.authorId = :authorId)
          AND (:keyword IS NULL OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY p.id ASC
    """)
    List<Post> findOldest(@Param("cursor") UUID cursor,
                          @Param("authorId") UUID authorId,
                          @Param("keyword") String keyword,
                          Pageable pageable);

    @Query("SELECT p FROM Post p ORDER BY p.id DESC")
    List<Post> findTopForWarm(Pageable pageable);

    @Query("SELECT p FROM Post p ORDER BY p.id ASC")
    List<Post> findBottomForWarm(Pageable pageable);
}