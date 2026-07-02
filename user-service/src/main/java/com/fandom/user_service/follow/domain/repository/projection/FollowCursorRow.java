package com.fandom.user_service.follow.domain.repository.projection;

import java.util.UUID;

/**
 * 커서 페이징 조회 결과의 한 행.
 *
 * @param followId     follow 레코드의 id. 다음 페이지 커서로 사용한다(follow.id 기준 정렬).
 * @param targetUserId 조회 대상 userId. 팔로워 목록이면 follower.id, 팔로잉 목록이면 followee.id.
 *                     응답 content에 담긴다.
 */
public record FollowCursorRow(
        UUID followId,
        UUID targetUserId
) {
}
