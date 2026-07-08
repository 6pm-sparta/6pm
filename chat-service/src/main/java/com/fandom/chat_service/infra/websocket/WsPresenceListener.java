package com.fandom.chat_service.infra.websocket;

import com.fandom.chat_service.application.port.OnlineStatePort;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsPresenceListener {

    private final OnlineStatePort onlineState;
    private final MeterRegistry meterRegistry;
    // 현재 WebSocket 세션 수 (Gauge). 동시연결 급감 감지용
    private final AtomicInteger activeSessions = new AtomicInteger(0);

    @PostConstruct
    void initMetrics() {
        meterRegistry.gauge("chat.ws.active_sessions", activeSessions);
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        activeSessions.incrementAndGet();
        UUID userId = extractUserId(event.getUser());
        if (userId != null) {
            onlineState.markOnline(userId);
            log.debug("WS online user_id={}", userId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        activeSessions.updateAndGet(v -> v > 0 ? v - 1 : 0);
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
