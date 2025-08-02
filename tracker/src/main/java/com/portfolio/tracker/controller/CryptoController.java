package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.CryptoPriceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crypto")
@CrossOrigin(origins = "http://localhost:5173") // adjust for production
public class CryptoController {

    private final CryptoPriceService cryptoPriceService;

    public CryptoController(CryptoPriceService cryptoPriceService) {
        this.cryptoPriceService = cryptoPriceService;
    }

    // ✅ Get current USD price for a coin (like bitcoin, ethereum)
    @GetMapping("/price/{coinId}")
    public double getCryptoPrice(@PathVariable String coinId) {
        return cryptoPriceService.getCryptoPriceInUSD(coinId.toLowerCase());
    }

    // ✅ Get details (id, symbol, name, image) of a coin
    @GetMapping("/details/{coinId}")
    public Map<String, Object> getCoinDetails(@PathVariable String coinId) {
        return cryptoPriceService.getCoinDetails(coinId.toLowerCase());
    }

    // ✅ Get top coins from cache (auto-refreshes every 10 min)
    @GetMapping("/top")
    public List<Map<String, Object>> getTopCoins() {
        return cryptoPriceService.getTopCoins();
    }
}
