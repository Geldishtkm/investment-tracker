package com.portfolio.tracker.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for streaming real-time market data via WebSockets
 * Integrates with existing crypto price services to provide live updates
 */
@Service
public class RealTimeMarketDataService {

    @Autowired
    private CryptoPriceService cryptoPriceService;
    
    // Track active subscriptions for different market data streams
    private final Map<String, Boolean> activeSubscriptions = new ConcurrentHashMap<>();
    
    // Counter for generating unique message IDs
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    
    // Callback for broadcasting market data (will be set by WebSocketHandler)
    private Runnable broadcastCallback;
    
    /**
     * Subscribe to real-time market data for specific symbols
     */
    public void subscribeToMarketData(String symbol) {
        activeSubscriptions.put(symbol, true);
    }
    
    /**
     * Unsubscribe from market data for specific symbols
     */
    public void unsubscribeFromMarketData(String symbol) {
        activeSubscriptions.remove(symbol);
    }
    
    /**
     * Get list of currently subscribed symbols
     */
    public List<String> getSubscribedSymbols() {
        return activeSubscriptions.keySet().stream().toList();
    }
    
    /**
     * Set the broadcast callback (called by WebSocketHandler)
     */
    public void setBroadcastCallback(Runnable callback) {
        this.broadcastCallback = callback;
    }
    
    /**
     * Broadcast market data update to all subscribed clients
     * This method is called periodically to simulate real-time updates
     */
    @Scheduled(fixedRate = 5000) // Update every 5 seconds
    public void broadcastMarketData() {
        if (activeSubscriptions.isEmpty()) {
            return; // No active subscriptions
        }
        
        try {
            // Get current market data from existing crypto service
            List<Map<String, Object>> topCoins = cryptoPriceService.getTopCoins();
            
            // Filter only subscribed symbols and broadcast updates
            topCoins.stream()
                .filter(coin -> {
                    String symbol = (String) coin.get("symbol");
                    return symbol != null && activeSubscriptions.containsKey(symbol.toUpperCase());
                })
                .forEach(coin -> {
                    String symbol = ((String) coin.get("symbol")).toUpperCase();
                    double currentPrice = getDoubleValue(coin, "current_price");
                    double priceChange = getDoubleValue(coin, "price_change_24h");
                    double priceChangePercent = getDoubleValue(coin, "price_change_percentage_24h");
                    
                    // Create market data update message
                    Map<String, Object> update = Map.of(
                        "type", "market-data",
                        "symbol", symbol,
                        "price", currentPrice,
                        "priceChange", priceChange,
                        "priceChangePercent", priceChangePercent,
                        "timestamp", System.currentTimeMillis(),
                        "messageId", messageCounter.incrementAndGet()
                    );
                    
                    // Trigger broadcast via callback if set
                    if (broadcastCallback != null) {
                        broadcastCallback.run();
                    }
                });
                
        } catch (Exception e) {
            // Log error but don't crash the service
            System.err.println("Error broadcasting market data: " + e.getMessage());
        }
    }
    
    /**
     * Send immediate market data update for specific symbol
     * Useful for manual price refresh requests
     */
    public void sendImmediateUpdate(String symbol) {
        try {
            // Get price for specific symbol
            double price = cryptoPriceService.getCryptoPriceInUSD(symbol.toLowerCase());
            
            // Create a simple update with current price (no change data for immediate updates)
            Map<String, Object> update = Map.of(
                "type", "market-data",
                "symbol", symbol.toUpperCase(),
                "price", price,
                "priceChange", 0.0, // No change data available for immediate updates
                "priceChangePercent", 0.0, // No change percentage available for immediate updates
                "timestamp", System.currentTimeMillis(),
                "messageId", messageCounter.incrementAndGet()
            );
            
            // Trigger broadcast via callback if set
            if (broadcastCallback != null) {
                broadcastCallback.run();
            }
            
        } catch (Exception e) {
            System.err.println("Error sending immediate update for " + symbol + ": " + e.getMessage());
        }
    }
    
    /**
     * Helper method to safely extract double values from coin data
     */
    private double getDoubleValue(Map<String, Object> coin, String key) {
        Object value = coin.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}
