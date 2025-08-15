package com.portfolio.tracker.config;

import com.portfolio.tracker.service.RealTimeMarketDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple WebSocket handler for real-time market data
 * Handles basic WebSocket connections and messages without STOMP
 */
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private RealTimeMarketDataService marketDataService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Track active WebSocket sessions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        
        System.out.println("WebSocket connection established: " + sessionId);
        
        // Set up the broadcast callback in the market data service
        marketDataService.setBroadcastCallback(this::broadcastAllMarketData);
        
        // Send welcome message
        Map<String, Object> welcome = Map.of(
            "type", "connection-status",
            "status", "connected",
            "message", "WebSocket connected successfully",
            "sessionId", sessionId,
            "timestamp", System.currentTimeMillis()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            
            String type = (String) data.get("type");
            String symbol = (String) data.get("symbol");
            
            switch (type) {
                case "subscribe":
                    handleSubscribe(session, symbol);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session, symbol);
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
            }
            
        } catch (Exception e) {
            System.err.println("Error handling WebSocket message: " + e.getMessage());
            
            // Send error response
            Map<String, Object> error = Map.of(
                "type", "error",
                "message", "Failed to process message: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        
        System.out.println("WebSocket connection closed: " + sessionId);
    }
    
    private void handleSubscribe(WebSocketSession session, String symbol) throws Exception {
        try {
            marketDataService.subscribeToMarketData(symbol);
            
            Map<String, Object> response = Map.of(
                "type", "subscription-status",
                "status", "success",
                "message", "Subscribed to " + symbol + " market data",
                "symbol", symbol,
                "timestamp", System.currentTimeMillis()
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            
        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                "type", "subscription-status",
                "status", "error",
                "message", "Failed to subscribe to " + symbol + ": " + e.getMessage(),
                "symbol", symbol,
                "timestamp", System.currentTimeMillis()
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }
    
    private void handleUnsubscribe(WebSocketSession session, String symbol) throws Exception {
        try {
            marketDataService.unsubscribeFromMarketData(symbol);
            
            Map<String, Object> response = Map.of(
                "type", "subscription-status",
                "status", "success",
                "message", "Unsubscribed from " + symbol + " market data",
                "symbol", symbol,
                "timestamp", System.currentTimeMillis()
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            
        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                "type", "subscription-status",
                "status", "error",
                "message", "Failed to unsubscribe from " + symbol + ": " + e.getMessage(),
                "symbol", symbol,
                "timestamp", System.currentTimeMillis()
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }
    
    /**
     * Broadcast market data to all connected clients
     */
    public void broadcastMarketData(Map<String, Object> marketData) {
        String message;
        try {
            message = objectMapper.writeValueAsString(marketData);
        } catch (Exception e) {
            System.err.println("Error serializing market data: " + e.getMessage());
            return;
        }
        
        TextMessage textMessage = new TextMessage(message);
        
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            } catch (Exception e) {
                System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * Broadcast all current market data to connected clients
     * This method is called by the market data service callback
     */
    public void broadcastAllMarketData() {
        // Get all subscribed symbols and broadcast their current data
        List<String> subscribedSymbols = marketDataService.getSubscribedSymbols();
        
        // For now, we'll just broadcast a simple message to indicate data is available
        // The actual market data will be fetched and broadcast by the scheduled task
        Map<String, Object> update = Map.of(
            "type", "market-data-update",
            "message", "Market data update triggered",
            "subscribedSymbols", subscribedSymbols,
            "timestamp", System.currentTimeMillis()
        );
        
        broadcastMarketData(update);
    }
}
