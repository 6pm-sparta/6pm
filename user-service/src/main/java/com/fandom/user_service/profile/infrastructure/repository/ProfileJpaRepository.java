package com.fandom.user_service.profile.infrastructure.repository;

import com.fandom.user_service.profile.domain.entity.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileJpaRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<Profile> findAllByUserIdIn(Collection<UUID> userIds);

    boolean existsByNickname(String nickname);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Profile p set p.followerCount = p.followerCount + 1 where p.user.id = :userId")
    int increaseFollowerCountByUserId(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Profile p set p.followingCount = p.followingCount + 1 where p.user.id = :userId")
    int increaseFollowingCountByUserId(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Profile p set p.followerCount = p.followerCount - 1 where p.user.id = :userId and p.followerCount > 0")
    int decreaseFollowerCountByUserId(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Profile p set p.followingCount = p.followingCount - 1 where p.user.id = :userId and p.followingCount > 0")
    int decreaseFollowingCountByUserId(UUID userId);

    boolean existsByNicknameAndIdNot(String nickname, UUID id);
}
