package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.CryptoPriceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crypto")
@CrossOrigin(origins = "http://localhost:5173")
public class CryptoController {

    private final CryptoPriceService cryptoPriceService;

    public CryptoController(CryptoPriceService cryptoPriceService) {
        this.cryptoPriceService = cryptoPriceService;
    }

    // ✅ Get crypto price by coin ID
    @GetMapping("/price/{coinId}")
    public double getCryptoPrice(@PathVariable String coinId) {
        return cryptoPriceService.getCryptoPriceInUSD(coinId.toLowerCase());
    }
}
