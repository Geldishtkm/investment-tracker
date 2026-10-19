package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.Trade;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Records buys and sells in the trade ledger and applies them to the user's holdings:
 * a buy raises the quantity and averages the purchase price, a sell lowers the quantity at an
 * unchanged average cost, and selling everything closes the holding while keeping its trades.
 *
 * <p>Trades are paper trades against the user's bankroll: a buy must be paid for from cash, and every
 * price must be close to the coin's market price on the trade date.
 */
@Service
public class TradeService {

    /** Bitcoin's genesis block; no crypto trade can be older. */
    private static final LocalDate EARLIEST_TRADE_DATE = LocalDate.of(2009, 1, 3);
    /** Relative tolerance for "sell everything", so floating-point leftovers don't keep a dust position. */
    private static final double DUST = 1e-9;

    private final TradeRepository tradeRepository;
    private final AssetService assetService;
    private final AccountService accountService;
    private final MarketPriceGuard priceGuard;

    public TradeService(TradeRepository tradeRepository, AssetService assetService, AccountService accountService,
                        MarketPriceGuard priceGuard) {
        this.tradeRepository = tradeRepository;
        this.assetService = assetService;
        this.accountService = accountService;
        this.priceGuard = priceGuard;
    }

    /** Result of a trade: the updated holding, or null if a sell closed the position. */
    public record TradeResult(Trade trade, Asset asset) {}

    public List<Trade> getTrades(User user) {
        return tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(user.getId());
    }

    /**
     * Add a new holding: its opening buy is checked against the market price and paid for from cash,
     * then the holding and the buy are saved together.
     */
    @Transactional
    public Asset openHolding(User user, Asset asset, LocalDate purchaseDate) {
        LocalDate date = validateDate(purchaseDate);
        accountService.requireBankroll(user);
        priceGuard.verify(asset.getName(), asset.getPurchasePricePerUnit(), date);
        accountService.requireCash(user, asset.getQuantity() * asset.getPurchasePricePerUnit(), 0.0);
        asset.setUser(user);
        Asset saved = assetService.saveAsset(asset);
        tradeRepository.save(new Trade(user.getId(), saved.getId(), saved.getName(), Trade.Side.BUY,
                saved.getQuantity(), saved.getPurchasePricePerUnit(), date, true, Trade.Source.MANUAL));
        return saved;
    }

    /**
     * Edit a holding. Its name, quantity and purchase price can only be corrected while its opening buy is
     * its only trade (a typo fix, re-checked against cash and the market price); after that they change
     * through buys and sells. The current price always comes from the market, so it isn't editable.
     */
    @Transactional
    public Asset correctHolding(User user, Long assetId, String name, double quantity, double purchasePrice) {
        Asset asset = assetService.getAssetByIdAndUser(assetId, user);
        String newName = name.trim();
        boolean changed = !newName.equals(asset.getName()) || quantity != asset.getQuantity()
                || purchasePrice != asset.getPurchasePricePerUnit();
        if (!changed) return asset;

        List<Trade> trades = tradeRepository.findByAssetId(asset.getId());
        if (trades.size() != 1 || trades.get(0).getSide() != Trade.Side.BUY) {
            throw new IllegalArgumentException(asset.getName() + " has trade history, so its quantity and cost change "
                    + "through trades: use Trade → Buy or Sell.");
        }
        Trade opening = trades.get(0);
        // A holding imported without a date is checked against today's price
        LocalDate date = opening.isDateKnown() ? opening.getTradeDate() : LocalDate.now(ZoneOffset.UTC);
        priceGuard.verify(newName, purchasePrice, date);
        accountService.requireCash(user, quantity * purchasePrice, opening.getQuantity() * opening.getPrice());

        asset.setName(newName);
        asset.setQuantity(quantity);
        asset.setPurchasePricePerUnit(purchasePrice);
        Asset saved = assetService.saveAsset(asset);
        opening.correct(newName, quantity, purchasePrice);
        tradeRepository.save(opening);
        return saved;
    }

    @Transactional
    public TradeResult trade(User user, Long assetId, Trade.Side side, double quantity, double price, LocalDate date) {
        if (side == null) throw new IllegalArgumentException("Trade side must be BUY or SELL.");
        if (!(quantity > 0) || !Double.isFinite(quantity)) throw new IllegalArgumentException("Quantity must be a positive number.");
        if (!(price > 0) || !Double.isFinite(price)) throw new IllegalArgumentException("Price must be a positive number.");
        LocalDate tradeDate = validateDate(date);
        Asset asset = assetService.getAssetByIdAndUser(assetId, user);
        double held = asset.getQuantity();
        if (side == Trade.Side.SELL && quantity > held * (1 + DUST)) {
            throw new IllegalArgumentException("You can't sell " + format(quantity) + " " + asset.getName()
                    + ": the holding is only " + format(held) + ".");
        }
        if (side == Trade.Side.BUY) accountService.requireBankroll(user);
        priceGuard.verify(asset.getName(), price, tradeDate);
        if (side == Trade.Side.BUY) accountService.requireCash(user, quantity * price, 0.0);

        Trade trade = tradeRepository.save(new Trade(user.getId(), asset.getId(), asset.getName(), side,
                quantity, price, tradeDate, true, Trade.Source.MANUAL));

        if (side == Trade.Side.BUY) {
            double newQuantity = held + quantity;
            double averageCost = (held * asset.getPurchasePricePerUnit() + quantity * price) / newQuantity;
            asset.setQuantity(newQuantity);
            asset.setPurchasePricePerUnit(averageCost);
            return new TradeResult(trade, assetService.saveAsset(asset));
        }

        double remaining = held - quantity;
        if (remaining <= held * DUST) {
            assetService.removeHolding(asset);
            return new TradeResult(trade, null);
        }
        asset.setQuantity(remaining); // the average cost of the remaining units is unchanged
        return new TradeResult(trade, assetService.saveAsset(asset));
    }

    /**
     * The trade date to record: today when none is given.
     * @throws IllegalArgumentException if the date is in the future or before 2009
     */
    public static LocalDate validateDate(LocalDate date) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (date == null) return today;
        // One day of slack: the user's local date can be ahead of UTC
        if (date.isAfter(today.plusDays(1))) throw new IllegalArgumentException("The trade date can't be in the future.");
        if (date.isBefore(EARLIEST_TRADE_DATE)) throw new IllegalArgumentException("The trade date is before crypto existed (2009).");
        return date;
    }

    private static String format(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
