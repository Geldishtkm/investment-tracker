package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.CryptoPriceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crypto")
@CrossOrigin(origins = "http://localhost:5173")
public class CryptoController {

    private final CryptoPriceService cryptoPriceService;

    public CryptoController(CryptoPriceService cryptoPriceService) {
        this.cryptoPriceService = cryptoPriceService;
    }

    // ✅ Get current USD price for a coin
    @GetMapping("/price/{coinId}")
    public double getCryptoPrice(@PathVariable String coinId) {
        return cryptoPriceService.getCryptoPriceInUSD(coinId.toLowerCase());
    }

    // ✅ Get basic info (id, symbol, name, image) for a single coin
    @GetMapping("/details/{coinId}")
    public Map<String, Object> getCoinDetails(@PathVariable String coinId) {
        return cryptoPriceService.getCoinDetails(coinId.toLowerCase());
    }

    // ✅ Get top 300 coins by market cap
    @GetMapping("/top")
    public List<Map<String, Object>> getTopCoins() {
        return cryptoPriceService.getTopCoins();
    }
}

