package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.PriceHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/price-history")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class PriceHistoryController {

    @Autowired
    private PriceHistoryService priceHistoryService;

    @GetMapping("/{coinId}")
    public ResponseEntity<List<List<Number>>> getPriceHistory(@PathVariable String coinId) {
        try {
            List<List<Number>> priceHistory = priceHistoryService.getPriceHistory(coinId);
            return ResponseEntity.ok(priceHistory);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
} 