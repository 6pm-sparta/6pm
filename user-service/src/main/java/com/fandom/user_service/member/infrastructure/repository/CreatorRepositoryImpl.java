package com.fandom.user_service.member.infrastructure.repository;

import com.fandom.user_service.member.domain.entity.Creator;
import com.fandom.user_service.member.domain.repository.CreatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CreatorRepositoryImpl implements CreatorRepository {

    private final CreatorJpaRepository creatorJpaRepository;

    @Override
    public Creator save(Creator creator) {
        return creatorJpaRepository.save(creator);
    }

    @Override
    public Optional<Creator> findByUserId(UUID userId) {
        return creatorJpaRepository.findByUserId(userId);
    }
}
