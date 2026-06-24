package com.fandom.feed.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum LikeErrorCode implements ErrorCode {
    DUPLICATE_LIKE (HttpStatus.CONFLICT, "이미 좋아요한 게시글입니다.");

    private final HttpStatus status;
    private final String message;
}