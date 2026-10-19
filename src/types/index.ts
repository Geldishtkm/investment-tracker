export interface Asset {
  id: number;
  name: string;
  quantity: number;
  pricePerUnit: number;
  purchasePricePerUnit: number;
  initialInvestment: number;
  /** ISO time the current price was quoted by the market; null if it never was. */
  priceUpdatedAt?: string | null;
}

export interface AssetFormData {
  name: string;
  quantity: string;
  pricePerUnit: string;
  purchasePricePerUnit: string;
  initialInvestment: string;
}

// Authentication Types
export interface LoginCredentials {
  username: string;
  password: string;
}

export interface RegisterCredentials {
  username: string;
  email: string;
  password: string;
}

/** One row of the analytics endpoint's asset breakdown. */
export interface AssetBreakdownItem {
  id: number;
  name: string;
  quantity: number;
  currentPrice: number;
  purchasePrice: number;
  currentValue: number;
  initialInvestment: number;
  individualRoi: number;
}

/** Response of GET /api/assets/analytics. Risk fields are present only when riskMetricsAvailable. */
export interface PortfolioAnalytics {
  totalValue: number;
  totalInitialInvestment: number;
  totalAssets: number;
  roi: number | null;
  sharpeRatio?: number;
  volatility?: number;
  beta?: number;
  diversificationScore?: number;
  annualizedReturn?: number;
  effectiveNumberOfAssets?: number;
  herfindahlIndex?: number;
  averageCorrelation?: number | null;
  effectiveIndependentBets?: number | null;
  diversificationRatio?: number | null;
  riskFreeRate?: number;
  benchmark?: string;
  observations?: number;
  historyStart?: string;
  historyEnd?: string;
  assetBreakdown: AssetBreakdownItem[];
  riskMetricsAvailable: boolean;
  riskMetricsMessage?: string;
  riskMetricsCode?: string;
}

export interface RegisterResponse {
  message: string;
  requiresVerification: boolean;
  email: string;
  emailSent: boolean;
  /** True when this repeated an unverified signup that was still waiting for its code. */
  resumed: boolean;
  codeExpiresInSeconds: number;
  resendCooldownSeconds: number;
}

export interface AuthResponse {
  token: string;
  refreshToken?: string;
  expiresInSeconds?: number;
  refreshExpiresInSeconds?: number;
  message?: string;
  username?: string;
  requiresMfa?: boolean;
}

export interface User {
  username: string;
  isAuthenticated: boolean;
  mfaEnabled?: boolean;
  mfaSecret?: string;
}

// MFA Types
export interface MfaSetupResponse {
  success: boolean;
  message: string;
  secret?: string;
  qrCode?: string;
  backupCodes?: string[];
}

export interface MfaVerificationResponse {
  success: boolean;
  message: string;
  token?: string;
  requiresMfa?: boolean;
}

export interface MfaVerificationRequest {
  username: string;
  code: string;
}

export interface ApiResponse<T> {
  data: T;
  message?: string;
  error?: string;
}

export interface CryptoPrice {
  coinId: string;
  price: number;
  lastUpdated: string;
}

export interface AssetWithPrice extends Asset {
  currentPrice?: number;
  priceChange?: number;
  priceChangePercent?: number;
}

/** A coin from CoinGecko's /coins/markets list (top 100 by market cap, refreshed every 10 minutes). */
export interface Coin {
  id: string;
  symbol: string;
  name: string;
  image: string;
  current_price: number;
  market_cap: number;
  market_cap_rank?: number | null;
  total_volume?: number | null;
  high_24h?: number | null;
  low_24h?: number | null;
  price_change_percentage_24h: number;
  price_change_percentage_7d_in_currency?: number | null;
  /** Hourly prices over the last 7 days. */
  sparkline_in_7d?: { price: number[] } | null;
}

export interface PriceHistoryPoint {
  timestamp: number;
  price: number;
}

export interface PriceHistoryData {
  coinId: string;
  data: PriceHistoryPoint[];
}

// New Portfolio Analytics Types
export interface PortfolioMetrics {
  totalValue: number;
  totalInvestment: number;
  roi: number;
  roiPercentage: number;
  sharpeRatio: number;
  volatility: number;
  assetCount: number;
  topPerformer: string;
  worstPerformer: string;
}

export interface AssetPerformance {
  id: number;
  name: string;
  currentValue: number;
  initialInvestment: number;
  roi: number;
  roiPercentage: number;
  weight: number; // Portfolio weight percentage
}

export interface RiskMetrics {
  sharpeRatio: number;
  volatility: number;
  beta: number;
  diversificationScore: number;
} 
// Institutional Trader Evaluation (GET /api/analytics/institutional-score)

export type FlagSeverity = 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export type PillarKey = 'risk' | 'behavior' | 'consistency' | 'execution';

