package com.fandom.user_service.follow.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.follow.application.port.FollowEventPublisher;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final FollowEventPublisher followEventPublisher;

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
        Profile followerProfile = findProfileByUserId(followerId);
        publishFollowedEventAfterCommit(follow.getId(), followerId, creatorId, followerProfile.getNickname());

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
        publishUnfollowedEventAfterCommit(follow.getId(), followerId, creatorId);
    }

    public PageResponse<FollowerResponse> getFollowers(UUID creatorId, Pageable pageable) {
        User creator = findUserById(creatorId);
        validateRole(creator, Role.CREATOR, FollowErrorCode.FOLLOWEE_MUST_BE_CREATOR);

        Page<Follow> followerPage = followRepository.findByFolloweeId(creatorId, pageable);
        Map<UUID, Profile> profilesByUserId = findProfilesByUserIds(
                followerPage.getContent().stream()
                        .map(follow -> follow.getFollower().getId())
                        .toList()
        );
        Page<FollowerResponse> followers = followerPage.map(follow -> {
            User follower = follow.getFollower();
            Profile profile = getProfile(profilesByUserId, follower.getId());
            return FollowerResponse.from(follower, profile);
        });

        return PageResponse.from(followers);
    }

    public PageResponse<FollowingResponse> getFollowings(UUID memberId, Pageable pageable) {
        User member = findUserById(memberId);
        validateFollowerRole(member);

        Page<Follow> followingPage = followRepository.findByFollowerId(memberId, pageable);
        Map<UUID, Profile> profilesByUserId = findProfilesByUserIds(
                followingPage.getContent().stream()
                        .map(follow -> follow.getFollowee().getId())
                        .toList()
        );
        Page<FollowingResponse> followings = followingPage.map(follow -> {
            User followee = follow.getFollowee();
            Profile profile = getProfile(profilesByUserId, followee.getId());
            return FollowingResponse.from(followee, profile);
        });

        return PageResponse.from(followings);
    }

    private void validateFollowable(User follower, User followee) {
        if (follower.getId().equals(followee.getId())) {
            throw new CustomException(FollowErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }
        validateFollowerRole(follower);
        validateRole(followee, Role.CREATOR, FollowErrorCode.FOLLOWEE_MUST_BE_CREATOR);
    }

    private void validateFollowerRole(User user) {
        if (user.getRole() != Role.MEMBER && user.getRole() != Role.CREATOR) {
            throw new CustomException(FollowErrorCode.FOLLOWER_MUST_BE_MEMBER_OR_CREATOR);
        }
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

    private Map<UUID, Profile> findProfilesByUserIds(List<UUID> userIds) {
        return profileRepository.findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity()));
    }

    private Profile getProfile(Map<UUID, Profile> profilesByUserId, UUID userId) {
        Profile profile = profilesByUserId.get(userId);
        if (profile == null) {
            throw new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND);
        }
        return profile;
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

    private void publishFollowedEventAfterCommit(UUID followId, UUID followerId, UUID followeeId, String nickname) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            followEventPublisher.publishFollowed(followId, followerId, followeeId, nickname);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                followEventPublisher.publishFollowed(followId, followerId, followeeId, nickname);
            }
        });
    }

    private void publishUnfollowedEventAfterCommit(UUID followId, UUID followerId, UUID followeeId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            followEventPublisher.publishUnfollowed(followId, followerId, followeeId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                followEventPublisher.publishUnfollowed(followId, followerId, followeeId);
            }
        });
    }
}
