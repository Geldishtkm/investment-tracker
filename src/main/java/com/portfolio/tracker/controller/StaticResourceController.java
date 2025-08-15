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
public class StaticResourceController {

    @GetMapping("/dist/**")
    public ResponseEntity<String> serveStaticFile(@RequestParam String path) {
        try {
            // Remove /dist prefix and get the file path
            String filePath = path.replace("/dist/", "");
            if (filePath.isEmpty()) {
                filePath = "index.html";
            }
            
            // Load file from classpath resources
            Resource resource = new ClassPathResource("dist/" + filePath);
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            // Read file content
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            // Determine content type based on file extension
            MediaType mediaType = getMediaType(filePath);
            
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(content);
                    
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error reading file: " + e.getMessage());
        }
    }
    
    private MediaType getMediaType(String fileName) {
        if (fileName.endsWith(".css")) {
            return MediaType.valueOf("text/css");
        } else if (fileName.endsWith(".js")) {
            return MediaType.valueOf("application/javascript");
        } else if (fileName.endsWith(".html")) {
            return MediaType.valueOf("text/html");
        } else if (fileName.endsWith(".ico")) {
            return MediaType.valueOf("image/x-icon");
        } else {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
