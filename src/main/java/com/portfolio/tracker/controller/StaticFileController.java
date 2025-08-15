package main.java.com.portfolio.tracker.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class StaticFileController {

    private final Map<String, String> mimeTypes = new HashMap<>();

    public StaticFileController() {
        // Initialize MIME types
        mimeTypes.put("css", "text/css");
        mimeTypes.put("js", "application/javascript");
        mimeTypes.put("ico", "image/x-icon");
        mimeTypes.put("html", "text/html");
        mimeTypes.put("png", "image/png");
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("svg", "image/svg+xml");
    }

    @GetMapping("/dist/**")
    public ResponseEntity<String> serveStaticFile(@RequestParam String path) {
        try {
            // Extract the file path from the request
            String filePath = "dist/" + path;
            
            // Determine MIME type from file extension
            String extension = getFileExtension(path);
            String mimeType = mimeTypes.getOrDefault(extension, "text/plain");
            
            Resource resource = new ClassPathResource(filePath);
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf(mimeType))
                    .body(content);
                    
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error reading file: " + e.getMessage());
        }
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<String> serveFavicon() {
        try {
            Resource resource = new ClassPathResource("dist/favicon.ico");
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("image/x-icon"))
                    .body(content);
                    
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error reading favicon: " + e.getMessage());
        }
    }

    private String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0 && lastDot < path.length() - 1) {
            return path.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
}
