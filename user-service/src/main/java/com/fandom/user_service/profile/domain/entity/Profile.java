package com.fandom.user_service.profile.domain.entity;

import com.fandom.common.entity.BaseEntity;
import com.fandom.user_service.member.domain.entity.User;
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

import java.time.LocalDate;

/**
 * 프로필(profiles) 엔티티.
 * User와 1:1로 연결되며, 닉네임/소개/이미지/팔로우 카운트 등 공개 프로필 정보를 담는다.
 * User -> Profile 단방향 (Profile만 User를 참조).
 *
 * 객체 생성은 빌더로만 가능하다(생성자 private). new 직접 생성은 차단된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "profiles")
public class Profile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "profile_message", columnDefinition = "TEXT")
    private String profileMessage;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Column(name = "banner_image", length = 255)
    private String bannerImage;

    @Column(name = "follower_count", nullable = false)
    private int followerCount;

    @Column(name = "following_count", nullable = false)
    private int followingCount;

    @Builder
    private Profile(User user, String nickname, LocalDate birthday, String profileMessage,
                    String profileImage, String bannerImage,
                    Integer followerCount, Integer followingCount) {
        this.user = user;
        this.nickname = nickname;
        this.birthday = birthday;
        this.profileMessage = profileMessage;
        this.profileImage = profileImage;
        this.bannerImage = bannerImage;
        this.followerCount = (followerCount != null) ? followerCount : 0;
        this.followingCount = (followingCount != null) ? followingCount : 0;
    }

    public void update(String nickname, LocalDate birthday, String profileMessage,
                       String profileImage, String bannerImage) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (birthday != null) {
            this.birthday = birthday;
        }
        if (profileMessage != null) {
            this.profileMessage = profileMessage;
        }
        if (profileImage != null) {
            this.profileImage = profileImage;
        }
        if (bannerImage != null) {
            this.bannerImage = bannerImage;
        }
    }

    public void updateWithoutBirthday(String nickname, String profileMessage,
                                      String profileImage, String bannerImage) {
        update(nickname, null, profileMessage, profileImage, bannerImage);
    }
}
