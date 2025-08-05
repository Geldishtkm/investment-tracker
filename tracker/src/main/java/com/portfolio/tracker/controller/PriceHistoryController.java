package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.PriceHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/price-history")
public class PriceHistoryController {

    private final PriceHistoryService priceHistoryService;

    public PriceHistoryController(PriceHistoryService priceHistoryService) {
        this.priceHistoryService = priceHistoryService;
    }

    // 1. Get price history for a coin and number of days
    @GetMapping("/{coinId}")
    public List<double[]> getPriceHistory(
            @PathVariable String coinId,
            @RequestParam(defaultValue = "90") int days
    ) {
        return priceHistoryService.getPriceHistory(coinId, days);
    }

    // 2. Force refresh cache manually
    @PostMapping("/refresh/{coinId}")
    public void refreshCache(
            @PathVariable String coinId,
            @RequestParam(defaultValue = "90") int days
    ) {
        priceHistoryService.refreshPriceHistory(coinId, days);
    }

    // 3. Get cache status for a specific coin
    @GetMapping("/status/{coinId}")
    public Map<String, Object> getCacheStatus(@PathVariable String coinId) {
        return priceHistoryService.getCacheStatus(coinId);
    }

    // 4. Get full service cache summary
    @GetMapping("/status")
    public Map<String, Object> getServiceStatus() {
        return priceHistoryService.getServiceStatus();
    }
}
