package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.RealTimeMarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * WebSocket controller for managing real-time market data subscriptions
 * Handles simple WebSocket messages for subscription management
 */
@Controller
@CrossOrigin(origins = "*")
public class WebSocketController {

    @Autowired
    private RealTimeMarketDataService marketDataService;
    
    /**
     * Get list of currently subscribed symbols
     * REST endpoint for checking subscription status
     */
    @GetMapping("/api/websocket/subscriptions")
    public Map<String, Object> getSubscriptions() {
        List<String> symbols = marketDataService.getSubscribedSymbols();
        
        return Map.of(
            "subscribedSymbols", symbols,
            "count", symbols.size(),
            "timestamp", System.currentTimeMillis()
        );
    }
    
    /**
     * Manually trigger market data update for specific symbol
     * Useful for testing and immediate updates
     */
    @PostMapping("/api/websocket/update/{symbol}")
    public Map<String, Object> triggerUpdate(@PathVariable String symbol) {
        try {
            marketDataService.sendImmediateUpdate(symbol);
            
            return Map.of(
                "status", "success",
                "message", "Triggered update for " + symbol,
                "symbol", symbol,
                "timestamp", System.currentTimeMillis()
            );
        } catch (Exception e) {
            return Map.of(
                "status", "error",
                "message", "Failed to trigger update for " + symbol + ": " + e.getMessage(),
                "symbol", symbol,
                "timestamp", System.currentTimeMillis()
            );
        }
    }
    
    /**
     * Health check endpoint for WebSocket service
     */
    @GetMapping("/api/websocket/health")
    public Map<String, Object> healthCheck() {
        List<String> symbols = marketDataService.getSubscribedSymbols();
        
        return Map.of(
            "status", "healthy",
            "activeSubscriptions", symbols.size(),
            "subscribedSymbols", symbols,
            "timestamp", System.currentTimeMillis(),
            "service", "WebSocket Market Data Service"
        );
    }
}
