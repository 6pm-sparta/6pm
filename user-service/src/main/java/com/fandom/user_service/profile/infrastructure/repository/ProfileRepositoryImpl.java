package com.fandom.user_service.profile.infrastructure.repository;

import com.fandom.user_service.profile.domain.entity.Profile;
import com.fandom.user_service.profile.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProfileRepositoryImpl implements ProfileRepository {

    private final ProfileJpaRepository profileJpaRepository;

    @Override
    public Profile save(Profile profile) {
        return profileJpaRepository.save(profile);
    }

    @Override
    public Optional<Profile> findById(UUID id) {
        return profileJpaRepository.findById(id);
    }

    @Override
    public Optional<Profile> findByUserId(UUID userId) {
        return profileJpaRepository.findByUserId(userId);
    }

    @Override
    public List<Profile> findAllByUserIdIn(Collection<UUID> userIds) {
        return profileJpaRepository.findAllByUserIdIn(userIds);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return profileJpaRepository.existsByNickname(nickname);
    }

    @Override
    public int increaseFollowerCountByUserId(UUID userId) {
        return profileJpaRepository.increaseFollowerCountByUserId(userId);
    }

    @Override
    public int increaseFollowingCountByUserId(UUID userId) {
        return profileJpaRepository.increaseFollowingCountByUserId(userId);
    }

    @Override
    public int decreaseFollowerCountByUserId(UUID userId) {
        return profileJpaRepository.decreaseFollowerCountByUserId(userId);
    }

    @Override
    public int decreaseFollowingCountByUserId(UUID userId) {
        return profileJpaRepository.decreaseFollowingCountByUserId(userId);
    }

    @Override
    public boolean existsByNicknameAndIdNot(String nickname, UUID id) {
        return profileJpaRepository.existsByNicknameAndIdNot(nickname, id);
    }
}
