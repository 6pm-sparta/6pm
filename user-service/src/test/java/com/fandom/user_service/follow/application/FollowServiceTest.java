package com.fandom.user_service.follow.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.follow.domain.entity.Follow;
import com.fandom.user_service.follow.domain.exception.FollowErrorCode;
import com.fandom.user_service.follow.domain.repository.FollowRepository;
import com.fandom.user_service.member.domain.entity.Role;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.member.domain.repository.UserRepository;
import com.fandom.user_service.profile.domain.entity.Profile;
import com.fandom.user_service.profile.domain.repository.ProfileRepository;
import com.fandom.user_service.follow.presentation.dto.response.FollowerResponse;
import com.fandom.user_service.follow.presentation.dto.response.FollowingResponse;
import com.fandom.user_service.follow.presentation.dto.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FollowService unit tests")
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private FollowService followService;

    @Test
    @DisplayName("follow saves relation and increases counts")
    void follow_success() {
        UUID followerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        User follower = user(Role.MEMBER);
        User creator = user(Role.CREATOR);
        Follow savedFollow = Follow.builder()
                .follower(follower)
                .followee(creator)
                .build();

        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(creatorId)).willReturn(Optional.of(creator));
        given(followRepository.existsByFollowerIdAndFolloweeId(followerId, creatorId)).willReturn(false);
        given(followRepository.saveAndFlush(any(Follow.class))).willReturn(savedFollow);
        given(profileRepository.increaseFollowingCountByUserId(followerId)).willReturn(1);
        given(profileRepository.increaseFollowerCountByUserId(creatorId)).willReturn(1);

        Follow follow = followService.follow(followerId, creatorId);

        assertThat(follow).isEqualTo(savedFollow);
        verify(followRepository).saveAndFlush(any(Follow.class));
        verify(profileRepository).increaseFollowingCountByUserId(followerId);
        verify(profileRepository).increaseFollowerCountByUserId(creatorId);
    }

    @Test
    @DisplayName("follow rejects duplicate relation")
    void follow_duplicate() {
        UUID followerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        given(userRepository.findById(followerId)).willReturn(Optional.of(user(Role.MEMBER)));
        given(userRepository.findById(creatorId)).willReturn(Optional.of(user(Role.CREATOR)));
        given(followRepository.existsByFollowerIdAndFolloweeId(followerId, creatorId)).willReturn(true);

        assertThatThrownBy(() -> followService.follow(followerId, creatorId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(FollowErrorCode.DUPLICATE_FOLLOW);
    }

    @Test
    @DisplayName("follow maps unique constraint violation to duplicate follow")
    void follow_duplicateByUniqueConstraint() {
        UUID followerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        given(userRepository.findById(followerId)).willReturn(Optional.of(user(Role.MEMBER)));
        given(userRepository.findById(creatorId)).willReturn(Optional.of(user(Role.CREATOR)));
        given(followRepository.existsByFollowerIdAndFolloweeId(followerId, creatorId)).willReturn(false);
        given(followRepository.saveAndFlush(any(Follow.class)))
                .willThrow(new DataIntegrityViolationException("duplicate follow"));

        assertThatThrownBy(() -> followService.follow(followerId, creatorId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(FollowErrorCode.DUPLICATE_FOLLOW);
    }

    @Test
    @DisplayName("follow rejects self follow")
    void follow_self() {
        UUID userId = UUID.randomUUID();
        User user = user(Role.MEMBER);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> followService.follow(userId, userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(FollowErrorCode.SELF_FOLLOW_NOT_ALLOWED);
    }

    @Test
    @DisplayName("follow rejects non-member follower")
    void follow_nonMemberFollower() {
        UUID followerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        given(userRepository.findById(followerId)).willReturn(Optional.of(user(Role.CREATOR)));
        given(userRepository.findById(creatorId)).willReturn(Optional.of(user(Role.CREATOR)));

        assertThatThrownBy(() -> followService.follow(followerId, creatorId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(FollowErrorCode.FOLLOWER_MUST_BE_MEMBER);
    }

    @Test
    @DisplayName("follow rejects non-creator followee")
    void follow_nonCreatorFollowee() {
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        given(userRepository.findById(followerId)).willReturn(Optional.of(user(Role.MEMBER)));
        given(userRepository.findById(followeeId)).willReturn(Optional.of(user(Role.MEMBER)));

        assertThatThrownBy(() -> followService.follow(followerId, followeeId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(FollowErrorCode.FOLLOWEE_MUST_BE_CREATOR);
    }

    @Test
    @DisplayName("unfollow deletes relation and decreases counts")
    void unfollow_success() {
        UUID followerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        User follower = user(Role.MEMBER);
        User creator = user(Role.CREATOR);
        Follow follow = Follow.builder()
                .follower(follower)
                .followee(creator)
                .build();

        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(creatorId)).willReturn(Optional.of(creator));
        given(followRepository.findByFollowerIdAndFolloweeId(followerId, creatorId)).willReturn(Optional.of(follow));

        followService.unfollow(followerId, creatorId);

        verify(followRepository).delete(follow);
        verify(profileRepository).decreaseFollowingCountByUserId(followerId);
        verify(profileRepository).decreaseFollowerCountByUserId(creatorId);
    }

    @Test
    @DisplayName("unfollow rejects missing relation")
    void unfollow_notFound() {
        UUID followerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        given(userRepository.findById(followerId)).willReturn(Optional.of(user(Role.MEMBER)));
        given(userRepository.findById(creatorId)).willReturn(Optional.of(user(Role.CREATOR)));
        given(followRepository.findByFollowerIdAndFolloweeId(followerId, creatorId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> followService.unfollow(followerId, creatorId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(FollowErrorCode.FOLLOW_NOT_FOUND);
    }

    @Test
    @DisplayName("getFollowers returns paged follower profiles")
    void getFollowers_success() {
        UUID creatorId = UUID.randomUUID();
        User creator = user(Role.CREATOR);
        User follower = user(Role.MEMBER);
        Profile followerProfile = profile(follower, "member");
        Follow follow = Follow.builder()
                .follower(follower)
                .followee(creator)
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        given(userRepository.findById(creatorId)).willReturn(Optional.of(creator));
        given(followRepository.findByFolloweeId(creatorId, pageable))
                .willReturn(new PageImpl<>(List.of(follow), pageable, 1));
        given(profileRepository.findByUserId(follower.getId())).willReturn(Optional.of(followerProfile));

        PageResponse<FollowerResponse> response = followService.getFollowers(creatorId, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).nickname()).isEqualTo("member");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getFollowings returns paged creator profiles")
    void getFollowings_success() {
        UUID memberId = UUID.randomUUID();
        User member = user(Role.MEMBER);
        User creator = user(Role.CREATOR);
        Profile creatorProfile = profile(creator, "creator", 10, 0);
        Follow follow = Follow.builder()
                .follower(member)
                .followee(creator)
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        given(userRepository.findById(memberId)).willReturn(Optional.of(member));
        given(followRepository.findByFollowerId(memberId, pageable))
                .willReturn(new PageImpl<>(List.of(follow), pageable, 1));
        given(profileRepository.findByUserId(creator.getId())).willReturn(Optional.of(creatorProfile));

        PageResponse<FollowingResponse> response = followService.getFollowings(memberId, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).nickname()).isEqualTo("creator");
        assertThat(response.content().get(0).followerCount()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    private User user(Role role) {
        User user = User.builder()
                .email(UUID.randomUUID() + "@example.com")
                .password("encoded-password")
                .role(role)
                .build();
        user.assignId();
        return user;
    }

    private Profile profile(User user, String nickname) {
        return profile(user, nickname, 0, 0);
    }

    private Profile profile(User user, String nickname, int followerCount, int followingCount) {
        return Profile.builder()
                .user(user)
                .nickname(nickname)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .build();
    }
}
