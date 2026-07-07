package com.fandom.user_service.member.domain.repository;

import com.fandom.user_service.member.domain.entity.Creator;

import java.util.Optional;
import java.util.UUID;

public interface CreatorRepository {

    Creator save(Creator creator);

    Optional<Creator> findByUserId(UUID userId);
}
