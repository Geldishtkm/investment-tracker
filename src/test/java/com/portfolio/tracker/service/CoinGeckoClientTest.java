package com.portfolio.tracker.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("CoinGecko client: API key and rate limiting")
class CoinGeckoClientTest {

    private static final String PRICE_PATH = "/simple/price?ids=bitcoin&vs_currencies=usd";

    @Test
    @DisplayName("Sends the demo API key header when COINGECKO_API_KEY is set")
    void sendsApiKeyHeader() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo(CoinGeckoClient.BASE_URL + PRICE_PATH))
                .andExpect(header("x-cg-demo-api-key", "CG-test-key"))
                .andRespond(withSuccess("{\"bitcoin\":{\"usd\":81000}}", MediaType.APPLICATION_JSON));

        String body = new CoinGeckoClient(rest, "CG-test-key", 60).get(PRICE_PATH);

        assertTrue(body.contains("81000"));
        server.verify();
    }

    @Test
    @DisplayName("Sends no key header when no API key is configured")
    void omitsHeaderWithoutKey() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo(CoinGeckoClient.BASE_URL + PRICE_PATH))
                .andExpect(headerDoesNotExist("x-cg-demo-api-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        new CoinGeckoClient(rest, "", 60).get(PRICE_PATH);
        server.verify();
    }

    @Test
    @DisplayName("After a 429 it honours Retry-After and sends no further requests until it passes")
    void pausesAfterRateLimit() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");
        server.expect(requestTo(CoinGeckoClient.BASE_URL + PRICE_PATH))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));
        CoinGeckoClient client = new CoinGeckoClient(rest, "", 60);

        CoinGeckoClient.RateLimitedException first =
                assertThrows(CoinGeckoClient.RateLimitedException.class, () -> client.get(PRICE_PATH));
        assertEquals(30, first.getRetryAfterSeconds());

        // A second call fails fast without hitting CoinGecko (the mock would reject an unexpected request)
        CoinGeckoClient.RateLimitedException second =
                assertThrows(CoinGeckoClient.RateLimitedException.class, () -> client.get(PRICE_PATH));
        assertTrue(second.getRetryAfterSeconds() > 0 && second.getRetryAfterSeconds() <= 30);
        assertTrue(client.secondsUntilAvailable() > 0);
        server.verify();
    }

    @Test
    @DisplayName("Uses the configured cooldown when a 429 has no Retry-After header")
    void defaultCooldownWithoutRetryAfter() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo(CoinGeckoClient.BASE_URL + PRICE_PATH))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        CoinGeckoClient client = new CoinGeckoClient(rest, "", 45);
        CoinGeckoClient.RateLimitedException e =
                assertThrows(CoinGeckoClient.RateLimitedException.class, () -> client.get(PRICE_PATH));
        assertEquals(45, e.getRetryAfterSeconds());
    }
}
