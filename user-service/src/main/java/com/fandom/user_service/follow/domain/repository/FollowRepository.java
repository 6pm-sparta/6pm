package com.fandom.user_service.follow.domain.repository;

import com.fandom.user_service.follow.domain.entity.Follow;
import com.fandom.user_service.follow.domain.repository.projection.FollowCursorRow;
import com.fandom.user_service.follow.domain.repository.projection.FollowingCursorRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository {

    Follow saveAndFlush(Follow follow);

    void delete(Follow follow);

    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    Optional<Follow> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    Page<Follow> findByFolloweeId(UUID followeeId, Pageable pageable);

    Page<Follow> findByFollowerId(UUID followerId, Pageable pageable);

    /**
     * 특정 크리에이터(followeeId)의 팔로워를 커서(follow.id) 이후로 limit개 조회한다.
     * 결과는 follow.id 오름차순(=팔로우한 순서, UUIDv7 시간순). cursor가 null이면 처음부터 조회한다.
     * 팬아웃 대상 조회용이므로 follow.id(커서)와 follower.id(대상 userId)만 담은 projection을 반환한다.
     */
    List<FollowCursorRow> findFollowerRowsByFolloweeId(UUID followeeId, UUID cursor, int limit);

    /**
     * 특정 사용자(followerId)가 팔로잉하는 대상을 커서(follow.id) 이후로 limit개 조회한다.
     * 팔로잉 대상(followee)의 Profile.followerCount를 함께 담은 projection을 반환한다(대형 여부 판단용).
     * 결과는 follow.id 오름차순. cursor가 null이면 처음부터 조회한다.
     */
    List<FollowingCursorRow> findFollowingRowsByFollowerId(UUID followerId, UUID cursor, int limit);

    /**
     * findFollowingRowsByFollowerId 와 동일하되, 대형 크리에이터(followerCount > minFollowerCount)만 조회한다.
     * 필터링을 DB에서 수행하므로 커서 페이징 size가 정확하게 유지된다.
     */
    List<FollowingCursorRow> findLargeFollowingRowsByFollowerId(UUID followerId, long minFollowerCount, UUID cursor, int limit);
}
