export interface Asset {
  id: number;
  name: string;
  quantity: number;
  pricePerUnit: number;
  purchasePricePerUnit: number;
  initialInvestment: number;
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

export interface Coin {
  id: string;
  symbol: string;
  name: string;
  image: string;
  current_price: number;
  market_cap: number;
  price_change_percentage_24h: number;
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

export interface InstitutionalScore {
  score: number;
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
  riskTargetVolatility: number | null;
  generatedAt: string;
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
