package com.fandom.user_service.member.application.port;

import com.fandom.user_service.member.domain.entity.Role;

import java.util.UUID;

public interface MemberWithdrawalEventPublisher {

    void publish(UUID userId, Role role);
}
