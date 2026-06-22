package geom;

import storage.Color;

/**
 * A horizontal colour region of the plot spanning a data-space y-value range
 * {@code [lo, hi)}, painted behind the grid to show semantic strata such as
 * Codeforces rank tiers.
 *
 * <p>This is intentionally <em>not</em> related to the "band" tick-family
 * election inside {@link DataGrid} (which decides label cadence). A value band
 * is purely a coloured backdrop keyed to data values.</p>
 *
 * <ul>
 *   <li>{@link #fill()} is the (typically dark, desaturated) region tint.</li>
 *   <li>{@link #edge()} is the brighter line drawn at the band's lower
 *       threshold {@code lo} — the semantic boundary value.</li>
 *   <li>{@link #label()} is an optional tier name (e.g. {@code "Grandmaster"}).</li>
 * </ul>
 */
public final class ValueBand {
    private final double lo;
    private final double hi;
    private final Color fill;
    private final Color edge;
    private final String label;

    public ValueBand(double lo, double hi, Color fill, Color edge, String label) {
        this.lo = Math.min(lo, hi);
        this.hi = Math.max(lo, hi);
        this.fill = fill;
        this.edge = edge;
        this.label = label;
    }

    public double lo() {
        return lo;
    }

    public double hi() {
        return hi;
    }

    public Color fill() {
        return fill;
    }

    public Color edge() {
        return edge;
    }

    public String label() {
        return label;
    }
}
