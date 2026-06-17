package com.fandom.user_service.member.domain.repository;

import com.fandom.user_service.member.domain.entity.Creator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CreatorRepository extends JpaRepository<Creator, UUID> {
}
