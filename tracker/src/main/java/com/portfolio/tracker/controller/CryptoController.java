package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.CryptoPriceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crypto")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true") // adjust for production
public class CryptoController {

    private final CryptoPriceService cryptoPriceService;

    public CryptoController(CryptoPriceService cryptoPriceService) {
        this.cryptoPriceService = cryptoPriceService;
    }

    /**
     * Get current USD price for a cryptocurrency
     * @param coinId The cryptocurrency identifier (e.g., bitcoin, ethereum)
     * @return Current price in USD
     */
    @GetMapping("/price/{coinId}")
    public ResponseEntity<?> getCryptoPrice(@PathVariable String coinId) {
        try {
            if (coinId == null || coinId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Coin ID is required");
            }
            
            double price = cryptoPriceService.getCryptoPriceInUSD(coinId.toLowerCase());
            return ResponseEntity.ok(Map.of("coinId", coinId, "price", price, "currency", "USD"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get price for " + coinId + ": " + e.getMessage()));
        }
    }

    /**
     * Get detailed information about a cryptocurrency
     * @param coinId The cryptocurrency identifier
     * @return Map containing coin details (id, symbol, name, image)
     */
    @GetMapping("/details/{coinId}")
    public ResponseEntity<?> getCoinDetails(@PathVariable String coinId) {
        try {
            if (coinId == null || coinId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Coin ID is required");
            }
            
            Map<String, Object> details = cryptoPriceService.getCoinDetails(coinId.toLowerCase());
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get details for " + coinId + ": " + e.getMessage()));
        }
    }

    /**
     * Get top cryptocurrencies from cache (auto-refreshes every 10 minutes)
     * @return List of top coins with their details
     */
    @GetMapping("/top")
    public ResponseEntity<?> getTopCoins() {
        try {
            List<Map<String, Object>> topCoins = cryptoPriceService.getTopCoins();
            return ResponseEntity.ok(Map.of("coins", topCoins, "count", topCoins.size()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get top coins: " + e.getMessage()));
        }
    }
}
