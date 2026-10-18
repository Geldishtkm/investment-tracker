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
 */
@Service
public class TradeService {

    /** Bitcoin's genesis block; no crypto trade can be older. */
    private static final LocalDate EARLIEST_TRADE_DATE = LocalDate.of(2009, 1, 3);
    /** Relative tolerance for "sell everything", so floating-point leftovers don't keep a dust position. */
    private static final double DUST = 1e-9;

    private final TradeRepository tradeRepository;
    private final AssetService assetService;

    public TradeService(TradeRepository tradeRepository, AssetService assetService) {
        this.tradeRepository = tradeRepository;
        this.assetService = assetService;
    }

    /** Result of a trade: the updated holding, or null if a sell closed the position. */
    public record TradeResult(Trade trade, Asset asset) {}

    public List<Trade> getTrades(User user) {
        return tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(user.getId());
    }

    /** Record the buy that opened a newly added holding. */
    @Transactional
    public Trade recordOpeningBuy(Asset asset, LocalDate purchaseDate) {
        LocalDate date = validateDate(purchaseDate);
        return tradeRepository.save(new Trade(asset.getUser().getId(), asset.getId(), asset.getName(), Trade.Side.BUY,
                asset.getQuantity(), asset.getPurchasePricePerUnit(), date, true, Trade.Source.MANUAL));
    }

    /**
     * Keep the ledger in line when a holding is edited. A holding whose only trade is its opening
     * buy is a typo fix, so that buy is corrected; holdings with more history keep their trades.
     */
    @Transactional
    public void syncCorrectedHolding(Asset asset) {
        List<Trade> trades = tradeRepository.findByAssetId(asset.getId());
        if (trades.size() == 1 && trades.get(0).getSide() == Trade.Side.BUY
                && asset.getQuantity() > 0 && asset.getPurchasePricePerUnit() > 0) {
            trades.get(0).correct(asset.getName(), asset.getQuantity(), asset.getPurchasePricePerUnit());
            tradeRepository.save(trades.get(0));
        }
    }

    @Transactional
    public TradeResult trade(User user, Long assetId, Trade.Side side, double quantity, double price, LocalDate date) {
        if (side == null) throw new IllegalArgumentException("Trade side must be BUY or SELL.");
        if (!(quantity > 0) || !Double.isFinite(quantity)) throw new IllegalArgumentException("Quantity must be a positive number.");
        if (!(price > 0) || !Double.isFinite(price)) throw new IllegalArgumentException("Price must be a positive number.");
        LocalDate tradeDate = validateDate(date);
        Asset asset = assetService.getAssetByIdAndUser(assetId, user);
        double held = asset.getQuantity();

        Trade trade = tradeRepository.save(new Trade(user.getId(), asset.getId(), asset.getName(), side,
                quantity, price, tradeDate, true, Trade.Source.MANUAL));

        if (side == Trade.Side.BUY) {
            double newQuantity = held + quantity;
            double averageCost = (held * asset.getPurchasePricePerUnit() + quantity * price) / newQuantity;
            asset.setQuantity(newQuantity);
            asset.setPurchasePricePerUnit(averageCost);
            return new TradeResult(trade, assetService.saveAsset(asset));
        }

        if (quantity > held * (1 + DUST)) {
            throw new IllegalArgumentException("You can't sell " + format(quantity) + " " + asset.getName()
                    + ": the holding is only " + format(held) + ".");
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
