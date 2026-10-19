package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Trades carry their execution time when it is known")
class BehavioralAnalyticsServiceTest {

    @Test
    @DisplayName("A trade recorded on its own trade date was placed then; backdated and imported trades have no time")
    void executedAt() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Trade live = new Trade(1L, 1L, "Bitcoin", Trade.Side.BUY, 1, 100, today, true, Trade.Source.MANUAL);
        assertNotNull(BehavioralAnalyticsService.executedAt(live));

        Trade backdated = new Trade(1L, 1L, "Bitcoin", Trade.Side.BUY, 1, 100, today.minusDays(30), true, Trade.Source.MANUAL);
        assertNull(BehavioralAnalyticsService.executedAt(backdated));

        Trade imported = new Trade(1L, 1L, "Bitcoin", Trade.Side.BUY, 1, 100, today, false, Trade.Source.IMPORTED);
        assertNull(BehavioralAnalyticsService.executedAt(imported));
    }
}
