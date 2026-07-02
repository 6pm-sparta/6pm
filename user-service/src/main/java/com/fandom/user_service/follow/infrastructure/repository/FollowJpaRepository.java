package com.fandom.user_service.follow.infrastructure.repository;

import com.fandom.user_service.follow.domain.entity.Follow;
import com.fandom.user_service.follow.domain.repository.projection.FollowCursorRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowJpaRepository extends JpaRepository<Follow, UUID> {

    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    Optional<Follow> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    @EntityGraph(attributePaths = "follower")
    Page<Follow> findByFolloweeId(UUID followeeId, Pageable pageable);

    @EntityGraph(attributePaths = "followee")
    Page<Follow> findByFollowerId(UUID followerId, Pageable pageable);

    /**
     * followeeId의 팔로워를 follow.id 커서 이후로 조회(follow.id ASC). cursor가 null이면 처음부터.
     * follow.id와 follower.id만 projection으로 가져온다(연관 엔티티 로딩 없음).
     * limit은 Pageable로 전달받는다(호출부에서 PageRequest.of(0, limit)).
     */
    @Query("""
            select new com.fandom.user_service.follow.domain.repository.projection.FollowCursorRow(f.id, f.follower.id)
            from Follow f
            where f.followee.id = :followeeId
              and (:cursor is null or f.id > :cursor)
            order by f.id asc
            """)
    List<FollowCursorRow> findFollowerRowsByFolloweeId(
            @Param("followeeId") UUID followeeId,
            @Param("cursor") UUID cursor,
            Pageable pageable
    );
}
