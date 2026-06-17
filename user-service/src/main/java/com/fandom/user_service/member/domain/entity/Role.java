package com.fandom.user_service.member.domain.entity;

/**
 * 사용자 권한 등급.
 * DB에는 문자열로 저장(@Enumerated(STRING))하고, 코드에서는 enum으로 사용한다.
 */
public enum Role {
    MEMBER,
    CREATOR,
    MASTER
}
