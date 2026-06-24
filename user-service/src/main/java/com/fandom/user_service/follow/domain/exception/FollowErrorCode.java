package com.fandom.user_service.follow.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FollowErrorCode implements ErrorCode {

    FOLLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "팔로우 관계를 찾을 수 없습니다."),
    DUPLICATE_FOLLOW(HttpStatus.CONFLICT, "이미 팔로우한 크리에이터입니다."),
    SELF_FOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신은 팔로우할 수 없습니다."),
    FOLLOWER_MUST_BE_MEMBER(HttpStatus.FORBIDDEN, "일반 회원만 팔로우할 수 있습니다."),
    FOLLOWER_MUST_BE_MEMBER_OR_CREATOR(HttpStatus.FORBIDDEN, "일반 회원 또는 크리에이터만 팔로우할 수 있습니다."),
    FOLLOWEE_MUST_BE_CREATOR(HttpStatus.BAD_REQUEST, "크리에이터만 팔로우할 수 있습니다.");

    private final HttpStatus status;
    private final String message;
}
