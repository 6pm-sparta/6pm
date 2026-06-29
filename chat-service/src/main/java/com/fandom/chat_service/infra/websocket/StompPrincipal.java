package com.fandom.chat_service.infra.websocket;

import java.security.Principal;

public record StompPrincipal(String userId) implements Principal {

    @Override
    public String getName() {
        return userId;
    }
}
