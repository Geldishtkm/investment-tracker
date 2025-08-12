package com.portfolio.tracker;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    
    @GetMapping("/test")
    public String test() {
        return "Hello World! Spring Boot is working!";
    }
    
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
