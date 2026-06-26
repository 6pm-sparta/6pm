package com.fandom.chat_service.infra.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Component
public class IdCardHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object userId = attributes.get(IdCardHandshakeInterceptor.USER_ID_ATTR);
        return userId == null ? null : new StompPrincipal(userId.toString()); // principal userId
    }
}
