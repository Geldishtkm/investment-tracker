package com.portfolio.tracker.config;

import com.portfolio.tracker.analytics.PortfolioDataException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Turns {@link PortfolioDataException} into HTTP 422 with a message the UI shows as-is, e.g.
 * {"error": "No assets available. Please add assets to your portfolio first.", "code": "NO_ASSETS"}.
 */
@RestControllerAdvice
public class AnalyticsExceptionHandler {

    @ExceptionHandler(PortfolioDataException.class)
    public ResponseEntity<Map<String, String>> handlePortfolioData(PortfolioDataException e) {
        HttpStatus status = e.getReason() == PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status)
                .body(Map.of("error", e.getMessage(), "code", e.getReason().name()));
    }
}
