package com.fandom.user_service.profile.domain.repository;

import com.fandom.user_service.profile.domain.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    boolean existsByNickname(String nickname);
}
