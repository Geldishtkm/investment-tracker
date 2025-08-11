package com.portfolio.tracker.config;

import com.portfolio.tracker.config.WebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for real-time market data streaming
 * Uses simple WebSocket instead of STOMP for better compatibility
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private WebSocketHandler webSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register WebSocket endpoint with the custom handler
        registry.addHandler(webSocketHandler, "/ws")
                .setAllowedOriginPatterns("*"); // Allow CORS for development
    }
}
