package com.fandom.user_service.follow.presentation.dto.response;

import com.fandom.user_service.follow.domain.repository.projection.FollowingCursorRow;

import java.util.UUID;

/**
 * 내부 팔로잉 조회 응답. (Feed 타임라인 조회 전용)
 *
 * Feed가 사용자의 타임라인을 구성할 때, 팔로잉 대상별로 대형 크리에이터 여부(isLarge)를 함께 내려준다.
 * 대형 여부에 따라 Feed는 팬아웃 전략(push/pull)을 달리 적용한다.
 *
 * 프로필 정보를 담는 일반 조회용 {@code FollowingResponse}와는 목적/구조가 다른 내부 전용 DTO다.
 * (Feign은 필드명 기준으로 역직렬화하므로 authorId/isLarge 필드명이 소비 측 계약과 일치하면 된다.)
 *
 * @param authorId 팔로잉 대상 userId
 * @param isLarge  대형 크리에이터 여부 (followerCount > minFollowerCount)
 */
public record InternalFollowingResponse(
        UUID authorId,
        boolean isLarge
) {

    public static InternalFollowingResponse of(FollowingCursorRow row, long minFollowerCount) {
        return new InternalFollowingResponse(
                row.followeeId(),
                row.followerCount() > minFollowerCount
        );
    }
}
