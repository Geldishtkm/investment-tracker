package com.portfolio.tracker.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class HealthController {

    @GetMapping(value = "/", produces = "text/html")
    public String home() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Portfolio Tracker</title>
                <style>
                    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
                    .success { color: green; font-size: 24px; }
                    .info { color: blue; font-size: 18px; }
                </style>
            </head>
            <body>
                <h1 class="success">🎉 Portfolio Tracker is Running! 🎉</h1>
                <p class="info">Your Spring Boot application is successfully deployed on Heroku!</p>
                <p>Check these endpoints:</p>
                <ul style="list-style: none; padding: 0;">
                    <li><a href="/health">/health</a> - Application health status</li>
                    <li><a href="/ping">/ping</a> - Simple ping test</li>
                </ul>
                <p><small>Deployed at: """ + LocalDateTime.now() + """</small></p>
            </body>
            </html>
            """;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("service", "Portfolio Tracker");
        health.put("version", "1.0.0");
        return ResponseEntity.ok(health);
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