/** One scored metric. score is 0–1; points = weight × score. Unavailable metrics score 0 and explain why in detail. */
export interface ScoreComponent {
  key: string;
  name: string;
  available: boolean;
  value: number | null;
  displayValue: string;
  weight: number;
  score: number;
  points: number;
  detail: string;
}

export interface ScorePillar {
  key: PillarKey;
  name: string;
  available: boolean;
  points: number;
  maxPoints: number;
  /** Share of the pillar's points whose metrics had data (0–1). */
  coverage: number;
  components: ScoreComponent[];
}

export interface DiagnosticFlag {
  id: string;
  severity: FlagSeverity;
  pillar: PillarKey;
  title: string;
  detail: string;
  recommendation: string;
}

export interface RankTier {
  rank: number;
  name: string;
  minScore: number;
  maxScore: number;
}

/** A limit on the score, e.g. "No sells recorded" capping at 499. */
export interface ScoreCap {
  id: string;
  maxScore: number;
  title: string;
  detail: string;
}

/** One requirement of a tier gate, e.g. Return on Capital: +0.4% (Target: ≥ +5.0%). */
export interface GateRequirement {
  label: string;
  met: boolean;
  actual: string;
  name: string;
  target: string;
  /** How far the actual value is towards the target, 0–1. */
  progress: number;
}

export interface TierGate {
  minScore: number;
  name: string;
  unlocked: boolean;
  requirements: GateRequirement[];
}

/** Concentration and correlation of the current holdings (see Diversification.java). */
export interface PortfolioDiversification {
  holdings: number;
  /** Herfindahl–Hirschman index: Σ w², 1 = a single holding. */
  hhi: number;
  /** N_eff = 1 / HHI. */
  effectiveHoldings: number;
  /** Weight-averaged pairwise correlation ρ̄; null without history or with one holding. */
  averageCorrelation: number | null;
  /** Correlation-adjusted effective number of independent bets (diversification ratio²). */
  effectiveBets: number | null;
  diversificationRatio: number | null;
  /** Component scores, 0–1. */
  concentrationScore: number;
  correlationScore: number | null;
  betsScore: number | null;
  /** 0–100. */
  score: number;
  positions: { id: string; name: string; weight: number; volatility: number | null }[];
  /** Correlation matrix in position order; null without history. */
  correlations: number[][] | null;
  /** Every pair, most correlated first. */
  pairs: { a: string; b: string; correlation: number; combinedWeight: number }[];
  observations: number;
  /** Why correlations are missing, or null when they are present. */
  correlationReason: string | null;
}

export interface InstitutionalScore {
  /** Final score after evidence caps. */
  score: number;
  /** Weighted pillar total before caps. */
  rawScore: number;
  /** Every cap that applies, lowest first; the first one is binding when it is below rawScore. */
  caps: ScoreCap[];
  gates: TierGate[];
  maxScore: number;
  /** True when some pillars couldn't be scored and the total is scaled over the rest. */
  provisional: boolean;
  pillarsScored: number;
  tier: RankTier;
  tiers: RankTier[];
  pillars: ScorePillar[];
  flags: DiagnosticFlag[];
  strengths: string[];
  coverage: { trades: number; datedTrades: number; closedLots: number; observations: number };
  /** Null when there are no current holdings. */
  diversification: PortfolioDiversification | null;
  riskTargetVolatility: number | null;
  /** How current the inputs are: prices older than 15 minutes, or history ending before yesterday, are stale. */
  dataFreshness: {
    pricesAsOf: string | null;
    stalePrices: string[];
    historyThrough: string | null;
    historyStale: boolean;
  };
  generatedAt: string;
}

/** Result of POST /api/assets/refresh-prices. */
export interface PriceRefreshResult {
  updated: number;
  unpriced: string[];
  /** Holdings still valued at an old (or never refreshed) price. */
  stale: string[];
  oldestPriceAt: string | null;
}

export interface TradeRequest {
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  /** YYYY-MM-DD; today when omitted. */
  date?: string;
}

export interface TradeResult {
  asset: Asset | null;
  positionClosed: boolean;
}

/** The signed-in user's paper-trading account (GET /api/account). */
export interface Account {
  username: string;
  email: string | null;
  /** S₀: the starting bankroll, null until the user sets it. */
  initialBankroll: number | null;
  bankrollSetAt: string | null;
  /** True until S₀ is set: the app asks for it before anything else. */
  bankrollRequired: boolean;
  /** S₀ − buys + sells; null without S₀. */
  cash: number | null;
  /** Buys − sells recorded so far. */
  netInvested: number;
  /** The smallest S₀ that covers the trades already recorded. */
  minimumBankroll: number;
  hasPortfolio: boolean;
}
