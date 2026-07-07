package com.fandom.user_service.member.application.port;

import java.util.UUID;

public interface CreatorCreatedEventPublisher {

    void publish(UUID userId, String nickname);
}
