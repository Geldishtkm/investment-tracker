package com.portfolio.tracker.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

@RestController
@CrossOrigin(origins = "*")
public class StaticFileController {

    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    
    static {
        MIME_TYPES.put("css", "text/css");
        MIME_TYPES.put("js", "application/javascript");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("woff", "font/woff");
        MIME_TYPES.put("woff2", "font/woff2");
        MIME_TYPES.put("ttf", "font/ttf");
        MIME_TYPES.put("eot", "application/vnd.ms-fontobject");
    }

    @GetMapping("/dist/**")
    public ResponseEntity<byte[]> serveDistFiles(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String filePath = requestURI.substring(1); // Remove leading slash
        
        try {
            Resource resource = new ClassPathResource(filePath);
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            byte[] content = resource.getInputStream().readAllBytes();
            String contentType = getContentType(filePath);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf(contentType))
                    .body(content);
                    
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<byte[]> serveFavicon() {
        try {
            Resource resource = new ClassPathResource("dist/favicon.ico");
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            byte[] content = resource.getInputStream().readAllBytes();
            
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("image/x-icon"))
                    .body(content);
                    
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/assets/**")
    public ResponseEntity<byte[]> serveAssets(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String filePath = "dist" + requestURI; // Map /assets/ to /dist/assets/
        
        try {
            Resource resource = new ClassPathResource(filePath);
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            byte[] content = resource.getInputStream().readAllBytes();
            String contentType = getContentType(filePath);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf(contentType))
                    .body(content);
                    
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String getContentType(String filePath) {
        String extension = "";
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = filePath.substring(lastDotIndex + 1).toLowerCase();
        }
        
        return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
