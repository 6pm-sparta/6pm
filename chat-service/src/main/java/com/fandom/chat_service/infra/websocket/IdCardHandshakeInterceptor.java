package com.fandom.chat_service.infra.websocket;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.filter.IdCardVerificationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class IdCardHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTR = "userId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Object attr = servletRequest.getServletRequest()
                    .getAttribute(IdCardVerificationFilter.ID_CARD_ATTRIBUTE);
            if (attr instanceof UserIdCard idCard && idCard.getUserId() != null) {
                attributes.put(USER_ID_ATTR, idCard.getUserId());
                return true;
            }
        }
        log.warn("WS 핸드셰이크 거부 - idCard 없음");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {

    }
}
