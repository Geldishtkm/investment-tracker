package com.portfolio.tracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single entry point for CoinGecko API calls. Adds the optional API key header and, after a
 * 429 Too Many Requests, stops calling CoinGecko until its Retry-After window has passed so we
 * don't extend the rate limit by retrying into it. Callers fall back to cached data meanwhile.
 */
@Component
public class CoinGeckoClient {

    private static final Logger logger = LoggerFactory.getLogger(CoinGeckoClient.class);

    public static final String BASE_URL = "https://api.coingecko.com/api/v3";
    private static final String DEMO_KEY_HEADER = "x-cg-demo-api-key";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final Duration defaultCooldown;
    private final AtomicReference<Instant> rateLimitedUntil = new AtomicReference<>(Instant.EPOCH);

    public CoinGeckoClient(RestTemplate restTemplate,
                           @Value("${coingecko.api-key:}") String apiKey,
                           @Value("${coingecko.rate-limit-cooldown-seconds}") long cooldownSeconds) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.defaultCooldown = Duration.ofSeconds(cooldownSeconds);
        logger.info("CoinGecko client using {} API access", this.apiKey.isEmpty() ? "public (keyless)" : "demo-key");
    }

    /** Thrown instead of calling CoinGecko while a rate limit is in effect, or when one is hit. */
    public static class RateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;

        public RateLimitedException(long retryAfterSeconds) {
            super("CoinGecko rate limit reached; retry in " + retryAfterSeconds + "s");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /**
     * GET a CoinGecko endpoint and return the raw JSON body.
     * @param pathAndQuery path below /api/v3, e.g. "/simple/price?ids=bitcoin&vs_currencies=usd"
     * @throws RateLimitedException if CoinGecko is rate limiting us (checked before any request is sent)
     * @throws HttpClientErrorException for other 4xx responses (e.g. 404 for an unknown coin)
     */
    public String get(String pathAndQuery) {
        long wait = secondsUntilAvailable();
        if (wait > 0) {
            throw new RateLimitedException(wait);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (!apiKey.isEmpty()) {
            headers.set(DEMO_KEY_HEADER, apiKey);
        }
        try {
            return restTemplate.exchange(BASE_URL + pathAndQuery, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class).getBody();
        } catch (HttpClientErrorException.TooManyRequests e) {
            Duration cooldown = retryAfter(e).orElse(defaultCooldown);
            rateLimitedUntil.set(Instant.now().plus(cooldown));
            logger.warn("CoinGecko rate limit hit; pausing CoinGecko calls for {}s", cooldown.getSeconds());
            throw new RateLimitedException(cooldown.getSeconds());
        }
    }

    /** Seconds until calls are allowed again (0 when not rate limited). */
    public long secondsUntilAvailable() {
        long ms = Duration.between(Instant.now(), rateLimitedUntil.get()).toMillis();
        return ms <= 0 ? 0 : (ms + 999) / 1000;
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    private static java.util.Optional<Duration> retryAfter(HttpClientErrorException e) {
        HttpHeaders headers = e.getResponseHeaders();
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) return java.util.Optional.empty();
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds > 0 ? java.util.Optional.of(Duration.ofSeconds(seconds)) : java.util.Optional.empty();
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty(); // HTTP-date form is not used by CoinGecko
        }
    }
}
