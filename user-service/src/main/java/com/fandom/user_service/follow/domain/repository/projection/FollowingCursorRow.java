package com.fandom.user_service.follow.domain.repository.projection;

import java.util.UUID;

/**
 * 팔로잉 커서 페이징 조회 결과의 한 행. (Feed 타임라인 조회용)
 *
 * 팔로잉 대상(followee)의 Profile.followerCount를 함께 조회해, 대형 크리에이터 여부 판단에 사용한다.
 * (isLarge = followerCount > minFollowerCount)
 *
 * @param followId      follow 레코드의 id. 다음 페이지 커서로 사용한다(follow.id 기준 정렬).
 * @param followeeId    팔로잉 대상 userId. 응답 content(authorId)에 담긴다.
 * @param followerCount 팔로잉 대상의 팔로워 수(Profile 집계값). 대형 여부 판단에 사용한다.
 */
public record FollowingCursorRow(
        UUID followId,
        UUID followeeId,
        long followerCount
) {
}
