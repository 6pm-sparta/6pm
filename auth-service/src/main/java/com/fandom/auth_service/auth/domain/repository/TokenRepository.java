package com.fandom.auth_service.auth.domain.repository;

import java.time.Duration;
import java.util.UUID;

public interface TokenRepository {

    void saveRefreshToken(UUID userId, String tokenId, Duration ttl);

    boolean existsRefreshToken(UUID userId, String tokenId);

    void deleteRefreshToken(UUID userId, String tokenId);

    void blacklistAccessToken(String tokenId, Duration ttl);
}
