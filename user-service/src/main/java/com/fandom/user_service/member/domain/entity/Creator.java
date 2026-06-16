package com.fandom.user_service.member.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크리에이터(creators) 엔티티.
 * User와 1:1로 연결되며, 크리에이터 부가정보(소속사명 등)를 담는다.
 * User -> Creator 단방향 (Creator만 User를 참조).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "creators")
public class Creator extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "agency_name", length = 50)
    private String agencyName;

    @Builder
    private Creator(User user, String agencyName) {
        this.user = user;
        this.agencyName = agencyName;
    }

    public static Creator create(User user, String agencyName) {
        return Creator.builder()
                .user(user)
                .agencyName(agencyName)
                .build();
    }
}
