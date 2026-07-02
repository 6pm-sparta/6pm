package com.fandom.user_service.follow.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.follow.application.port.FollowEventPublisher;
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
import com.fandom.user_service.follow.presentation.dto.response.CursorPageResponse;
import com.fandom.user_service.follow.domain.repository.projection.FollowCursorRow;
import com.fandom.user_service.profile.domain.exception.ProfileErrorCode;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("팔로우 서비스 단위 테스트")
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private FollowEventPublisher followEventPublisher;

    @InjectMocks
    private FollowService followService;

    @Test
    @DisplayName("팔로우에 성공하면 관계를 저장하고 카운트를 증가시킨다")
    void follow_success() {
        UUID followerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        User follower = user(Role.MEMBER);
        User creator = user(Role.CREATOR);
        Profile followerProfile = profile(follower, "member");
        UUID followId = UUID.randomUUID();
        Follow savedFollow = Follow.builder()
                .follower(follower)
                .followee(creator)
                .build();
        ReflectionTestUtils.setField(savedFollow, "id", followId);

        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(creatorId)).willReturn(Optional.of(creator));
        given(followRepository.existsByFollowerIdAndFolloweeId(followerId, creatorId)).willReturn(false);
        given(followRepository.saveAndFlush(any(Follow.class))).willReturn(savedFollow);
        given(profileRepository.increaseFollowingCountByUserId(followerId)).willReturn(1);
        given(profileRepository.increaseFollowerCountByUserId(creatorId)).willReturn(1);
        given(profileRepository.findByUserId(followerId)).willReturn(Optional.of(followerProfile));

        Follow follow = followService.follow(followerId, creatorId);

        assertThat(follow).isEqualTo(savedFollow);
        verify(followRepository).saveAndFlush(any(Follow.class));
        verify(profileRepository).increaseFollowingCountByUserId(followerId);
        verify(profileRepository).increaseFollowerCountByUserId(creatorId);
        verify(followEventPublisher).publishFollowed(followId, followerId, creatorId, "member");
    }

    @Test
    @DisplayName("크리에이터는 다른 크리에이터를 팔로우할 수 있다")
    void follow_creatorToCreator_success() {
        UUID followerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        User follower = user(Role.CREATOR);
        User creator = user(Role.CREATOR);
        Profile followerProfile = profile(follower, "creator-follower");
        UUID followId = UUID.randomUUID();
        Follow savedFollow = Follow.builder()
                .follower(follower)
                .followee(creator)
                .build();
        ReflectionTestUtils.setField(savedFollow, "id", followId);

        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(creatorId)).willReturn(Optional.of(creator));
        given(followRepository.existsByFollowerIdAndFolloweeId(followerId, creatorId)).willReturn(false);
        given(followRepository.saveAndFlush(any(Follow.class))).willReturn(savedFollow);
        given(profileRepository.increaseFollowingCountByUserId(followerId)).willReturn(1);
        given(profileRepository.increaseFollowerCountByUserId(creatorId)).willReturn(1);
        given(profileRepository.findByUserId(followerId)).willReturn(Optional.of(followerProfile));

        Follow follow = followService.follow(followerId, creatorId);

        assertThat(follow).isEqualTo(savedFollow);
        verify(followRepository).saveAndFlush(any(Follow.class));
        verify(profileRepository).increaseFollowingCountByUserId(followerId);
        verify(profileRepository).increaseFollowerCountByUserId(creatorId);
        verify(followEventPublisher).publishFollowed(followId, followerId, creatorId, "creator-follower");
    }

    @Test
    @DisplayName("이미 팔로우한 관계면 예외가 발생한다")
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
    @DisplayName("유니크 제약 위반은 중복 팔로우 예외로 변환된다")
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
    @DisplayName("자기 자신 팔로우는 예외가 발생한다")
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
    @DisplayName("지원하지 않는 팔로워 역할이면 예외가 발생한다")
    void follow_unsupportedFollowerRole() {
        UUID followerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        given(userRepository.findById(followerId)).willReturn(Optional.of(user(Role.MASTER)));
        given(userRepository.findById(creatorId)).willReturn(Optional.of(user(Role.CREATOR)));

        assertThatThrownBy(() -> followService.follow(followerId, creatorId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(FollowErrorCode.FOLLOWER_MUST_BE_MEMBER_OR_CREATOR);
    }

    @Test
    @DisplayName("팔로우 대상이 크리에이터가 아니면 예외가 발생한다")
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
    @DisplayName("언팔로우에 성공하면 관계를 삭제하고 카운트를 감소시킨다")
    void unfollow_success() {
        UUID followerId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        User follower = user(Role.MEMBER);
        User creator = user(Role.CREATOR);
        UUID followId = UUID.randomUUID();
        Follow follow = Follow.builder()
                .follower(follower)
                .followee(creator)
                .build();
        ReflectionTestUtils.setField(follow, "id", followId);

        given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
        given(userRepository.findById(creatorId)).willReturn(Optional.of(creator));
        given(followRepository.findByFollowerIdAndFolloweeId(followerId, creatorId)).willReturn(Optional.of(follow));

        followService.unfollow(followerId, creatorId);

        verify(followRepository).delete(follow);
        verify(profileRepository).decreaseFollowingCountByUserId(followerId);
        verify(profileRepository).decreaseFollowerCountByUserId(creatorId);
        verify(followEventPublisher).publishUnfollowed(followId, followerId, creatorId);
    }

    @Test
    @DisplayName("팔로우 관계가 없으면 언팔로우 예외가 발생한다")
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
    @DisplayName("팔로워 목록은 페이지 응답으로 조회된다")
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
        given(profileRepository.findAllByUserIdIn(List.of(follower.getId()))).willReturn(List.of(followerProfile));

        PageResponse<FollowerResponse> response = followService.getFollowers(creatorId, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).nickname()).isEqualTo("member");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("팔로잉 목록은 크리에이터 프로필 페이지 응답으로 조회된다")
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
        given(profileRepository.findAllByUserIdIn(List.of(creator.getId()))).willReturn(List.of(creatorProfile));

        PageResponse<FollowingResponse> response = followService.getFollowings(memberId, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).nickname()).isEqualTo("creator");
        assertThat(response.content().get(0).followerCount()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("크리에이터의 팔로잉 목록도 조회할 수 있다")
    void getFollowings_creatorFollower_success() {
        UUID creatorFollowerId = UUID.randomUUID();
        User creatorFollower = user(Role.CREATOR);
        User creator = user(Role.CREATOR);
        Profile creatorProfile = profile(creator, "creator", 10, 0);
        Follow follow = Follow.builder()
                .follower(creatorFollower)
                .followee(creator)
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        given(userRepository.findById(creatorFollowerId)).willReturn(Optional.of(creatorFollower));
        given(followRepository.findByFollowerId(creatorFollowerId, pageable))
                .willReturn(new PageImpl<>(List.of(follow), pageable, 1));
        given(profileRepository.findAllByUserIdIn(List.of(creator.getId()))).willReturn(List.of(creatorProfile));

        PageResponse<FollowingResponse> response = followService.getFollowings(creatorFollowerId, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).nickname()).isEqualTo("creator");
        assertThat(response.content().get(0).followerCount()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("팔로워 수는 프로필 집계값으로 조회된다")
    void countFollowers_returnsProfileFollowerCount() {
        UUID authorId = UUID.randomUUID();
        User creator = user(Role.CREATOR);
        Profile creatorProfile = profile(creator, "creator", 42, 0);
        given(profileRepository.findByUserId(authorId)).willReturn(Optional.of(creatorProfile));

        long count = followService.countFollowers(authorId);

        assertThat(count).isEqualTo(42);
    }

    @Test
    @DisplayName("팔로워 수 조회 시 프로필이 없으면 예외가 발생한다")
    void countFollowers_profileNotFound() {
        UUID authorId = UUID.randomUUID();
        given(profileRepository.findByUserId(authorId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> followService.countFollowers(authorId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("팔로워 ID 목록: 결과가 size 이하면 hasNext=false, nextCursor=null")
    void getFollowerIds_lastPage() {
        UUID authorId = UUID.randomUUID();
        UUID follower1 = UUID.randomUUID();
        UUID follower2 = UUID.randomUUID();
        // size=3 요청 -> limit(size+1)=4로 조회, 결과 2개(size 이하) -> 마지막 페이지
        given(followRepository.findFollowerRowsByFolloweeId(authorId, null, 4))
                .willReturn(List.of(
                        new FollowCursorRow(UUID.randomUUID(), follower1),
                        new FollowCursorRow(UUID.randomUUID(), follower2)
                ));

        CursorPageResponse<UUID> response = followService.getFollowerIds(authorId, null, 3);

        assertThat(response.content()).containsExactly(follower1, follower2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("팔로워 ID 목록: 결과가 size 초과면 hasNext=true, 마지막 요소는 잘리고 nextCursor는 반환된 마지막의 followId")
    void getFollowerIds_hasNext() {
        UUID authorId = UUID.randomUUID();
        UUID follow1 = UUID.randomUUID();
        UUID follow2 = UUID.randomUUID();
        UUID follow3 = UUID.randomUUID();
        UUID follower1 = UUID.randomUUID();
        UUID follower2 = UUID.randomUUID();
        UUID follower3 = UUID.randomUUID();
        // size=2 요청 -> limit=3으로 조회, 결과 3개(size 초과) -> hasNext, 마지막 1개 잘라냄
        given(followRepository.findFollowerRowsByFolloweeId(authorId, null, 3))
                .willReturn(List.of(
                        new FollowCursorRow(follow1, follower1),
                        new FollowCursorRow(follow2, follower2),
                        new FollowCursorRow(follow3, follower3)
                ));

        CursorPageResponse<UUID> response = followService.getFollowerIds(authorId, null, 2);

        assertThat(response.content()).containsExactly(follower1, follower2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(follow2);
    }

    @Test
    @DisplayName("팔로워 ID 목록: 결과가 없으면 빈 목록, hasNext=false, nextCursor=null")
    void getFollowerIds_empty() {
        UUID authorId = UUID.randomUUID();
        given(followRepository.findFollowerRowsByFolloweeId(authorId, null, 4))
                .willReturn(List.of());

        CursorPageResponse<UUID> response = followService.getFollowerIds(authorId, null, 3);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("팔로워 ID 목록: size가 1 미만이면 예외가 발생한다")
    void getFollowerIds_invalidSize_tooSmall() {
        UUID authorId = UUID.randomUUID();

        assertThatThrownBy(() -> followService.getFollowerIds(authorId, null, 0))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(FollowErrorCode.INVALID_PAGE_SIZE);
    }

    @Test
    @DisplayName("팔로워 ID 목록: size가 1000 초과면 예외가 발생한다")
    void getFollowerIds_invalidSize_tooLarge() {
        UUID authorId = UUID.randomUUID();

        assertThatThrownBy(() -> followService.getFollowerIds(authorId, null, 1001))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(FollowErrorCode.INVALID_PAGE_SIZE);
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
