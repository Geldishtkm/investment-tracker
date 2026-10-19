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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Trade ledger: buys, sells and holding updates")
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private AssetService assetService;

    @Mock
    private AccountService accountService;

    @Mock
    private MarketPriceGuard priceGuard;

    private TradeService service;
    private User user;
    private Asset btc;

    @BeforeEach
    void setUp() {
        service = new TradeService(tradeRepository, assetService, accountService, priceGuard);
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
    @DisplayName("Adding a holding checks its price and cash, then records its opening buy")
    void openingBuy() {
        Asset eth = new Asset("Ethereum", 2.0, 2500, 2400);
        Asset saved = service.openHolding(user, eth, LocalDate.of(2025, 12, 24));
        verify(priceGuard).verify("Ethereum", 2400, LocalDate.of(2025, 12, 24));
        verify(accountService).requireCash(user, 4800, 0.0);
        assertSame(user, saved.getUser());
        org.mockito.ArgumentCaptor<Trade> trade = org.mockito.ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(trade.capture());
        assertEquals(2.0, trade.getValue().getQuantity());
        assertEquals(2400, trade.getValue().getPrice());
        assertEquals(LocalDate.of(2025, 12, 24), trade.getValue().getTradeDate());
        assertTrue(trade.getValue().isDateKnown());
        assertEquals(Trade.Source.MANUAL, trade.getValue().getSource());
    }

    @Test
    @DisplayName("A holding can't be added without the cash to pay for it")
    void openingBuyNeedsCash() {
        doThrow(new AccountService.AccountException(org.springframework.http.HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", "Not enough cash"))
                .when(accountService).requireCash(eq(user), anyDouble(), anyDouble());
        assertThrows(AccountService.AccountException.class,
                () -> service.openHolding(user, new Asset("Ethereum", 1000, 2500, 2500), null));
        verify(assetService, never()).saveAsset(any());
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Without a bankroll a buy is refused before its price is even checked")
    void bankrollFirst() {
        doThrow(new AccountService.AccountException(org.springframework.http.HttpStatus.CONFLICT, "BANKROLL_REQUIRED", "Set your bankroll"))
                .when(accountService).requireBankroll(user);
        AccountService.AccountException e = assertThrows(AccountService.AccountException.class,
                () -> service.openHolding(user, new Asset("Ethereum", 1, 2500, 1), null));
        assertEquals("BANKROLL_REQUIRED", e.getCode());
        verify(priceGuard, never()).verify(any(), anyDouble(), any());
    }

    @Test
    @DisplayName("Buys are paid from cash at a checked price; sells are price-checked but need no cash")
    void tradesCheckCashAndPrice() {
        service.trade(user, 11L, Trade.Side.BUY, 1.0, 80000, LocalDate.of(2026, 5, 1));
        verify(priceGuard).verify("Bitcoin", 80000, LocalDate.of(2026, 5, 1));
        verify(accountService).requireCash(user, 80000, 0.0);

        doThrow(new IllegalArgumentException("far from market")).when(priceGuard).verify(eq("Bitcoin"), eq(1.0), any());
        assertThrows(IllegalArgumentException.class, () -> service.trade(user, 11L, Trade.Side.SELL, 1.0, 1.0, null));
        verify(accountService, times(1)).requireCash(any(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Correcting a holding with only its opening buy re-checks price and cash against the old cost")
    void correction() {
        Trade opening = new Trade(7L, 11L, "Bitcoin", Trade.Side.BUY, 2.0, 50000, LocalDate.of(2026, 1, 1), true, Trade.Source.MANUAL);
        when(tradeRepository.findByAssetId(11L)).thenReturn(List.of(opening));
        Asset updated = service.correctHolding(user, 11L, "Bitcoin", 3.0, 45000);
        verify(priceGuard).verify("Bitcoin", 45000, LocalDate.of(2026, 1, 1));
        verify(accountService).requireCash(user, 135000, 100000);
        assertEquals(3.0, updated.getQuantity());
        assertEquals(3.0, opening.getQuantity());
        assertEquals(45000, opening.getPrice());
    }

    @Test
    @DisplayName("A holding with more trades can't have its quantity edited; unchanged edits do nothing")
    void correctionWithHistoryRejected() {
        Trade opening = new Trade(7L, 11L, "Bitcoin", Trade.Side.BUY, 2.0, 50000, LocalDate.of(2026, 1, 1), true, Trade.Source.MANUAL);
        Trade second = new Trade(7L, 11L, "Bitcoin", Trade.Side.SELL, 1.0, 70000, LocalDate.of(2026, 2, 1), true, Trade.Source.MANUAL);
        when(tradeRepository.findByAssetId(11L)).thenReturn(List.of(opening, second));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.correctHolding(user, 11L, "Bitcoin", 9.0, 50000));
        assertTrue(e.getMessage().contains("Trade → Buy or Sell"), e.getMessage());
        assertEquals(2.0, btc.getQuantity());

        service.correctHolding(user, 11L, "Bitcoin", 2.0, 50000);
        verify(accountService, never()).requireCash(any(), anyDouble(), anyDouble());
    }
}
