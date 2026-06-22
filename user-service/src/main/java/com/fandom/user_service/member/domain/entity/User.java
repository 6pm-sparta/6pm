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
 *
 * 객체 생성은 빌더로만 가능하다(생성자 private). new 직접 생성은 차단된다.
 * role은 가입 종류에 따라 호출 측에서 지정하고, status는 기본 ACTIVE이다.
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
        this.status = (status != null) ? status : Status.ACTIVE;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
    }

    public void updateAccount(String email, String password, String zipCode, String address1, String address2) {
        if (email != null) {
            this.email = email;
        }
        if (password != null) {
            this.password = password;
        }
        if (zipCode != null) {
            this.zipCode = zipCode;
        }
        if (address1 != null) {
            this.address1 = address1;
        }
        if (address2 != null) {
            this.address2 = address2;
        }
    }
}
