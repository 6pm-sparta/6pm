package com.fandom.user_service.profile.domain.repository;

import com.fandom.user_service.profile.domain.entity.Profile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {

    Profile save(Profile profile);

    Optional<Profile> findById(UUID id);

    Optional<Profile> findByUserId(UUID userId);

    List<Profile> findAllByUserIdIn(Collection<UUID> userIds);

    boolean existsByNickname(String nickname);

    int increaseFollowerCountByUserId(UUID userId);

    int increaseFollowingCountByUserId(UUID userId);

    int decreaseFollowerCountByUserId(UUID userId);

    int decreaseFollowingCountByUserId(UUID userId);

    boolean existsByNicknameAndIdNot(String nickname, UUID id);
}
