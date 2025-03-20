package com.example.fingerprint_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Con SockJS
        registry.addEndpoint("/ws-fingerprint")
                // Opción 1: Permitir sólo tu Front local
                .setAllowedOrigins("http://localhost:3000")
                .withSockJS();

        // Opción 2 (si tuvieras más orígenes):
        // registry.addEndpoint("/ws-fingerprint")
        //         .setAllowedOriginPatterns("http://*.midominio.com", "http://localhost:[*]", ...)
        //         .withSockJS();
    }
}

