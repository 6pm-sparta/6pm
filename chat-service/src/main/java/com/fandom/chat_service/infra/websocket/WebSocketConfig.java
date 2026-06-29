package com.fandom.chat_service.infra.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final IdCardHandshakeInterceptor handshakeInterceptor;
    private final IdCardHandshakeHandler handshakeHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/v1/chats/ws")
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic"); // 추후 교체 예정
        registry.setApplicationDestinationPrefixes("/app"); // 유저->서버
        registry.setUserDestinationPrefix("/user"); // 유저 개인 큐
    }
}
