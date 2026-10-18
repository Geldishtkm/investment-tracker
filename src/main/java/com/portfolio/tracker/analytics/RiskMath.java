package com.portfolio.tracker.analytics;

import java.util.Arrays;

/**
 * Pure numerical routines used by the risk and optimization pipelines.
 * Everything here is stateless and operates only on the data passed in.
 */
public final class RiskMath {

    // Convergence settings for the iterative solvers (numerical tolerances, not model inputs)
    private static final int MAX_SOLVER_ITERATIONS = 20_000;
    private static final double SOLVER_TOLERANCE = 1e-12;
    private static final int BISECTION_ITERATIONS = 200;

    private RiskMath() {
    }

    // ---------------------------------------------------------------------
    // Descriptive statistics
    // ---------------------------------------------------------------------

    public static double mean(double[] x) {
        double sum = 0.0;
        for (double v : x) sum += v;
        return sum / x.length;
    }

    /** Unbiased sample variance (n - 1 denominator). */
    public static double sampleVariance(double[] x) {
        double m = mean(x);
        double ss = 0.0;
        for (double v : x) ss += (v - m) * (v - m);
        return ss / (x.length - 1);
    }

    public static double sampleStdDev(double[] x) {
        return Math.sqrt(sampleVariance(x));
    }

    /** Adjusted Fisher-Pearson sample skewness (G1), as used by Excel SKEW and pandas. */
    public static double skewness(double[] x) {
        int n = x.length;
        double m = mean(x);
        double m2 = 0.0, m3 = 0.0;
        for (double v : x) {
            double d = v - m;
            m2 += d * d;
            m3 += d * d * d;
        }
        m2 /= n;
        m3 /= n;
        if (m2 == 0.0) return 0.0;
        double g1 = m3 / Math.pow(m2, 1.5);
        return g1 * Math.sqrt((double) n * (n - 1)) / (n - 2);
    }

    /**
     * Sample kurtosis (not excess): the bias-corrected excess kurtosis (G2, as in Excel KURT)
     * plus 3, so a normal distribution reads 3.
     */
    public static double kurtosis(double[] x) {
        int n = x.length;
        double m = mean(x);
        double m2 = 0.0, m4 = 0.0;
        for (double v : x) {
            double d = v - m;
            m2 += d * d;
            m4 += d * d * d * d;
        }
        m2 /= n;
        m4 /= n;
        if (m2 == 0.0) return 3.0;
        double g2 = m4 / (m2 * m2) - 3.0;
        double excess = ((n - 1.0) / ((n - 2.0) * (n - 3.0))) * ((n + 1.0) * g2 + 6.0);
        return excess + 3.0;
    }

    /** Sample covariance of two equally long series. */
    public static double covariance(double[] x, double[] y) {
        double mx = mean(x), my = mean(y);
        double s = 0.0;
        for (int i = 0; i < x.length; i++) s += (x[i] - mx) * (y[i] - my);
        return s / (x.length - 1);
    }

