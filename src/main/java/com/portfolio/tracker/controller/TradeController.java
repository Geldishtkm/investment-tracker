package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.Trade;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.TradeService;
import com.portfolio.tracker.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The trade ledger: list trades, and buy more of or sell part of a holding. */
@RestController
@RequestMapping("/api")
public class TradeController {

    private final TradeService tradeService;
    private final UserService userService;

    public TradeController(TradeService tradeService, UserService userService) {
        this.tradeService = tradeService;
        this.userService = userService;
    }

    @GetMapping("/trades")
    public List<Trade> trades(Authentication authentication) {
        return tradeService.getTrades(userService.findByUsername(authentication.getName()));
    }

    /**
     * Record a buy or sell on a holding: {"side": "BUY"|"SELL", "quantity": 0.5, "price": 64000, "date": "2026-03-14"}.
     * The date is optional (today). Selling the whole quantity closes the holding.
     */
    @PostMapping("/assets/{assetId}/trades")
    public ResponseEntity<?> trade(@PathVariable Long assetId, @RequestBody Map<String, Object> request,
                                   Authentication authentication) {
        Trade.Side side;
        double quantity, price;
        LocalDate date;
        try {
            side = Trade.Side.valueOf(String.valueOf(request.get("side")).trim().toUpperCase(Locale.ROOT));
            quantity = number(request.get("quantity"), "quantity");
            price = number(request.get("price"), "price");
            Object rawDate = request.get("date");
            date = rawDate == null || String.valueOf(rawDate).isBlank() ? null : LocalDate.parse(String.valueOf(rawDate));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e instanceof DateTimeParseException
                    ? "date must be in YYYY-MM-DD format" : e.getMessage().startsWith("No enum")
                    ? "side must be BUY or SELL" : e.getMessage()));
        }

        User user = userService.findByUsername(authentication.getName());
        try {
            TradeService.TradeResult result = tradeService.trade(user, assetId, side, quantity, price, date);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("trade", result.trade());
            body.put("asset", result.asset());
            body.put("positionClosed", result.asset() == null);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", "Asset not found"));
        }
    }

    private static double number(Object value, String field) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }
}
