package com.vanguard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket / STOMP configuration.
 *
 * Topic structure:
 *   /topic/jobs              → broadcast new PENDING jobs to all online drivers
 *   /topic/job/{id}          → per-job status updates (driver + customer subscribe)
 *   /topic/driver/{id}/location → driver GPS broadcast to customer tracking view
 *   /user/queue/notifications → per-user notification queue (requires auth principal)
 *
 * Clients connect via:
 *   SockJS at ws://localhost:8080/api/ws
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker handles /topic (broadcast) and /user (point-to-point)
        registry.enableSimpleBroker("/topic", "/user");
        // Prefix for messages coming FROM clients → @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
        // Enables /user/{userId}/queue/... targeting
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // Allow frontend dev servers
                .setAllowedOriginPatterns("http://localhost:3000", "http://localhost:3001", "https://*.vanguardenergy.co.za")
                .withSockJS();
    }
}
