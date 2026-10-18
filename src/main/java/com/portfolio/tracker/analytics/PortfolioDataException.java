package com.portfolio.tracker.analytics;

/**
 * Raised when a risk or optimization calculation cannot be performed because of the portfolio's
 * contents or the available market data (as opposed to a server fault). Controllers return it as
 * HTTP 422 with a message the UI can show directly.
 */
public class PortfolioDataException extends RuntimeException {

    public enum Reason {
        NO_ASSETS,
        INSUFFICIENT_HISTORY,
        UNKNOWN_ASSET,
        MARKET_DATA_UNAVAILABLE,
        INVALID_VIEW
    }

    private final Reason reason;

    public PortfolioDataException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public static PortfolioDataException noAssets() {
        return new PortfolioDataException(Reason.NO_ASSETS,
                "No assets available. Please add assets to your portfolio first.");
    }
}
