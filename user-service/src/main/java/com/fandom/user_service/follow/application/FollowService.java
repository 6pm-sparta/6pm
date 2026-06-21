package com.fandom.user_service.follow.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.follow.domain.entity.Follow;
import com.fandom.user_service.follow.domain.exception.FollowErrorCode;
import com.fandom.user_service.follow.domain.repository.FollowRepository;
import com.fandom.user_service.member.domain.entity.Role;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.member.domain.exception.MemberErrorCode;
import com.fandom.user_service.member.domain.repository.UserRepository;
import com.fandom.user_service.profile.domain.entity.Profile;
import com.fandom.user_service.profile.domain.exception.ProfileErrorCode;
import com.fandom.user_service.profile.domain.repository.ProfileRepository;
import com.fandom.user_service.follow.presentation.dto.response.FollowerResponse;
import com.fandom.user_service.follow.presentation.dto.response.FollowingResponse;
import com.fandom.user_service.follow.presentation.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public Follow follow(UUID followerId, UUID creatorId) {
        User follower = findUserById(followerId);
        User followee = findUserById(creatorId);

        validateFollowable(follower, followee);
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, creatorId)) {
            throw new CustomException(FollowErrorCode.DUPLICATE_FOLLOW);
        }

        Follow follow = saveFollow(follower, followee);

        increaseFollowingCount(followerId);
        increaseFollowerCount(creatorId);

        return follow;
    }

    @Transactional
    public void unfollow(UUID followerId, UUID creatorId) {
        User follower = findUserById(followerId);
        User followee = findUserById(creatorId);

        validateFollowable(follower, followee);
        Follow follow = followRepository.findByFollowerIdAndFolloweeId(followerId, creatorId)
                .orElseThrow(() -> new CustomException(FollowErrorCode.FOLLOW_NOT_FOUND));

        followRepository.delete(follow);

        decreaseFollowingCount(followerId);
        decreaseFollowerCount(creatorId);
    }

    public PageResponse<FollowerResponse> getFollowers(UUID creatorId, Pageable pageable) {
        User creator = findUserById(creatorId);
        validateRole(creator, Role.CREATOR, FollowErrorCode.FOLLOWEE_MUST_BE_CREATOR);

        Page<FollowerResponse> followers = followRepository.findByFolloweeId(creatorId, pageable)
                .map(follow -> {
                    User follower = follow.getFollower();
                    Profile profile = findProfileByUserId(follower.getId());
                    return FollowerResponse.from(follower, profile);
                });

        return PageResponse.from(followers);
    }

    public PageResponse<FollowingResponse> getFollowings(UUID memberId, Pageable pageable) {
        User member = findUserById(memberId);
        validateRole(member, Role.MEMBER, FollowErrorCode.FOLLOWER_MUST_BE_MEMBER);

        Page<FollowingResponse> followings = followRepository.findByFollowerId(memberId, pageable)
                .map(follow -> {
                    User followee = follow.getFollowee();
                    Profile profile = findProfileByUserId(followee.getId());
                    return FollowingResponse.from(followee, profile);
                });

        return PageResponse.from(followings);
    }

    private void validateFollowable(User follower, User followee) {
        if (follower.getId().equals(followee.getId())) {
            throw new CustomException(FollowErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }
        validateRole(follower, Role.MEMBER, FollowErrorCode.FOLLOWER_MUST_BE_MEMBER);
        validateRole(followee, Role.CREATOR, FollowErrorCode.FOLLOWEE_MUST_BE_CREATOR);
    }

    private void validateRole(User user, Role role, FollowErrorCode errorCode) {
        if (user.getRole() != role) {
            throw new CustomException(errorCode);
        }
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    private Profile findProfileByUserId(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND));
    }

    private Follow saveFollow(User follower, User followee) {
        try {
            return followRepository.saveAndFlush(
                    Follow.builder()
                            .follower(follower)
                            .followee(followee)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(FollowErrorCode.DUPLICATE_FOLLOW);
        }
    }

    private void increaseFollowerCount(UUID userId) {
        validateProfileUpdated(profileRepository.increaseFollowerCountByUserId(userId));
    }

    private void increaseFollowingCount(UUID userId) {
        validateProfileUpdated(profileRepository.increaseFollowingCountByUserId(userId));
    }

    private void decreaseFollowerCount(UUID userId) {
        profileRepository.decreaseFollowerCountByUserId(userId);
    }

    private void decreaseFollowingCount(UUID userId) {
        profileRepository.decreaseFollowingCountByUserId(userId);
    }

    private void validateProfileUpdated(int updatedCount) {
        if (updatedCount == 0) {
            throw new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND);
        }
    }
}
