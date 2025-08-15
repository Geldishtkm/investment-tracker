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
        return "<!DOCTYPE html>" +
               "<html lang=\"en\">" +
               "<head>" +
               "<meta charset=\"UTF-8\">" +
               "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
               "<title>Portfolio Tracker</title>" +
               "<link rel=\"stylesheet\" href=\"/dist/assets/index-ff80a9a3.css\">" +
               "</head>" +
               "<body>" +
               "<div id=\"root\"></div>" +
               "<script type=\"module\" src=\"/dist/assets/index-3ea4737f.js\"></script>" +
               "</body>" +
               "</html>";
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
