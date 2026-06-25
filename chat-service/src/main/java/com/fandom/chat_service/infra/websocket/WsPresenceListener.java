package com.fandom.chat_service.infra.websocket;

import com.fandom.chat_service.application.port.OnlineStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsPresenceListener {

    private final OnlineStatePort onlineState;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        UUID userId = extractUserId(event.getUser());
        if (userId != null) {
            onlineState.markOnline(userId);
            log.debug("WS online user_id={}", userId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        UUID userId = extractUserId(event.getUser());
        if (userId != null) {
            onlineState.markOffline(userId);
            log.debug("WS offline user_id={}", userId);
        }
    }

    private UUID extractUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            log.warn("WS Principal name이 UUID가 아님: {}", principal.getName());
            return null;
        }
    }
}
