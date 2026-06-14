package util;

/**
 * Shape-preserving piecewise cubic Hermite interpolation (PCHIP, the
 * Fritsch–Carlson / MATLAB flavour). Monotone between knots, no overshoot,
 * C1 continuous — ideal for animating rating curves through sparse knots.
 *
 * Evaluation outside the knot range clamps to the endpoint values.
 */
public final class Pchip {
    private final double[] xs;
    private final double[] ys;
    private final double[] slopes;

    public Pchip(double[] xs, double[] ys) {
        if (xs == null || ys == null || xs.length != ys.length) {
            throw new IllegalArgumentException("xs and ys must be non-null and the same length");
        }
        if (xs.length < 2) {
            throw new IllegalArgumentException("Pchip needs at least two knots");
        }
        for (int i = 1; i < xs.length; i++) {
            if (xs[i] <= xs[i - 1]) {
                throw new IllegalArgumentException("xs must be strictly increasing");
            }
        }
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.slopes = computeSlopes(this.xs, this.ys);
    }

    public double firstX() {
        return xs[0];
    }

    public double lastX() {
        return xs[xs.length - 1];
    }

    public double value(double x) {
        if (x <= xs[0]) {
            return ys[0];
        }
        if (x >= xs[xs.length - 1]) {
            return ys[ys.length - 1];
        }

        int lo = 0;
        int hi = xs.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= x) {
                lo = mid;
            } else {
                hi = mid;
            }
        }

        double h = xs[lo + 1] - xs[lo];
        double t = (x - xs[lo]) / h;
        double t2 = t * t;
        double t3 = t2 * t;
        double h00 = 2 * t3 - 3 * t2 + 1;
        double h10 = t3 - 2 * t2 + t;
        double h01 = -2 * t3 + 3 * t2;
        double h11 = t3 - t2;
        return h00 * ys[lo]
                + h10 * h * slopes[lo]
                + h01 * ys[lo + 1]
                + h11 * h * slopes[lo + 1];
    }

    private static double[] computeSlopes(double[] xs, double[] ys) {
        int n = xs.length;
        double[] h = new double[n - 1];
        double[] delta = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            h[i] = xs[i + 1] - xs[i];
            delta[i] = (ys[i + 1] - ys[i]) / h[i];
        }

        double[] m = new double[n];
        if (n == 2) {
            m[0] = delta[0];
            m[1] = delta[0];
            return m;
        }

        // Interior: weighted harmonic mean of adjacent secants (zero across extrema).
        for (int i = 1; i < n - 1; i++) {
            if (delta[i - 1] * delta[i] <= 0) {
                m[i] = 0;
            } else {
                double w1 = 2 * h[i] + h[i - 1];
                double w2 = h[i] + 2 * h[i - 1];
                m[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i]);
            }
        }

        m[0] = endpointSlope(h[0], h[1], delta[0], delta[1]);
        m[n - 1] = endpointSlope(h[n - 2], h[n - 3], delta[n - 2], delta[n - 3]);
        return m;
    }

    // Shape-preserving three-point endpoint formula.
    private static double endpointSlope(double h0, double h1, double d0, double d1) {
        double slope = ((2 * h0 + h1) * d0 - h0 * d1) / (h0 + h1);
        if (slope * d0 <= 0) {
            return 0;
        }
        if (d0 * d1 < 0 && Math.abs(slope) > 3 * Math.abs(d0)) {
            return 3 * d0;
        }
        return slope;
    }
}
