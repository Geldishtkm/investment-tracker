package com.portfolio.tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.web.client.RestTemplate;
// import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.portfolio.tracker")
// @EnableJpaRepositories  // Temporarily disabled for Railway deployment
@EnableScheduling
@EnableCaching
public class PortfolioTrackerApplication {

    public static void main(String[] args) {
        System.out.println("Starting Portfolio Tracker Application...");
        SpringApplication.run(PortfolioTrackerApplication.class, args);
        System.out.println("Portfolio Tracker Application started successfully!");
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
} 