package com.portfolio.tracker.controller;

import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.analytics.TraderScoring;
import com.portfolio.tracker.analytics.TraderScoring.Evaluation;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.BehavioralAnalyticsService;
import com.portfolio.tracker.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Institutional Trader Evaluation: the 0–1000 Trader Index Score and the user's risk target. */
@RestController
@RequestMapping("/api/analytics")
public class InstitutionalAnalyticsController {

    private final BehavioralAnalyticsService analyticsService;
    private final UserService userService;

    public InstitutionalAnalyticsController(BehavioralAnalyticsService analyticsService, UserService userService) {
        this.analyticsService = analyticsService;
        this.userService = userService;
    }

    @GetMapping("/institutional-score")
    public ResponseEntity<?> institutionalScore(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        BehavioralAnalyticsService.Result result = analyticsService.evaluate(user);
        Evaluation e = result.evaluation();
        if (e.tier() == null) {
            throw new PortfolioDataException(PortfolioDataException.Reason.INSUFFICIENT_HISTORY,
                    "None of the four pillars can be scored yet. Add holdings with price history or record trades.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("score", e.score());
        body.put("rawScore", e.rawScore());
        body.put("maxScore", TraderScoring.MAX_SCORE);
        body.put("provisional", e.provisional());
        body.put("pillarsScored", e.pillarsScored());
        body.put("tier", e.tier());
        body.put("tiers", TraderScoring.TIERS);
        body.put("pillars", e.pillars());
        body.put("caps", e.caps());
        body.put("gates", e.gates());
        body.put("flags", e.flags());
        body.put("strengths", e.strengths());
        body.put("coverage", e.coverage());
        body.put("diversification", e.diversification());
        body.put("riskTargetVolatility", user.getRiskTargetVolatility());
        BehavioralAnalyticsService.Freshness f = result.freshness();
        Map<String, Object> freshness = new LinkedHashMap<>();
        freshness.put("pricesAsOf", f.pricesAsOf() == null ? null : f.pricesAsOf().toString());
        freshness.put("stalePrices", f.stalePrices());
        freshness.put("historyThrough", f.historyThrough() == null ? null : f.historyThrough().toString());
        freshness.put("historyStale", f.historyStale());
        body.put("dataFreshness", freshness);
        body.put("generatedAt", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    /** Set or clear (null) the target annual volatility, e.g. {"targetVolatility": 0.4} for 40%. */
    @PutMapping("/risk-target")
    public ResponseEntity<?> setRiskTarget(@RequestBody(required = false) Map<String, Object> request,
                                           Authentication authentication) {
        Object raw = request == null ? null : request.get("targetVolatility");
        Double target;
        if (raw == null) {
            target = null;
        } else if (raw instanceof Number n && n.doubleValue() >= 0.01 && n.doubleValue() <= 3.0) {
            target = n.doubleValue();
        } else {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "targetVolatility must be between 0.01 and 3.0 (1% to 300% a year), or null to clear it."));
        }
        User user = userService.findByUsername(authentication.getName());
        user.setRiskTargetVolatility(target);
        userService.save(user);
        Map<String, Object> body = new HashMap<>();
        body.put("riskTargetVolatility", target);
        return ResponseEntity.ok(body);
    }
}
