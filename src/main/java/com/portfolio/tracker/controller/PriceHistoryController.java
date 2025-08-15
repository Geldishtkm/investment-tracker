package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.PriceHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/price-history")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class PriceHistoryController {

    private final PriceHistoryService priceHistoryService;

    public PriceHistoryController(PriceHistoryService priceHistoryService) {
        this.priceHistoryService = priceHistoryService;
    }

    /**
     * Get historical price data for a cryptocurrency
     * @param coinId The cryptocurrency identifier
     * @param days Number of days of history to retrieve (default: 90)
     * @return List of price data points [timestamp, price]
     */
    @GetMapping("/{coinId}")
    public ResponseEntity<?> getPriceHistory(
            @PathVariable String coinId,
            @RequestParam(defaultValue = "90") int days
    ) {
        try {
            if (coinId == null || coinId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Coin ID is required");
            }
            if (days <= 0 || days > 365) {
                return ResponseEntity.badRequest().body("Days must be between 1 and 365");
            }
            
            List<double[]> priceHistory = priceHistoryService.getPriceHistory(coinId, days);
            return ResponseEntity.ok(Map.of(
                "coinId", coinId,
                "days", days,
                "dataPoints", priceHistory.size(),
                "priceHistory", priceHistory
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get price history for " + coinId + ": " + e.getMessage()));
        }
    }

    /**
     * Force refresh the price history cache for a specific coin
     * @param coinId The cryptocurrency identifier
     * @param days Number of days of history to refresh (default: 90)
     * @return Success message
     */
    @PostMapping("/refresh/{coinId}")
    public ResponseEntity<?> refreshCache(
            @PathVariable String coinId,
            @RequestParam(defaultValue = "90") int days
    ) {
        try {
            if (coinId == null || coinId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Coin ID is required");
            }
            if (days <= 0 || days > 365) {
                return ResponseEntity.badRequest().body("Days must be between 1 and 365");
            }
            
            priceHistoryService.refreshPriceHistory(coinId, days);
            return ResponseEntity.ok(Map.of("message", "Cache refreshed successfully for " + coinId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to refresh cache for " + coinId + ": " + e.getMessage()));
        }
    }

    /**
     * Get cache status for a specific cryptocurrency
     * @param coinId The cryptocurrency identifier
     * @return Map containing cache status information
     */
    @GetMapping("/status/{coinId}")
    public ResponseEntity<?> getCacheStatus(@PathVariable String coinId) {
        try {
            if (coinId == null || coinId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Coin ID is required");
            }
            
            Map<String, Object> status = priceHistoryService.getCacheStatus(coinId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get cache status for " + coinId + ": " + e.getMessage()));
        }
    }

    /**
     * Get full service cache summary and status
     * @return Map containing overall service status and cache information
     */
    @GetMapping("/status")
    public ResponseEntity<?> getServiceStatus() {
        try {
            Map<String, Object> status = priceHistoryService.getServiceStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get service status: " + e.getMessage()));
        }
    }
}
