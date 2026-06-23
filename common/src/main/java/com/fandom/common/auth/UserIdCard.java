package com.fandom.common.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Gateway가 Access Token 검증 후 생성하여 HMAC 서명과 함께 downstream으로 전달하는 사용자 신원 객체.
 * 각 도메인 서비스는 @CurrentIdCard 어노테이션으로 주입받아 사용한다.
 * DB 조회 없이 요청 컨텍스트 내에서 사용자 식별 및 권한 체크에 사용된다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserIdCard {

    private UUID userId;
    private String role; // MEMBER, CREATOR, MASTER

    public static UserIdCard of(UUID userId, String role) {
        return new UserIdCard(userId, role);
    }

    @JsonIgnore
    public boolean isMe(UUID userId) {
        return userId != null && userId.equals(this.userId);
    }

    @JsonIgnore
    public boolean isMember() {
        return "MEMBER".equals(role);
    }

    @JsonIgnore
    public boolean isCreator() {
        return "CREATOR".equals(role);
    }

    @JsonIgnore
    public boolean isMaster() {
        return "MASTER".equals(role);
    }
}
