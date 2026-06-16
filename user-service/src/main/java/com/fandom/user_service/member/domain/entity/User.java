package com.fandom.user_service.member.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자(users) 엔티티.
 * 일반회원/크리에이터/마스터가 role로 구분되며, 공통 계정 정보를 담는다.
 * 크리에이터 부가정보는 Creator 엔티티가 1:1로 참조한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "address1")
    private String address1;

    @Column(name = "address2")
    private String address2;

    @Builder
    private User(String email, String password, Role role, Status status,
                 String zipCode, String address1, String address2) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.status = status;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
    }

    /**
     * 일반회원 가입용 팩토리. role=USER, status=ACTIVE 기본 부여.
     * password는 호출 측에서 BCrypt 해싱된 값을 넘긴다.
     */
    public static User createUser(String email, String encodedPassword) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .role(Role.USER)
                .status(Status.ACTIVE)
                .build();
    }

    /**
     * 크리에이터 가입용 팩토리. role=CREATOR, status=ACTIVE 기본 부여.
     */
    public static User createCreator(String email, String encodedPassword) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .role(Role.CREATOR)
                .status(Status.ACTIVE)
                .build();
    }
}
