package com.portfolio.tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class PortfolioTrackerApplication {

    public static void main(String[] args) {
        System.out.println("🚀 Starting Portfolio Tracker Application...");
        System.out.println("📊 Initializing portfolio management system...");
        
        SpringApplication.run(PortfolioTrackerApplication.class, args);
        
        System.out.println("✅ Portfolio Tracker Application started successfully!");
        System.out.println("🌐 Ready to track your investments!");
    }

    @Bean
    public RestTemplate restTemplate() {
        // Configure RestTemplate for making HTTP requests to external APIs
        return new RestTemplate();
    }
} 