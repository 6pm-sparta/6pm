package com.fandom.user_service.follow.infrastructure.repository;

import com.fandom.user_service.follow.domain.entity.Follow;
import com.fandom.user_service.follow.domain.repository.projection.FollowCursorRow;
import com.fandom.user_service.follow.domain.repository.projection.FollowingCursorRow;
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

    /**
     * followerId의 팔로잉을 follow.id 커서 이후로 조회(follow.id ASC). cursor가 null이면 처음부터.
     * 팔로잉 대상(followee)의 Profile을 조인해 followerCount를 함께 가져온다(대형 여부 판단용).
     * Follow와 Profile은 직접 연관이 없어 user 기준 세타 조인으로 연결한다(p.user.id = f.followee.id).
     * limit은 Pageable로 전달받는다(호출부에서 PageRequest.of(0, limit)).
     */
    @Query("""
            select new com.fandom.user_service.follow.domain.repository.projection.FollowingCursorRow(f.id, f.followee.id, p.followerCount)
            from Follow f, Profile p
            where f.follower.id = :followerId
              and p.user.id = f.followee.id
              and (:cursor is null or f.id > :cursor)
            order by f.id asc
            """)
    List<FollowingCursorRow> findFollowingRowsByFollowerId(
            @Param("followerId") UUID followerId,
            @Param("cursor") UUID cursor,
            Pageable pageable
    );

    /**
     * findFollowingRowsByFollowerId 와 동일하되, 대형 크리에이터(followerCount > minFollowerCount)만 필터링한다.
     * 필터를 DB에서 적용하므로 커서 페이징이 정확하다(애플리케이션 필터 시 size가 어긋나는 문제 방지).
     */
    @Query("""
            select new com.fandom.user_service.follow.domain.repository.projection.FollowingCursorRow(f.id, f.followee.id, p.followerCount)
            from Follow f, Profile p
            where f.follower.id = :followerId
              and p.user.id = f.followee.id
              and p.followerCount > :minFollowerCount
              and (:cursor is null or f.id > :cursor)
            order by f.id asc
            """)
    List<FollowingCursorRow> findLargeFollowingRowsByFollowerId(
            @Param("followerId") UUID followerId,
            @Param("minFollowerCount") long minFollowerCount,
            @Param("cursor") UUID cursor,
            Pageable pageable
    );
}
