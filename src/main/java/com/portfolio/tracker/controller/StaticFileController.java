package main.java.com.portfolio.tracker.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@CrossOrigin(origins = "*")
public class StaticFileController {

    @GetMapping("/dist/assets/index-ff80a9a3.css")
    public ResponseEntity<String> serveCss() {
        return serveFile("dist/assets/index-ff80a9a3.css", "text/css");
    }
    
    @GetMapping("/dist/assets/index-3ea4737f.js")
    public ResponseEntity<String> serveJs() {
        return serveFile("dist/assets/index-3ea4737f.js", "application/javascript");
    }
    
    @GetMapping("/favicon.ico")
    public ResponseEntity<String> serveFavicon() {
        return serveFile("dist/favicon.ico", "image/x-icon");
    }
    
    private ResponseEntity<String> serveFile(String filePath, String contentType) {
        try {
            Resource resource = new ClassPathResource(filePath);
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf(contentType))
                    .body(content);
                    
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error reading file: " + e.getMessage());
        }
    }
}