    /**
     * Sample covariance matrix of a T x N matrix of observations (rows are periods,
     * columns are assets).
     */
    public static double[][] covarianceMatrix(double[][] observations) {
        int t = observations.length;
        int n = observations[0].length;
        double[] means = columnMeans(observations);
        double[][] cov = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double s = 0.0;
                for (int k = 0; k < t; k++) {
                    s += (observations[k][i] - means[i]) * (observations[k][j] - means[j]);
                }
                cov[i][j] = s / (t - 1);
                cov[j][i] = cov[i][j];
            }
        }
        return cov;
    }

    public static double[] columnMeans(double[][] observations) {
        int n = observations[0].length;
        double[] means = new double[n];
        for (double[] row : observations) {
            for (int j = 0; j < n; j++) means[j] += row[j];
        }
        for (int j = 0; j < n; j++) means[j] /= observations.length;
        return means;
    }

    /**
     * Empirical quantile with linear interpolation between order statistics
     * (Hyndman-Fan definition 7, the default in R, NumPy and Excel PERCENTILE.INC).
     */
    public static double quantile(double[] data, double p) {
        double[] sorted = data.clone();
        Arrays.sort(sorted);
        double h = (sorted.length - 1) * p;
        int lo = (int) Math.floor(h);
        int hi = (int) Math.ceil(h);
        return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo]);
    }

    // ---------------------------------------------------------------------
    // Normal distribution
    // ---------------------------------------------------------------------

    /**
     * Inverse of the standard normal CDF (Acklam's rational approximation, relative error
     * below 1.15e-9), so any confidence level gets its own z-score rather than a lookup table.
     */
    public static double inverseNormalCdf(double p) {
        if (p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException("Probability must be in (0, 1), got " + p);
        }
        final double[] a = {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
                1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
        final double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
                6.680131188771972e+01, -1.328068155288572e+01};
        final double[] c = {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
                -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
        final double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
                3.754408661907416e+00};
        final double pLow = 0.02425;
        double x;
        if (p < pLow) {
            double q = Math.sqrt(-2 * Math.log(p));
            x = (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        } else if (p <= 1 - pLow) {
            double q = p - 0.5;
            double r = q * q;
            x = (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
                    / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
        } else {
            double q = Math.sqrt(-2 * Math.log(1 - p));
            x = -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        return x;
    }

    // ---------------------------------------------------------------------
    // Linear algebra
    // ---------------------------------------------------------------------

    public static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    public static double[] multiply(double[][] m, double[] v) {
        double[] out = new double[m.length];
        for (int i = 0; i < m.length; i++) out[i] = dot(m[i], v);
        return out;
    }

    /** w' M w */
    public static double quadraticForm(double[] w, double[][] m) {
        return dot(w, multiply(m, w));
    }

    public static double[][] scale(double[][] m, double factor) {
        double[][] out = new double[m.length][];
        for (int i = 0; i < m.length; i++) {
            out[i] = new double[m[i].length];
            for (int j = 0; j < m[i].length; j++) out[i][j] = m[i][j] * factor;
        }
        return out;
    }

    public static double[] scale(double[] v, double factor) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] * factor;
        return out;
    }

    /**
     * Cholesky factor L (lower triangular, M = L L'). A covariance matrix that is only
     * positive semi-definite (e.g. a stablecoin with near-zero variance) gets the smallest
     * diagonal loading needed to factor it.
     */
    public static double[][] cholesky(double[][] m) {
        int n = m.length;
        double trace = 0.0;
        for (int i = 0; i < n; i++) trace += m[i][i];
        double jitter = 0.0;
        double jitterStep = Math.max(trace / n, Double.MIN_NORMAL) * 1e-12;
        while (true) {
            double[][] l = tryCholesky(m, jitter);
            if (l != null) return l;
            jitter = jitter == 0.0 ? jitterStep : jitter * 10;
            if (jitter > trace) {
                throw new IllegalArgumentException("Covariance matrix is not positive semi-definite");
            }
        }
    }

    private static double[][] tryCholesky(double[][] m, double jitter) {
        int n = m.length;
        double[][] l = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = m[i][j] + (i == j ? jitter : 0.0);
                for (int k = 0; k < j; k++) sum -= l[i][k] * l[j][k];
                if (i == j) {
                    if (sum <= 0.0) return null;
                    l[i][i] = Math.sqrt(sum);
                } else {
                    l[i][j] = sum / l[j][j];
                }
            }
        }
        return l;
    }

    /** Inverse of a square matrix by Gauss-Jordan elimination with partial pivoting. */
    public static double[][] invert(double[][] m) {
        int n = m.length;
        double[][] a = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(m[i], 0, a[i], 0, n);
            a[i][n + i] = 1.0;
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            }
            if (Math.abs(a[pivot][col]) < 1e-300) {
                throw new IllegalArgumentException("Matrix is singular");
            }
            double[] tmp = a[col];
            a[col] = a[pivot];
            a[pivot] = tmp;
            double div = a[col][col];
            for (int j = 0; j < 2 * n; j++) a[col][j] /= div;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = a[r][col];
                if (f == 0.0) continue;
                for (int j = 0; j < 2 * n; j++) a[r][j] -= f * a[col][j];
            }
        }
        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(a[i], n, inv[i], 0, n);
        return inv;
    }

    /** Largest eigenvalue of a symmetric PSD matrix (power iteration). */
    static double spectralNorm(double[][] m) {
        int n = m.length;
        double[] v = new double[n];
        Arrays.fill(v, 1.0 / Math.sqrt(n));
        double lambda = 0.0;
        for (int iter = 0; iter < 1000; iter++) {
            double[] mv = multiply(m, v);
            double norm = Math.sqrt(dot(mv, mv));
            if (norm == 0.0) return 0.0;
            for (int i = 0; i < n; i++) v[i] = mv[i] / norm;
            if (Math.abs(norm - lambda) <= 1e-14 * norm) return norm;
            lambda = norm;
        }
        return lambda;
    }

    // ---------------------------------------------------------------------
    // Long-only, fully invested mean-variance optimization
    // ---------------------------------------------------------------------

    /** Euclidean projection onto the probability simplex {w >= 0, sum w = 1} (Duchi et al., 2008). */
    public static double[] projectOntoSimplex(double[] v) {
        int n = v.length;
        double[] u = v.clone();
        Arrays.sort(u);
        double cumulative = 0.0;
        double theta = 0.0;
        for (int i = n - 1; i >= 0; i--) {
            cumulative += u[i];
            double t = (cumulative - 1.0) / (n - i);
            if (i == 0 || u[i - 1] <= t) {
                theta = t;
                break;
            }
        }
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = Math.max(v[i] - theta, 0.0);
        return w;
    }

    /**
     * Markowitz utility maximisation: argmax_w  mu'w - (riskAversion / 2) w' Sigma w
     * subject to w >= 0 and sum(w) = 1, solved with accelerated projected gradient (FISTA).
     */
    public static double[] maximizeUtility(double[] mu, double[][] sigma, double riskAversion) {
        int n = mu.length;
        double lipschitz = riskAversion * spectralNorm(sigma);
        if (lipschitz <= 0.0) {
            // No risk penalty: the optimum is the highest expected return asset
            return unitVector(n, argMax(mu));
        }
        double step = 1.0 / lipschitz;
        double[] w = new double[n];
        Arrays.fill(w, 1.0 / n);
        double[] y = w.clone();
        double t = 1.0;
        for (int iter = 0; iter < MAX_SOLVER_ITERATIONS; iter++) {
            double[] sy = multiply(sigma, y);
            double[] next = new double[n];
            for (int i = 0; i < n; i++) next[i] = y[i] + step * (mu[i] - riskAversion * sy[i]);
            next = projectOntoSimplex(next);
            double tNext = (1 + Math.sqrt(1 + 4 * t * t)) / 2;
            double change = 0.0;
            for (int i = 0; i < n; i++) {
                y[i] = next[i] + ((t - 1) / tNext) * (next[i] - w[i]);
                change += Math.abs(next[i] - w[i]);
            }
            w = next;
            t = tNext;
            if (change < SOLVER_TOLERANCE) break;
        }
        return w;
    }

    /** Global minimum-variance long-only portfolio. */
    public static double[] minimumVariance(double[][] sigma) {
        // Utility maximisation with mu = 0 and any positive risk aversion minimises variance
        return maximizeUtility(new double[sigma.length], sigma, 1.0);
    }

    /**
     * Point on the long-only efficient frontier selected by a risk tolerance in [0, 1]:
     * 0 is the minimum-variance portfolio, 1 the maximum expected return portfolio, and values
     * in between target a volatility interpolated linearly between those two endpoints.
     */
    public static double[] efficientFrontierPortfolio(double[] mu, double[][] sigma, double riskTolerance) {
        double[] wMin = minimumVariance(sigma);
        double[] wMax = maximumReturnPortfolio(mu, sigma);
        double volMin = Math.sqrt(quadraticForm(wMin, sigma));
        double volMax = Math.sqrt(quadraticForm(wMax, sigma));
        if (riskTolerance <= 0.0 || volMax <= volMin) return wMin;
        if (riskTolerance >= 1.0) return wMax;

        double targetVol = volMin + riskTolerance * (volMax - volMin);
        // Volatility of the utility-maximising portfolio falls monotonically as risk aversion rises,
        // so bisect on log(risk aversion) for the portfolio whose volatility hits the target.
        double scale = Math.max(spectralNorm(sigma), Double.MIN_NORMAL);
        double maxAbsMu = 0.0;
        for (double m : mu) maxAbsMu = Math.max(maxAbsMu, Math.abs(m));
        double base = Math.max(maxAbsMu, Double.MIN_NORMAL) / scale;
        double logLo = Math.log(base) - 30; // aggressive end
        double logHi = Math.log(base) + 30; // conservative end
        double[] best = wMin;
        for (int i = 0; i < BISECTION_ITERATIONS; i++) {
            double mid = (logLo + logHi) / 2;
            double[] w = maximizeUtility(mu, sigma, Math.exp(mid));
            double vol = Math.sqrt(quadraticForm(w, sigma));
            best = w;
            if (Math.abs(vol - targetVol) <= 1e-9 * Math.max(targetVol, 1e-12)) break;
            if (vol > targetVol) logLo = mid; else logHi = mid;
            if (logHi - logLo < 1e-10) break;
        }
        return best;
    }

    /**
     * Highest expected return portfolio. Ties between assets with the same expected return are
     * split by solving the minimum-variance problem among them.
     */
    private static double[] maximumReturnPortfolio(double[] mu, double[][] sigma) {
        int n = mu.length;
        double top = mu[argMax(mu)];
        int[] tied = java.util.stream.IntStream.range(0, n)
                .filter(i -> Math.abs(mu[i] - top) <= 1e-15 * Math.max(1.0, Math.abs(top)))
                .toArray();
        double[] w = new double[n];
        if (tied.length == 1) {
            w[tied[0]] = 1.0;
            return w;
        }
        double[][] sub = new double[tied.length][tied.length];
        for (int i = 0; i < tied.length; i++) {
            for (int j = 0; j < tied.length; j++) sub[i][j] = sigma[tied[i]][tied[j]];
        }
        double[] subW = minimumVariance(sub);
        for (int i = 0; i < tied.length; i++) w[tied[i]] = subW[i];
        return w;
    }

    private static int argMax(double[] v) {
        int best = 0;
        for (int i = 1; i < v.length; i++) if (v[i] > v[best]) best = i;
        return best;
    }

    private static double[] unitVector(int n, int index) {
        double[] w = new double[n];
        w[index] = 1.0;
        return w;
    }
}
