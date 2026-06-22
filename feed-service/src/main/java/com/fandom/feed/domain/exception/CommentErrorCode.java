package com.fandom.feed.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CommentErrorCode implements ErrorCode {
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."),
    FORBIDDEN_COMMENT_UPDATE(HttpStatus.FORBIDDEN, "해당 댓글의 수정 권한이 없습니다."),
    FORBIDDEN_COMMENT_DELETE(HttpStatus.FORBIDDEN, "해당 댓글의 삭제 권한이 없습니다.");

    private final HttpStatus status;
    private final String message;
}