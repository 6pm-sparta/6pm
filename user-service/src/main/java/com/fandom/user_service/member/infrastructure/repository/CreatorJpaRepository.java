package com.fandom.user_service.member.infrastructure.repository;

import com.fandom.user_service.member.domain.entity.Creator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreatorJpaRepository extends JpaRepository<Creator, UUID> {

    Optional<Creator> findByUserId(UUID userId);
}
