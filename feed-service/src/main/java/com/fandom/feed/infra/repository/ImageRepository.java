package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, UUID> {
}