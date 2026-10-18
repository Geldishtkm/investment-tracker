package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.Trade;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Trade ledger: buys, sells and holding updates")
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private AssetService assetService;

    private TradeService service;
    private User user;
    private Asset btc;

    @BeforeEach
    void setUp() {
        service = new TradeService(tradeRepository, assetService);
        user = new User();
        user.setId(7L);
        btc = new Asset("Bitcoin", 2.0, 60000, 50000);
        btc.setId(11L);
        btc.setUser(user);
        when(assetService.getAssetByIdAndUser(11L, user)).thenReturn(btc);
        when(assetService.saveAsset(any(Asset.class))).thenAnswer(inv -> {
            Asset a = inv.getArgument(0);
            a.setInitialInvestment(a.getQuantity() * a.getPurchasePricePerUnit());
            return a;
        });
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Buying more raises the quantity and averages the purchase price")
    void buyAveragesCost() {
        TradeService.TradeResult r = service.trade(user, 11L, Trade.Side.BUY, 1.0, 80000, LocalDate.of(2026, 5, 1));

        assertEquals(3.0, r.asset().getQuantity(), 1e-12);
        assertEquals((2 * 50000 + 80000) / 3.0, r.asset().getPurchasePricePerUnit(), 1e-9);
        assertEquals(180000, r.asset().getInitialInvestment(), 1e-6);
        assertEquals(Trade.Side.BUY, r.trade().getSide());
        assertEquals(LocalDate.of(2026, 5, 1), r.trade().getTradeDate());
    }

    @Test
    @DisplayName("A partial sell lowers the quantity and keeps the average cost")
    void partialSell() {
        TradeService.TradeResult r = service.trade(user, 11L, Trade.Side.SELL, 0.5, 70000, null);

        assertEquals(1.5, r.asset().getQuantity(), 1e-12);
        assertEquals(50000, r.asset().getPurchasePricePerUnit(), 1e-12);
        assertEquals(LocalDate.now(ZoneOffset.UTC), r.trade().getTradeDate()); // no date means today
        verify(assetService, never()).removeHolding(any());
    }

    @Test
    @DisplayName("Selling everything closes the holding but keeps the trade")
    void fullSellClosesPosition() {
        TradeService.TradeResult r = service.trade(user, 11L, Trade.Side.SELL, 2.0, 70000, null);

        assertNull(r.asset());
        verify(assetService).removeHolding(btc);
        verify(tradeRepository).save(any(Trade.class));
        verify(tradeRepository, never()).deleteByAssetId(any());
    }

    @Test
    @DisplayName("Selling more than is held is rejected before anything changes")
    void oversellRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.trade(user, 11L, Trade.Side.SELL, 2.5, 70000, null));
        assertTrue(e.getMessage().contains("only 2"), e.getMessage());
        assertEquals(2.0, btc.getQuantity());
        verify(assetService, never()).saveAsset(any());
    }

    @Test
    @DisplayName("Invalid quantity, price or date is rejected")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> service.trade(user, 11L, Trade.Side.BUY, 0, 1, null));
        assertThrows(IllegalArgumentException.class, () -> service.trade(user, 11L, Trade.Side.BUY, 1, -5, null));
        assertThrows(IllegalArgumentException.class, () -> service.trade(user, 11L, Trade.Side.BUY, Double.NaN, 1, null));
        assertThrows(IllegalArgumentException.class, () -> service.trade(user, 11L, null, 1, 1, null));
        LocalDate future = LocalDate.now(ZoneOffset.UTC).plusDays(5);
        assertThrows(IllegalArgumentException.class, () -> service.trade(user, 11L, Trade.Side.BUY, 1, 1, future));
        assertThrows(IllegalArgumentException.class, () -> service.trade(user, 11L, Trade.Side.BUY, 1, 1, LocalDate.of(2005, 1, 1)));
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Adding a holding records its opening buy with the given purchase date")
    void openingBuy() {
        Trade t = service.recordOpeningBuy(btc, LocalDate.of(2025, 12, 24));
        assertEquals(2.0, t.getQuantity());
        assertEquals(50000, t.getPrice());
        assertEquals(LocalDate.of(2025, 12, 24), t.getTradeDate());
        assertTrue(t.isDateKnown());
        assertEquals(Trade.Source.MANUAL, t.getSource());
    }

    @Test
    @DisplayName("Editing a holding with only its opening buy corrects that buy; one with more trades keeps them")
    void correctionSync() {
        Trade opening = new Trade(7L, 11L, "Bitcoin", Trade.Side.BUY, 2.0, 50000, LocalDate.of(2026, 1, 1), true, Trade.Source.MANUAL);
        when(tradeRepository.findByAssetId(11L)).thenReturn(List.of(opening));
        btc.setQuantity(3.0);
        btc.setPurchasePricePerUnit(45000);
        service.syncCorrectedHolding(btc);
        assertEquals(3.0, opening.getQuantity());
        assertEquals(45000, opening.getPrice());

        Trade second = new Trade(7L, 11L, "Bitcoin", Trade.Side.SELL, 1.0, 70000, LocalDate.of(2026, 2, 1), true, Trade.Source.MANUAL);
        when(tradeRepository.findByAssetId(11L)).thenReturn(List.of(opening, second));
        btc.setQuantity(9.0);
        service.syncCorrectedHolding(btc);
        assertEquals(3.0, opening.getQuantity()); // unchanged: the history is the source of truth
    }
}
