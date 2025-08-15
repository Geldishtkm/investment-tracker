package com.portfolio.tracker.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class HealthController {

    @GetMapping(value = "/", produces = "text/html")
    public ResponseEntity<String> home() {
        try {
            // Try to serve the test HTML file
            Resource resource = new ClassPathResource("dist/index-inline.html");
            
            if (resource.exists()) {
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(content);
            } else {
                // Fallback to simple HTML if file not found
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(getFallbackHtml());
            }
        } catch (IOException e) {
            // Fallback to simple HTML if error reading file
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(getFallbackHtml());
        }
    }

    private String getFallbackHtml() {
        return "<!DOCTYPE html>" +
               "<html lang=\"en\">" +
               "<head>" +
               "<meta charset=\"UTF-8\">" +
               "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
               "<title>Portfolio Tracker</title>" +
               "<style>" +
               "body { font-family: Arial, sans-serif; background: #1f2937; color: #f9fafb; margin: 0; padding: 40px; text-align: center; }" +
               ".container { max-width: 600px; margin: 0 auto; background: #374151; padding: 30px; border-radius: 16px; }" +
               "h1 { color: #10b981; }" +
               ".status { background: #059669; color: white; padding: 15px; border-radius: 8px; margin: 20px 0; }" +
               "</style>" +
               "</head>" +
               "<body>" +
               "<div class=\"container\">" +
               "<h1>🎯 Portfolio Tracker</h1>" +
               "<div class=\"status\">" +
               "✅ Application is running!<br>" +
               "Backend: Spring Boot<br>" +
               "Database: PostgreSQL<br>" +
               "Time: " + LocalDateTime.now().toString() +
               "</div>" +
               "<p>This is the fallback page. The main application should be accessible.</p>" +
               "</div>" +
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
