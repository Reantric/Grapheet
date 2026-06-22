package geom;

import core.Applet;
import processing.core.PFont;
import storage.Color;
import storage.ColorType;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.DoubleFunction;

/**
 * First-quadrant data chart renderer with adaptive, fade-in/fade-out tick
 * families on both axes.
 *
 * <p>Tick model: candidate steps come from the nice-step ladder
 * {@code ... 1, 2, 5, 10, 20, 50 ...} (or calendar boundaries — month,
 * quarter, year, 2/5/10 years — when the x axis is in calendar mode). The
 * label band is elected from physical density: screen-space spacing divided
 * by measured label clearance for the actual font and visible strings.
 *
 * <p>Families on the ladder are not mutually nested (500 is not a multiple of
 * 200), so families are NOT rendered independently. Instead all visible
 * families are merged into one tick list; a tick's alpha accumulates over the
 * families that contain it, and its visual style (size, brightness, stroke)
 * derives continuously from that alpha. This is what makes zoom transitions
 * read as a clean crossfade instead of duplicated/cramped label bands.
 */
public final class DataGrid {
    private static final double EPSILON = 1e-6;
    private static final float LABEL_BAND_GAP = 14f;
    private static final float SLIM_RAIL_PADDING = 16f;
    /** Only labels at least this faded-in size the slim rail. Faint, transient
     *  edge labels (e.g. a negative minor tick dipping into view) must not widen
     *  the rail and shove the whole axis sideways mid-race. */
    private static final float SLIM_RAIL_LABEL_MIN_ALPHA = 0.6f;
    private static final double[] NICE_STEP_MULTIPLIERS = {1.0, 2.0, 5.0};

    private static final float ALPHA_EPSILON = 0.02f;
    private static final float BOTTOM_AXIS_FALL_FADE_PX = 36f;
    private static final float Y_AXIS_EXIT_FADE_PX = 48f;
    // Label crossfades are TIME-based and exactly ONE family per axis is
    // the live "label band": density only elects the band, and each family's
    // rendered alpha eases toward in-band/not at a fixed rate. Driving
    // rendered alpha straight off pixel spacing left labels stuck at mid-grey
    // for tens of seconds when the camera crawled through a crossfade, and
    // any rule that allowed two strong NON-NESTED families at once (5-year vs
    // two-year ticks) overprinted label text.
    private static final float LABEL_FADE_IN_RATE = 4.5f;
    /** X fade-out stays fast: during a zoom the dying calendar labels keep
     *  compressing toward their neighbours, so the ghost-overlap window
     *  must be short. The y axis has no such pressure and reads better
     *  with a gentler exit. */
    private static final float X_LABEL_FADE_OUT_RATE = 5.5f;
    private static final float Y_LABEL_FADE_OUT_RATE = 3.2f;
    /** Multiplicative breathing room around measured y-label ink. Tuned to
     *  preserve the approved PR8 coarse horizontal-grid cadence while keeping
     *  the density model tied to actual text metrics. */
    private static final float Y_LABEL_GAP_COMFORT = 4.0f;
    /** Calendar/numeric x labels need their own horizontal cadence; sharing
     *  the y comfort makes the date axis too sparse. */
    private static final float X_LABEL_GAP_COMFORT = 1.35f;
    /** Extra clearance a finer challenger needs before it takes the band. */
    private static final float LABEL_DENSITY_HYSTERESIS = 0.12f;
    /** Shared left-edge exit window for x gridlines AND their labels (a
     *  fraction of the base gap), so both leave together and the leftmost
     *  date does not hang around half-faded for seconds. */
    private static final float X_LABEL_EXIT_FADE_FRACTION = 0.45f;
    /** Cap on the exit-fade ramp, as a fraction of the plot. The major step is
     *  fixed (a quarter in calendar mode), so at deep zoom the raw ramp can
     *  dwarf the plot and dim every label/gridline at once — this keeps it a
     *  local edge effect. Sits above the ~0.11 ramp at the normal 1-year
     *  window, so ordinary zooms are unaffected. */
    private static final float X_LABEL_EXIT_FADE_MAX_FRACTION = 0.12f;
    /** Horizontal gridlines and y labels overshoot the plot bottom by this
     *  much and dissolve, instead of popping out at the boundary. */
    private static final float BOTTOM_GRID_OVERSCAN_PX = 30f;

    // GRIDLINES follow the label band: the band family's lines are the
    // majors, and a nested finer family can appear as soft minors only when
    // its own spacing is readable as structure.
    private static final float MINOR_GRID_STRENGTH = 0.55f;
    private static final float MINOR_GRID_DENSITY_START = 0.75f;
    private static final float MINOR_GRID_DENSITY_FULL = 1.25f;
    // A nested finer family may show as soft minors only if it subdivides the
    // band into at most this many cells. Compared against the ROUNDED ratio so
    // inexact family ratios are judged by their true integer subdivision count
    // (e.g. YEAR/QUARTER = 4.0001 from averageDays, or 0.05/0.01 binary error).
    private static final int MAX_MINOR_SUBDIVISIONS = 4;

    private final Applet p;
    private final DecimalFormat numberFormat = new DecimalFormat("0.##");

    private PFont font;

    private float leftInset = 150f;
    private float topInset = 72f;
    private float rightInset = 88f;
    private float bottomInset = 110f;

    private float plotLeft;
    private float plotTop;
    private float plotWidth;
    private float plotHeight;
    private float viewportLeft;
    private float currentCollapseProgress;
    /** Eased slim-rail width: the raw width jumps when the widest visible
     *  y-label gains/loses a character (a negative or extra-digit tick entering
     *  the window), which bumped the whole label column — easing slides it. */
    private float easedSlimRailWidth = -1f;
    private static final float RAIL_EASE_RATE = 7f;
    /** Widest labels the y-axis will ever show across the whole animation,
     *  declared by the scene; the rail reserves their width so it never juts
     *  sideways when the live tick labels change width. */
    private String[] slimRailReserveLabels;

    private double xMin = 0;
    private double xMax = 24;
    private double yMin = 800;
    private double yMax = 1800;

    private double xAnchor = 0;
    private double yAnchor = 0;
    private double xMajorStep = 3;
    private double yMajorStep = 100;

    /** When non-null, the y axis shows exactly these values (sorted) instead of
     *  the adaptive 1/2/5 ladder — for fixed semantic ticks (e.g. rank
     *  thresholds) that do not follow a nice-number cadence. */
    private double[] yExplicitTicks;

    /** When non-null, x values are interpreted as days since this date. */
    private LocalDate xCalendarDayZero;

    private DoubleFunction<String> xLabelFormatter = this::formatDefaultLabel;
    private DoubleFunction<String> yLabelFormatter = this::formatDefaultLabel;

    private final Color axisColor = new Color(ColorType.WHITE);
    private final Color labelBackgroundColor = new Color(0, 0, 0, 92);

    private float axisStroke = 5f;
    private float majorGridStroke = 2.75f;
    private float minorGridStroke = 1.5f;
    /** Extra opacity for MINOR gridlines (0 = default). Scales by how minor a line
     *  is, so majors are untouched — lets a scene with busy coloured backdrops
     *  (the JToH tier bands) keep its minors legible without restyling every grid. */
    private float minorGridBoost = 0f;
    private float majorLabelSize = 34f;
    private float minorLabelSize = 27f;
    private float xLabelInset = 40f;
    private float yLabelInset = 18f;
    private float topGridOverscan = 112f;
    private float rightGridOverscan = 116f;

    private boolean showMinorGrid = true;
    private boolean showLabels = true;
    private boolean showAxisBackgroundStrips = true;
    /** Coloured value-range strata painted behind the grid (e.g. rank tiers). */
    private List<ValueBand> valueBands = List.of();
    private boolean showValueBandLabels = true;
    private float valueBandLabelSize = 22f;
    private boolean railCollapseRatchet;
    private float reachedCollapseProgress;

    /** Per-family label fade state: key -> {displayed alpha, target}. */
    private final Map<Object, float[]> xLabelFadeStates = new HashMap<>();
    private final Map<Object, float[]> yLabelFadeStates = new HashMap<>();
    /** Per-family gridline fade state, driven by the same band election. */
    private final Map<Object, float[]> xGridFadeStates = new HashMap<>();
    private final Map<Object, float[]> yGridFadeStates = new HashMap<>();
    private Object xLabelBandKey;
    private Object yLabelBandKey;
    /** Old band held at full alpha during a nested refinement (see
     *  applyBandTransition) until the finer band has fully faded in. */
    private Object xLingeringBandKey;
    private Object yLingeringBandKey;
    private float labelFadeDt = 1f / 60f;

    /** Tick-subset relation between the outgoing and incoming band. */
    private enum Nesting { NEW_WITHIN_OLD, OLD_WITHIN_NEW, NONE }

    public DataGrid(Applet window) {
        this.p = window;
    }

    public void render() {
        // Plot top/height never depend on tick contents; rail width does
        // (via y-label widths), so resolve geometry in two phases.
        viewportLeft = -p.width / 2f;
        plotTop = -p.height / 2f + topInset;
        plotHeight = p.height - topInset - bottomInset;
        if (plotHeight <= 0 || xMax <= xMin || yMax <= yMin) {
            return;
        }

        List<Tick> yTicks = yExplicitTicks != null
                ? buildExplicitTicks(yExplicitTicks, yLabelFormatter)
                : buildNumericTicks(yMin, yMax, yAnchor, yMajorStep, plotHeight,
                        topGridOverscan, yLabelFormatter, yLabelFadeStates, yGridFadeStates);

        currentCollapseProgress = railCollapseProgress();
        float targetSlimRail = slimRailWidth(yTicks);
        easedSlimRailWidth = easedSlimRailWidth < 0f
                ? targetSlimRail
                : easedSlimRailWidth + (targetSlimRail - easedSlimRailWidth)
                        * (1f - (float) Math.exp(-RAIL_EASE_RATE * labelFadeDt));
        float currentLeftRailWidth = interpolate(leftInset, easedSlimRailWidth, currentCollapseProgress);
        plotLeft = viewportLeft + currentLeftRailWidth;
        plotWidth = p.width - currentLeftRailWidth - rightInset;
        if (plotWidth <= 0) {
            return;
        }

        List<Tick> xTicks = xCalendarDayZero != null
                ? buildCalendarTicks()
                : buildNumericTicks(xMin, xMax, xAnchor, xMajorStep, plotWidth,
                        rightGridOverscan, xLabelFormatter, xLabelFadeStates, xGridFadeStates);

        // Value bands are the quietest layer: behind gridlines, axes and data.
        drawValueBands();
        drawVerticalGrid(xTicks);
        drawHorizontalGrid(yTicks);
        // The bottom band sits under the axes, but the left rail band draws
        // OVER them: it is the cover mask that hides the world-space y-axis
        // line as the axis exits into the rail.
        if (showAxisBackgroundStrips) {
            drawBottomLabelBand();
        }
        drawAxes();
        if (showAxisBackgroundStrips) {
            drawLeftLabelBand();
        }
        if (showLabels) {
            ensureFont();
            p.textFont(font);
            p.noStroke();
            drawYLabels(yTicks);
            drawXLabels(xTicks);
        }
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    public void setDomain(double xMin, double xMax, double yMin, double yMax) {
        setXRange(xMin, xMax);
        setYRange(yMin, yMax);
    }

    public void setXRange(double xMin, double xMax) {
        if (xMax <= xMin) {
            throw new IllegalArgumentException("xMax must be greater than xMin");
        }
        this.xMin = xMin;
        this.xMax = xMax;
    }

    public void setYRange(double yMin, double yMax) {
        if (yMax <= yMin) {
            throw new IllegalArgumentException("yMax must be greater than yMin");
        }
        this.yMin = yMin;
        this.yMax = yMax;
    }

    public void setAnchor(double xAnchor, double yAnchor) {
        this.xAnchor = xAnchor;
        this.yAnchor = yAnchor;
    }

    public void setXMajorStep(double xMajorStep) {
        this.xMajorStep = requirePositive(xMajorStep, "xMajorStep");
    }

    public void setYMajorStep(double yMajorStep) {
        this.yMajorStep = requirePositive(yMajorStep, "yMajorStep");
    }

    /**
     * Pins the y axis to a fixed set of tick values (labelled by the y label
     * formatter) instead of the adaptive ladder. Each becomes a full-strength
     * major tick; edge fades as the window pans still apply. Pass {@code null}
     * to restore the automatic ladder.
     */
    public void setYExplicitTicks(double[] yExplicitTicks) {
        this.yExplicitTicks = yExplicitTicks == null ? null : yExplicitTicks.clone();
    }

    /**
     * Switch the x axis to calendar mode: x values become days since
     * {@code dayZero}, gridlines/labels sit on day, week (1/8/15/22), month,
     * quarter (Jan 1, Apr 1, Jul 1, Oct 1) and year boundaries, and fade
     * between granularities with zoom. The sub-month levels only surface when
     * the window is zoomed in far (e.g. the JToH burst dive-in). Also sets the
     * x major step to an average quarter so the moving
     * left-rail math keeps working.
     */
    public void setXCalendarAxis(LocalDate dayZero) {
        this.xCalendarDayZero = Objects.requireNonNull(dayZero, "dayZero");
        this.xMajorStep = 91.3125;
    }

    public void clearXCalendarAxis() {
        this.xCalendarDayZero = null;
    }

    /**
     * When enabled, the left-rail collapse only ever moves forward: once the
     * camera has followed past the y-axis, zooming back out does NOT bring
     * the wide rail and the world y-axis line back. Calling this (with either
     * value) also resets the ratchet state.
     */
    public void setRailCollapseRatchet(boolean railCollapseRatchet) {
        this.railCollapseRatchet = railCollapseRatchet;
        this.reachedCollapseProgress = 0f;
    }

    /** Overrides the default (Computer Modern) axis label font. */
    public void setLabelFont(PFont labelFont) {
        this.font = Objects.requireNonNull(labelFont, "labelFont");
    }

    /** Override the default axis label sizes (major = full-strength labels,
     *  minor = the faded next-family labels mid-crossfade). */
    public void setLabelSizes(float majorLabelSize, float minorLabelSize) {
        if (majorLabelSize <= 0f || minorLabelSize <= 0f) {
            throw new IllegalArgumentException("Label sizes must be positive");
        }
        this.majorLabelSize = majorLabelSize;
        this.minorLabelSize = minorLabelSize;
    }

    /**
     * Animation time step for the label crossfades, in seconds. Scenes on a
     * fixed-timestep clock should pass their dt every frame so fades stay
     * tied to the video timeline; without it a 60fps wall clock is assumed.
     */
    public void setLabelFadeTimeStep(double dt) {
        this.labelFadeDt = (float) Math.max(0.0, dt);
    }

    public void setPlotInsets(float leftInset, float topInset, float rightInset, float bottomInset) {
        if (leftInset < 0 || topInset < 0 || rightInset < 0 || bottomInset < 0) {
            throw new IllegalArgumentException("Plot insets must be non-negative");
        }
        this.leftInset = leftInset;
        this.topInset = topInset;
        this.rightInset = rightInset;
        this.bottomInset = bottomInset;
    }

    /** Gap from the plot edge to the axis labels (x = below the plot, y = left
     *  of it). Larger values push the numbers further out from the gridlines. */
    public void setLabelInsets(float xLabelInset, float yLabelInset) {
        if (xLabelInset < 0f || yLabelInset < 0f) {
            throw new IllegalArgumentException("Label insets must be non-negative");
        }
        this.xLabelInset = xLabelInset;
        this.yLabelInset = yLabelInset;
    }

    public void setXLabelFormatter(DoubleFunction<String> formatter) {
        this.xLabelFormatter = Objects.requireNonNull(formatter, "formatter");
    }

    public void setYLabelFormatter(DoubleFunction<String> formatter) {
        this.yLabelFormatter = Objects.requireNonNull(formatter, "formatter");
    }

    /**
     * Reserve the y-axis label rail for the widest labels the axis will ever
     * show, so the plot's left edge holds still instead of jutting sideways
     * when a live label gains a digit ("8" -> "10") or a line dips to a negative
     * tick. Pass the worst-case labels (e.g. the formatted global min and max).
     */
    public void setYLabelReserve(String... labels) {
        this.slimRailReserveLabels = labels;
    }

    public void showMinorGrid(boolean showMinorGrid) {
        this.showMinorGrid = showMinorGrid;
    }

    /** Extra minor-gridline opacity (0 = default). Useful when busy coloured
     *  backdrops wash the minors out; majors are left as designed. */
    public void setMinorGridBoost(float minorGridBoost) {
        this.minorGridBoost = Math.max(0f, minorGridBoost);
    }

    public void showLabels(boolean showLabels) {
        this.showLabels = showLabels;
    }

    public void showAxisBackgroundStrips(boolean showAxisBackgroundStrips) {
        this.showAxisBackgroundStrips = showAxisBackgroundStrips;
    }

    /**
     * Sets the coloured value-range strata drawn behind the grid. Bands are
     * keyed to data-space y values, so they ride the y-axis as it pans/zooms.
     * Pass an empty list (or {@code null}) to clear.
     */
    public void setValueBands(List<ValueBand> bands) {
        this.valueBands = bands == null ? List.of() : List.copyOf(bands);
    }

    public void showValueBandLabels(boolean showValueBandLabels) {
        this.showValueBandLabels = showValueBandLabels;
    }

    public void setValueBandLabelSize(float valueBandLabelSize) {
        this.valueBandLabelSize = valueBandLabelSize;
    }

    // ------------------------------------------------------------------
    // Geometry helpers for scene code
    // ------------------------------------------------------------------

    public float domainToCanvasX(double value) {
        double t = (value - xMin) / (xMax - xMin);
        return plotLeft + (float) (t * plotWidth);
    }

    public float domainToCanvasY(double value) {
        double t = (value - yMin) / (yMax - yMin);
        return plotTop + plotHeight - (float) (t * plotHeight);
    }

    public float getPlotLeft() {
        return plotLeft;
    }

    public float getPlotTop() {
        return plotTop;
    }

    public float getPlotWidth() {
        return plotWidth;
    }

    public float getPlotHeight() {
        return plotHeight;
    }

    public double getXMin() {
        return xMin;
    }

    public double getXMax() {
        return xMax;
    }

    public double getYMin() {
        return yMin;
    }

    public double getYMax() {
        return yMax;
    }

    // ------------------------------------------------------------------
    // Tick construction
    // ------------------------------------------------------------------

    /** Fixed major-tick list from explicit values (see {@link #setYExplicitTicks}). */
    private List<Tick> buildExplicitTicks(double[] values, DoubleFunction<String> formatter) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        List<Tick> ticks = new ArrayList<>(sorted.length);
        for (double value : sorted) {
            Tick tick = new Tick(value, formatter.apply(value));
            tick.gridAlpha = 1f;
            tick.labelAlpha = 1f;
            ticks.add(tick);
        }
        return ticks;
    }

    private List<Tick> buildNumericTicks(
            double min,
            double max,
            double anchor,
            double baseStep,
            float pixelSpan,
            float overscanPx,
            DoubleFunction<String> formatter,
            Map<Object, float[]> labelFadeStates,
            Map<Object, float[]> gridFadeStates
    ) {
        double span = max - min;
        if (baseStep <= EPSILON || span <= EPSILON || pixelSpan <= 0f) {
            return new ArrayList<>();
        }

        double low = Math.min(min, anchor);
        double high = max + span * (overscanPx / pixelSpan);

        int firstIndex = niceStepFloorIndex(baseStep, span * 8.0 / pixelSpan) - 1;
        int lastIndex = niceStepFloorIndex(baseStep, span * 1500.0 / pixelSpan) + 2;
        boolean isYAxis = labelFadeStates == yLabelFadeStates;

        ensureFont();
        p.textFont(font);
        p.textSize(majorLabelSize);
        float yLabelClearancePx = labelClearancePx(yLabelExtentPx(), Y_LABEL_GAP_COMFORT);

        int count = lastIndex - firstIndex + 1;
        double[] steps = new double[count];
        float[] labelDensities = new float[count];
        Object[] fadeKeys = new Object[count];
        for (int i = 0; i < count; i++) {
            int index = firstIndex + i;
            fadeKeys[i] = index;
            double step = niceStep(baseStep, index);
            steps[i] = step;
            if (step <= EPSILON) {
                continue;
            }
            float spacingPx = (float) (pixelSpan * (step / span));
            float labelClearancePx = isYAxis
                    ? yLabelClearancePx
                    : labelClearancePx(maxNumericLabelWidth(low, high, anchor, step, formatter),
                            X_LABEL_GAP_COMFORT);
            labelDensities[i] = spacingPx / labelClearancePx;
        }

        Object previousBand = isYAxis ? yLabelBandKey : xLabelBandKey;
        Object lingering = isYAxis ? yLingeringBandKey : xLingeringBandKey;
        Object band = selectLabelBand(previousBand, fadeKeys, labelDensities);
        if (previousBand != null && !band.equals(previousBand)) {
            Nesting nesting = previousBand instanceof Integer oldIndex && band instanceof Integer newIndex
                    ? numericNesting(baseStep, oldIndex, newIndex)
                    : Nesting.NONE;
            lingering = applyBandTransition(
                    labelFadeStates, gridFadeStates, previousBand, band, lingering, nesting);
        }

        // Gridlines follow the band: band lines are the majors, and the nearest
        // finer family whose step divides the band can fade in as soft minors
        // only if its own spacing is readable.
        int bandIndex = (Integer) band;
        double bandStep = niceStep(baseStep, bandIndex);
        int minorIndex = numericMinorIndex(baseStep, bandIndex);
        float minorTarget = 0f;
        if (minorIndex >= firstIndex && minorIndex <= lastIndex) {
            double minorStep = niceStep(baseStep, minorIndex);
            float subdivisions = (float) (bandStep / minorStep);
            if (Math.round(subdivisions) <= MAX_MINOR_SUBDIVISIONS) {
                float minorSpacingPx = (float) (pixelSpan * (minorStep / span));
                float minorClearancePx = isYAxis
                        ? yLabelClearancePx
                        : labelClearancePx(maxNumericLabelWidth(low, high, anchor, minorStep, formatter),
                                X_LABEL_GAP_COMFORT);
                float minorDensity = minorSpacingPx / minorClearancePx;
                minorTarget = MINOR_GRID_STRENGTH * densityRamp(
                        minorDensity, MINOR_GRID_DENSITY_START, MINOR_GRID_DENSITY_FULL);
            }
        }

        float outRate = isYAxis ? Y_LABEL_FADE_OUT_RATE : X_LABEL_FADE_OUT_RATE;
        float[] labelAlphas = new float[count];
        float[] gridAlphas = new float[count];
        for (int i = 0; i < count; i++) {
            int index = firstIndex + i;
            boolean on = fadeKeys[i].equals(band) || fadeKeys[i].equals(lingering);
            labelAlphas[i] = fadedAlpha(labelFadeStates, fadeKeys[i], on ? 1f : 0f, outRate);
            float gridTarget = on ? 1f : (index == minorIndex ? minorTarget : 0f);
            gridAlphas[i] = fadedAlpha(gridFadeStates, fadeKeys[i], gridTarget, outRate);
        }
        lingering = releaseLingerWhenCovered(labelFadeStates, gridFadeStates, band, lingering);
        if (isYAxis) {
            yLabelBandKey = band;
            yLingeringBandKey = lingering;
        } else {
            xLabelBandKey = band;
            xLingeringBandKey = lingering;
        }

        List<double[]> families = new ArrayList<>(); // {step, gridAlpha, labelAlpha}
        for (int i = 0; i < count; i++) {
            if (steps[i] > EPSILON && Math.max(gridAlphas[i], labelAlphas[i]) > ALPHA_EPSILON) {
                families.add(new double[]{steps[i], gridAlphas[i], labelAlphas[i]});
            }
        }
        if (families.isEmpty()) {
            return new ArrayList<>();
        }

        double finestStep = Double.MAX_VALUE;
        for (double[] family : families) {
            finestStep = Math.min(finestStep, family[0]);
        }
        // Every ladder step above the finest visible one is an integer
        // multiple of (finestStep / 2), so this key merges coincident ticks
        // exactly, with no floating-point near-miss duplicates.
        double keyUnit = finestStep / 2.0;

        TreeMap<Long, Tick> merged = new TreeMap<>();
        for (double[] family : families) {
            double step = family[0];
            float gridAlpha = (float) family[1];
            float labelAlpha = (float) family[2];
            double first = firstLineAtOrAfter(low, anchor, step);
            for (double value = first; value <= high + EPSILON; value += step) {
                long key = Math.round((value - anchor) / keyUnit);
                double snapped = anchor + key * keyUnit;
                Tick tick = merged.get(key);
                if (tick == null) {
                    tick = new Tick(snapped, formatter.apply(cleanZero(snapped)));
                    merged.put(key, tick);
                }
                // Capped SUM, not max: during a non-nested crossfade (0.5 ->
                // 0.2) a tick shared by both families (2.00) must hold full
                // alpha — under max it dipped to ~0.5 mid-fade and blinked.
                tick.gridAlpha = Math.min(1f, tick.gridAlpha + gridAlpha);
                tick.labelAlpha = Math.min(1f, tick.labelAlpha + labelAlpha);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<Tick> buildCalendarTicks() {
        double span = xMax - xMin;
        if (span <= EPSILON || plotWidth <= 0f) {
            return new ArrayList<>();
        }

        double low = Math.min(xMin, xAnchor);
        double high = xMax + span * (rightGridOverscan / plotWidth);

        ensureFont();
        p.textFont(font);
        p.textSize(majorLabelSize);
        CalendarFamily[] families = CalendarFamily.values();
        float[] labelDensities = new float[families.length];
        for (int i = 0; i < families.length; i++) {
            float spacingPx = (float) (plotWidth * (families[i].averageDays / span));
            labelDensities[i] = spacingPx / labelClearancePx(
                    maxCalendarLabelWidth(low, high, families[i]), X_LABEL_GAP_COMFORT);
        }
        Object previousBand = xLabelBandKey;
        Object band = selectLabelBand(previousBand, families, labelDensities);
        if (previousBand != null && !band.equals(previousBand)) {
            Nesting nesting = previousBand instanceof CalendarFamily oldFamily
                    && band instanceof CalendarFamily newFamily
                    ? calendarNesting(oldFamily, newFamily)
                    : Nesting.NONE;
            xLingeringBandKey = applyBandTransition(xLabelFadeStates, xGridFadeStates,
                    previousBand, band, xLingeringBandKey, nesting);
        }
        xLabelBandKey = band;

        // Gridlines follow the band (majors = band, soft minors = the
        // nearest finer family that nests into the band and has enough
        // spacing to read as structure; the rest fade out).
        CalendarFamily bandFamily = (CalendarFamily) band;
        CalendarFamily minorFamily = null;
        float minorTarget = 0f;
        for (int i = bandFamily.ordinal() - 1; i >= 0; i--) {
            if (calendarNesting(bandFamily, families[i]) == Nesting.OLD_WITHIN_NEW) {
                minorFamily = families[i];
                float subdivisions = (float) (bandFamily.averageDays / minorFamily.averageDays);
                if (Math.round(subdivisions) <= MAX_MINOR_SUBDIVISIONS) {
                    float minorSpacingPx = (float) (plotWidth * (minorFamily.averageDays / span));
                    float minorDensity = minorSpacingPx
                            / labelClearancePx(maxCalendarLabelWidth(low, high, minorFamily),
                                    X_LABEL_GAP_COMFORT);
                    minorTarget = MINOR_GRID_STRENGTH * densityRamp(
                            minorDensity, MINOR_GRID_DENSITY_START, MINOR_GRID_DENSITY_FULL);
                }
                break;
            }
        }

        float[] labelAlphas = new float[families.length];
        float[] gridAlphas = new float[families.length];
        for (int i = 0; i < families.length; i++) {
            boolean on = families[i].equals(band) || families[i].equals(xLingeringBandKey);
            labelAlphas[i] = fadedAlpha(xLabelFadeStates, families[i], on ? 1f : 0f,
                    X_LABEL_FADE_OUT_RATE);
            float gridTarget = on ? 1f : (families[i] == minorFamily ? minorTarget : 0f);
            gridAlphas[i] = fadedAlpha(xGridFadeStates, families[i], gridTarget,
                    X_LABEL_FADE_OUT_RATE);
        }
        xLingeringBandKey = releaseLingerWhenCovered(
                xLabelFadeStates, xGridFadeStates, band, xLingeringBandKey);

        TreeMap<Long, Tick> merged = new TreeMap<>();
        for (int i = 0; i < families.length; i++) {
            CalendarFamily family = families[i];
            float gridAlpha = gridAlphas[i];
            float labelAlpha = labelAlphas[i];
            if (Math.max(gridAlpha, labelAlpha) <= ALPHA_EPSILON) {
                continue;
            }

            LocalDate date = family.firstBoundaryOnOrAfter(
                    xCalendarDayZero.plusDays((long) Math.floor(low)));
            while (true) {
                long day = ChronoUnit.DAYS.between(xCalendarDayZero, date);
                if (day > high + EPSILON) {
                    break;
                }
                Tick tick = merged.get(day);
                if (tick == null) {
                    tick = new Tick(day, calendarLabel(date));
                    merged.put(day, tick);
                }
                // Capped SUM, not max — see the numeric merge: shared ticks
                // must hold full alpha through non-nested crossfades.
                tick.gridAlpha = Math.min(1f, tick.gridAlpha + gridAlpha);
                tick.labelAlpha = Math.min(1f, tick.labelAlpha + labelAlpha);
                date = family.next(date);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * Elect the single live label-band family for an axis from physical label
     * density: the finest family whose spacing clears the measured label
     * extent. The incumbent stays while still readable, and a finer challenger
     * needs a little extra room before taking over.
     */
    private Object selectLabelBand(Object incumbentKey, Object[] keys, float[] densities) {
        int desired = keys.length - 1;
        int incumbent = -1;
        boolean foundReadable = false;
        for (int i = 0; i < keys.length; i++) {
            if (!foundReadable && densities[i] >= 1f) {
                desired = i;
                foundReadable = true;
            }
            if (keys[i].equals(incumbentKey)) {
                incumbent = i;
            }
        }
        if (incumbent >= 0) {
            if (incumbent <= desired && densities[incumbent] >= 1f) {
                return keys[incumbent];
            }
            if (desired < incumbent
                    && densities[desired] < 1f + LABEL_DENSITY_HYSTERESIS) {
                return keys[incumbent];
            }
        }
        return keys[desired];
    }

    /**
     * Make nested band hand-offs seamless: ticks shared by the outgoing and
     * incoming band must never blink. Coarsening (new band's ticks are a
     * subset of the old's — 1 to 2, 5 to 10, quarter to year): the incoming
     * band snaps to the outgoing band's current alpha, so its ticks simply
     * continue while the old band's extra ticks fade out. Refinement (old
     * within new): the old band LINGERS at full until the finer band has
     * fully faded in, then drops invisibly. Non-nested switches (2 to 5,
     * two-year to five-year) get the ordinary simultaneous crossfade —
     * there is no shared structure to preserve. Returns the lingering key.
     */
    private Object applyBandTransition(Map<Object, float[]> labelStates,
                                       Map<Object, float[]> gridStates, Object previousBand,
                                       Object band, Object lingering, Nesting nesting) {
        if (lingering != null && !lingering.equals(band)) {
            snapLabelState(labelStates, lingering, 0f);
            snapLabelState(gridStates, lingering, 0f);
        }
        if (nesting == Nesting.NEW_WITHIN_OLD) {
            snapLabelState(labelStates, band, currentLabelAlpha(labelStates, previousBand));
            snapLabelState(gridStates, band, currentLabelAlpha(gridStates, previousBand));
            return null;
        }
        if (nesting == Nesting.OLD_WITHIN_NEW) {
            return previousBand;
        }
        return null;
    }

    private Object releaseLingerWhenCovered(Map<Object, float[]> labelStates,
                                            Map<Object, float[]> gridStates,
                                            Object band, Object lingering) {
        if (lingering != null && currentLabelAlpha(labelStates, band) >= 1f - ALPHA_EPSILON) {
            snapLabelState(labelStates, lingering, 0f);
            snapLabelState(gridStates, lingering, 0f);
            return null;
        }
        return lingering;
    }

    private void snapLabelState(Map<Object, float[]> states, Object key, float alpha) {
        float[] state = states.get(key);
        if (state == null) {
            states.put(key, new float[]{alpha, alpha});
        } else {
            state[0] = alpha;
            state[1] = alpha;
        }
    }

    private float currentLabelAlpha(Map<Object, float[]> states, Object key) {
        float[] state = states.get(key);
        return state == null ? 0f : state[0];
    }

    private Nesting numericNesting(double baseStep, int oldIndex, int newIndex) {
        double oldStep = niceStep(baseStep, oldIndex);
        double newStep = niceStep(baseStep, newIndex);
        if (isIntegerMultiple(newStep, oldStep)) {
            return Nesting.NEW_WITHIN_OLD;
        }
        if (isIntegerMultiple(oldStep, newStep)) {
            return Nesting.OLD_WITHIN_NEW;
        }
        return Nesting.NONE;
    }

    /** Every calendar family pair nests except two-year vs five-year. */
    private Nesting calendarNesting(CalendarFamily oldFamily, CalendarFamily newFamily) {
        boolean newCoarser = newFamily.averageDays > oldFamily.averageDays;
        CalendarFamily coarse = newCoarser ? newFamily : oldFamily;
        CalendarFamily fine = newCoarser ? oldFamily : newFamily;
        if (coarse == CalendarFamily.FIVE_YEARS && fine == CalendarFamily.TWO_YEARS) {
            return Nesting.NONE;
        }
        return newCoarser ? Nesting.NEW_WITHIN_OLD : Nesting.OLD_WITHIN_NEW;
    }

    private boolean isIntegerMultiple(double larger, double smaller) {
        if (larger <= smaller || smaller <= EPSILON) {
            return false;
        }
        double ratio = larger / smaller;
        return Math.abs(ratio - Math.round(ratio)) < 1e-6;
    }

    /**
     * Time-based label crossfade: the rendered alpha eases toward the
     * binary in-band target at a fixed rate — so a fade, once started,
     * completes in well under a second even when the camera crawls through
     * a crossfade boundary. (Driving rendered alpha straight off pixel
     * spacing left labels stuck at mid-grey for tens of seconds.)
     */
    private float fadedAlpha(Map<Object, float[]> states, Object key, float target, float outRate) {
        float[] state = states.get(key);
        if (state == null) {
            state = new float[]{target, target};
            states.put(key, state);
        }
        state[1] = target;
        float rate = target >= state[0] ? LABEL_FADE_IN_RATE : outRate;
        state[0] += (state[1] - state[0]) * (1f - (float) Math.exp(-rate * labelFadeDt));
        // Snap the exponential tails so faded-out families actually drop out
        // of the tick list and fully-faded-in ones reach exact full style.
        if (Math.abs(state[0] - state[1]) < ALPHA_EPSILON) {
            state[0] = state[1];
        }
        return state[0];
    }

    // Reference-style date labels. The year shows ONLY on month-boundary dates
    // (the 1st), so a tick's text is identical whether it is drawn as a week tick
    // or a month tick — it never flips ("Oct 1, 2024" <-> "Oct 1") when the band
    // crosses the week/month zoom boundary. Pure week ticks (8th/15th/22nd) stay
    // bare and simply fade in and out by alpha.
    private static String calendarLabel(LocalDate date) {
        String dayLabel = date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                + " " + date.getDayOfMonth();
        return date.getDayOfMonth() == 1 ? dayLabel + ", " + date.getYear() : dayLabel;
    }

    private float yLabelExtentPx() {
        float height = p.textAscent() + p.textDescent();
        return Math.max(1f, height);
    }

    private float labelClearancePx(float labelExtentPx, float comfort) {
        return Math.max(1f, labelExtentPx * comfort);
    }

    private float maxNumericLabelWidth(
            double low,
            double high,
            double anchor,
            double step,
            DoubleFunction<String> formatter
    ) {
        float maxWidth = 1f;
        double first = firstLineAtOrAfter(low, anchor, step);
        for (double value = first; value <= high + EPSILON; value += step) {
            maxWidth = Math.max(maxWidth, p.textWidth(formatter.apply(cleanZero(value))));
        }
        return maxWidth;
    }

    private float maxCalendarLabelWidth(double low, double high, CalendarFamily family) {
        float maxWidth = 1f;
        boolean subMonth = family.averageDays < CalendarFamily.MONTH.averageDays;
        LocalDate date = family.firstBoundaryOnOrAfter(
                xCalendarDayZero.plusDays((long) Math.floor(low)));
        while (true) {
            long day = ChronoUnit.DAYS.between(xCalendarDayZero, date);
            if (day > high + EPSILON) {
                break;
            }
            // Sub-month families: skip the wide month-boundary label (it carries
            // the year); the typical bare week/day label sets the cadence and the
            // rare month boundary has a full cell of room around it.
            if (!(subMonth && date.getDayOfMonth() == 1)) {
                maxWidth = Math.max(maxWidth, p.textWidth(calendarLabel(date)));
            }
            date = family.next(date);
        }
        return maxWidth;
    }

    private int numericMinorIndex(double baseStep, int bandIndex) {
        double bandStep = niceStep(baseStep, bandIndex);
        for (int index = bandIndex - 1; index > bandIndex - 4; index--) {
            if (isIntegerMultiple(bandStep, niceStep(baseStep, index))) {
                return index;
            }
        }
        return Integer.MIN_VALUE;
    }

    private float densityRamp(float density, float start, float end) {
        return smoothstep(clamp01((density - start) / (end - start)));
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private void drawVerticalGrid(List<Tick> ticks) {
        float plotRight = plotLeft + plotWidth;
        // Same exit window as the x labels, so a line and its label leave
        // together instead of the line dimming first.
        float fadeWidthPx = xExitFadeWidthPx();
        // While the world y-axis line is visible, gridlines beside it are
        // suppressed — a tick a couple of days from the anchor (Jan 1 next
        // to a Dec 30 day-zero) otherwise peeks out as a grey sliver along
        // the axis edge. The clearance is DOMAIN-based (a fraction of the
        // major step) so it scales with canvas width and zoom: a fixed
        // pixel radius covered the 720p offset but not the same tick at
        // 1080p.
        float axisX = domainToCanvasX(xAnchor);
        boolean axisVisible = !(railCollapseRatchet && currentCollapseProgress >= 1f)
                && yAxisFadeFactor(axisX, xBaseGapWidthPx()) > 0f;
        float axisClearancePx = axisStroke + 2f;
        double axisClearanceDomain = 0.06 * xMajorStep;
        p.noFill();

        for (Tick tick : ticks) {
            float t = tick.gridAlpha;
            if (t <= ALPHA_EPSILON || isAxisLineValue(tick.value)) {
                continue;
            }
            if (!showMinorGrid && t < 0.85f) {
                continue;
            }
            float x = domainToCanvasX(tick.value);
            if (x > plotRight + rightGridOverscan + 1f) {
                break;
            }
            if (x < plotLeft - 1f) {
                continue;
            }
            if (axisVisible && (Math.abs(x - axisX) < axisClearancePx
                    || Math.abs(tick.value - xAnchor) < axisClearanceDomain)) {
                continue;
            }
            float fade = leftVerticalFadeFactor(x, fadeWidthPx);
            if (fade <= 0f) {
                continue;
            }
            applyGridStroke(t, fade);
            p.line(x, plotTop - topGridOverscan, x, plotTop + plotHeight);
        }
    }

    private void drawHorizontalGrid(List<Tick> ticks) {
        float plotRight = plotLeft + plotWidth;
        float plotBottom = plotTop + plotHeight;
        p.noFill();

        for (Tick tick : ticks) {
            float t = tick.gridAlpha;
            if (t <= ALPHA_EPSILON) {
                continue;
            }
            if (!showMinorGrid && t < 0.85f) {
                continue;
            }
            float y = domainToCanvasY(tick.value);
            if (y < plotTop - topGridOverscan - 1f) {
                break;
            }
            // Lines slide a little past the bottom edge and dissolve under
            // the label band instead of popping out exactly at the boundary.
            if (y > plotBottom + BOTTOM_GRID_OVERSCAN_PX) {
                continue;
            }
            float bottomFade = y <= plotBottom
                    ? 1f
                    : 1f - smoothstep((y - plotBottom) / BOTTOM_GRID_OVERSCAN_PX);
            if (bottomFade <= ALPHA_EPSILON) {
                continue;
            }
            applyGridStroke(t, bottomFade);
            p.line(plotLeft, y, plotRight + rightGridOverscan, y);
        }
    }

    /** Gridline style is a continuous function of the tick's fade state. */
    private void applyGridStroke(float t, float extraFade) {
        p.strokeWeight(interpolate(minorGridStroke, majorGridStroke, t));
        float brightness = interpolate(34f, 46f, t);
        float alpha = interpolate(28f, 44f, t) * clamp01(t / 0.3f) * extraFade;
        // Lift minors (low t) by minorGridBoost; majors (t≈1) stay as designed.
        alpha *= 1f + (1f - t) * minorGridBoost;
        p.stroke(0, 0, brightness, alpha);
    }

    /**
     * Paints the coloured value-range strata behind everything else. Bands are
     * clamped to the plot rect and use {@link #domainToCanvasY} so they track
     * the y-axis as it pans and zooms. Drawn in three passes (fills, then
     * threshold lines, then tier labels) so edges and text sit over the tints.
     */
    private void drawValueBands() {
        if (valueBands.isEmpty()) {
            return;
        }
        float bandLeft = plotLeft;
        float bandRight = plotLeft + plotWidth + rightGridOverscan;
        float plotBottom = plotTop + plotHeight;
        // Fills run to the top canvas edge so the colour reaches behind the
        // header HUD instead of leaving a black margin above the top tier.
        // (Threshold lines and tier labels below stay clamped to the plot.)
        float fillTop = -p.height / 2f;

        // 1) Region fills — the quiet, dark tints (rectMode is CORNERS).
        p.noStroke();
        for (ValueBand band : valueBands) {
            float top = Math.max(domainToCanvasY(band.hi()), fillTop);
            float bottom = Math.min(domainToCanvasY(band.lo()), plotBottom);
            if (bottom - top <= 0.5f) {
                continue;
            }
            Color f = band.fill();
            p.fill(f.getHue().getValue(), f.getSaturation().getValue(),
                    f.getBrightness().getValue(), f.getAlpha().getValue());
            p.rect(bandLeft, top, bandRight, bottom);
        }

        // 2) Threshold lines at each band's lower boundary (the semantic value).
        p.noFill();
        for (ValueBand band : valueBands) {
            float y = domainToCanvasY(band.lo());
            if (y < plotTop || y > plotBottom) {
                continue;
            }
            Color e = band.edge();
            p.strokeWeight(majorGridStroke);
            p.stroke(e.getHue().getValue(), e.getSaturation().getValue(),
                    e.getBrightness().getValue(), e.getAlpha().getValue());
            p.line(bandLeft, y, bandRight, y);
        }

        // 3) Tier labels — faint, near the left edge, centred in the band.
        if (showValueBandLabels) {
            ensureFont();
            p.textFont(font);
            p.textAlign(Applet.LEFT, Applet.CENTER);
            p.textSize(valueBandLabelSize);
            p.noStroke();
            float labelX = plotLeft + 18f;
            for (ValueBand band : valueBands) {
                String label = band.label();
                if (label == null || label.isEmpty()) {
                    continue;
                }
                float top = Math.max(domainToCanvasY(band.hi()), plotTop);
                float bottom = Math.min(domainToCanvasY(band.lo()), plotBottom);
                if (bottom - top < valueBandLabelSize + 8f) {
                    continue;
                }
                float ly = (top + bottom) / 2f;
                Color e = band.edge();
                p.fill(0, 0, 0, 55f);
                p.text(label, labelX + 1.5f, ly + 1.5f);
                p.fill(e.getHue().getValue(), e.getSaturation().getValue(),
                        e.getBrightness().getValue(), 70f);
                p.text(label, labelX, ly);
            }
        }
    }

    private void drawAxes() {
        float plotRight = plotLeft + plotWidth;
        float plotBottom = plotTop + plotHeight;
        p.strokeWeight(axisStroke);
        drawWorldYAxis(plotBottom);
        drawWorldXAxis(plotRight);
    }

    /**
     * The x axis is the world-space "ground" line at {@code yAnchor}, not a
     * pinned frame edge: when the visible y-window climbs above the ground,
     * the line slides off the bottom of the plot and dissolves, exactly like
     * the moving y-axis exiting through the collapsing left rail.
     */
    private void drawWorldXAxis(float plotRight) {
        float plotBottom = plotTop + plotHeight;
        float y = domainToCanvasY(yAnchor);
        if (y < plotTop - topGridOverscan) {
            return;
        }
        float alphaFactor = 1f;
        if (y > plotBottom) {
            alphaFactor = 1f - clamp01((y - plotBottom) / BOTTOM_AXIS_FALL_FADE_PX);
        }
        if (alphaFactor <= 0f) {
            return;
        }
        strokeWithAlpha(axisColor, alphaFactor);
        p.line(plotLeft, y, plotRight + rightGridOverscan, y);
    }

    private void drawBottomLabelBand() {
        float plotRight = plotLeft + plotWidth;
        float plotBottom = plotTop + plotHeight;
        p.noStroke();
        p.fill(labelBackgroundColor);
        p.rect(plotLeft, plotBottom + LABEL_BAND_GAP, plotRight + rightGridOverscan, p.height / 2f);
    }

    private void drawLeftLabelBand() {
        float plotBottom = plotTop + plotHeight;
        p.noStroke();
        p.fill(labelBackgroundColor);
        p.rect(viewportLeft, plotTop - topGridOverscan, plotLeft - LABEL_BAND_GAP, plotBottom);
    }

    private void drawYLabels(List<Tick> ticks) {
        float plotBottom = plotTop + plotHeight;
        p.textAlign(Applet.RIGHT, Applet.CENTER);
        for (Tick tick : ticks) {
            float t = tick.labelAlpha;
            if (t <= ALPHA_EPSILON) {
                continue;
            }
            if (!showMinorGrid && t < 0.85f) {
                continue;
            }
            float y = domainToCanvasY(tick.value);
            if (y < plotTop - topGridOverscan - 1f) {
                break;
            }
            if (y > plotBottom + BOTTOM_GRID_OVERSCAN_PX) {
                continue;
            }
            float bottomFade = y <= plotBottom
                    ? 1f
                    : 1f - smoothstep((y - plotBottom) / BOTTOM_GRID_OVERSCAN_PX);
            if (bottomFade <= ALPHA_EPSILON) {
                continue;
            }
            applyLabelStyle(t, bottomFade);
            p.text(tick.label, yLabelX(), y);
        }
    }

    private void drawXLabels(List<Tick> ticks) {
        float labelY = plotTop + plotHeight + xLabelInset;
        float plotRight = plotLeft + plotWidth;
        float fadeWidthPx = xExitFadeWidthPx();

        p.textAlign(Applet.CENTER, Applet.CENTER);
        for (Tick tick : ticks) {
            float t = tick.labelAlpha;
            if (t <= ALPHA_EPSILON) {
                continue;
            }
            if (!showMinorGrid && t < 0.85f) {
                continue;
            }
            float x = domainToCanvasX(tick.value);
            if (x > plotRight + rightGridOverscan + 1f) {
                break;
            }
            if (!shouldDrawXLabel(tick.value, x)) {
                continue;
            }
            float fade = xLabelFadeFactor(tick.value, x, fadeWidthPx);
            // Overscan labels fade against the viewport's right edge instead
            // of rendering half-clipped ("Jan 1, 20|").
            applyLabelStyle(t, 1f);
            float halfWidth = p.textWidth(tick.label) / 2f;
            fade *= clamp01((p.width / 2f - x) / Math.max(1f, halfWidth * 1.2f));
            if (fade <= ALPHA_EPSILON) {
                continue;
            }
            applyLabelStyle(t, fade);
            p.text(tick.label, x, labelY);
        }
    }

    /**
     * Label style is a continuous function of the tick's fade state: fading
     * ticks shrink toward the minor size and dim, which is what keeps
     * crossfades readable instead of two full label bands fighting.
     */
    private void applyLabelStyle(float t, float extraFade) {
        p.textSize(interpolate(minorLabelSize, majorLabelSize, t));
        float brightness = interpolate(68f, 100f, t);
        float alpha = 100f * (float) Math.pow(t, 2.0) * extraFade;
        p.fill(0, 0, brightness, alpha);
    }

    // ------------------------------------------------------------------
    // Rail collapse + edge fades
    // ------------------------------------------------------------------

    private void ensureFont() {
        if (font == null) {
            font = p.createFont("src/data/cmunbmr.ttf", 150, true);
        }
    }

    private String formatDefaultLabel(double value) {
        return numberFormat.format(cleanZero(value));
    }

    private float xBaseGapWidthPx() {
        if (xMajorStep <= 0 || xMax <= xMin || plotWidth <= 0) {
            return 1f;
        }
        return Math.max(1f, (float) (plotWidth * (xMajorStep / (xMax - xMin))));
    }

    /** Exit-fade ramp width, capped so it stays a local left-edge effect even
     *  when zoomed far below the (quarter) major-step cadence. */
    private float xExitFadeWidthPx() {
        return Math.min(xBaseGapWidthPx() * X_LABEL_EXIT_FADE_FRACTION,
                plotWidth * X_LABEL_EXIT_FADE_MAX_FRACTION);
    }

    private float railCollapseProgress() {
        if (xMajorStep <= EPSILON) {
            return 0f;
        }
        float progress = smoothstep(clamp01((float) ((xMin - xAnchor) / xMajorStep)));
        if (railCollapseRatchet) {
            reachedCollapseProgress = Math.max(reachedCollapseProgress, progress);
            return reachedCollapseProgress;
        }
        return progress;
    }

    private float slimRailWidth(List<Tick> yTicks) {
        float minRailWidth = yLabelInset + SLIM_RAIL_PADDING;
        if (!showLabels) {
            return minRailWidth;
        }

        ensureFont();
        p.textFont(font);

        // A scene that declares a reserve (setYLabelReserve) gets a STABLE rail:
        // it is pre-sized to the widest label the axis will ever show, faint
        // transient labels are ignored, and widths are measured at a fixed major
        // size — so the rail never juts mid-race when a label gains a digit
        // ("8" -> "10") or a line dips to a negative tick. Scenes that declare no
        // reserve keep the original adaptive behaviour (count any drawn label at
        // its live crossfade size), so this change cannot affect them.
        boolean reserved = slimRailReserveLabels != null;
        float maxLabelWidth = 0f;
        if (reserved) {
            p.textSize(majorLabelSize);
            for (String s : slimRailReserveLabels) {
                if (s != null) {
                    maxLabelWidth = Math.max(maxLabelWidth, p.textWidth(s));
                }
            }
        }
        for (Tick tick : yTicks) {
            boolean skip = reserved
                    ? tick.labelAlpha < SLIM_RAIL_LABEL_MIN_ALPHA
                    : tick.labelAlpha <= ALPHA_EPSILON;
            if (skip) {
                continue;
            }
            float y = domainToCanvasY(tick.value);
            if (y < plotTop - topGridOverscan - 1f || y > plotTop + plotHeight + 1f) {
                continue;
            }
            p.textSize(reserved
                    ? majorLabelSize
                    : interpolate(minorLabelSize, majorLabelSize, tick.labelAlpha));
            maxLabelWidth = Math.max(maxLabelWidth, p.textWidth(tick.label));
        }
        return Math.max(minRailWidth, maxLabelWidth + yLabelInset + SLIM_RAIL_PADDING);
    }

    /**
     * Tick families come from the ABSOLUTE decimal 1-2-5 ladder
     * (..., 0.05, 0.1, 0.2, 0.5, 1, 2, 5, ...), anchored at the configured
     * base step (index 0). Anchoring multipliers at the base instead
     * (base x 1, 2, 5, 10) produced base-relative steps like 0.25 from a
     * 0.05 base — "1.25, 1.75" axis values where every charting convention
     * (and the data's own idiom) expects "1.20, 1.40". Bonus: 0.1 -> 0.2 is
     * a nested hand-off (seamless), unlike the old 0.1 -> 0.25 crossfade.
     * A base step that is not itself on the ladder is inserted as index 0.
     */
    private double niceStep(double baseStep, int index) {
        if (index == 0) {
            return baseStep;
        }
        int baseRung = ladderFloorRung(baseStep);
        boolean baseOnLadder = Math.abs(ladderValue(baseRung) - baseStep) <= baseStep * 1e-9;
        int rung = baseRung + index + (!baseOnLadder && index < 0 ? 1 : 0);
        return ladderValue(rung);
    }

    private double ladderValue(int rung) {
        int power = Math.floorDiv(rung, NICE_STEP_MULTIPLIERS.length);
        int multiplierIndex = Math.floorMod(rung, NICE_STEP_MULTIPLIERS.length);
        return NICE_STEP_MULTIPLIERS[multiplierIndex] * Math.pow(10, power);
    }

    /** Largest ladder rung whose value is <= the given value. */
    private int ladderFloorRung(double value) {
        int rung = (int) Math.floor(Math.log10(value)) * NICE_STEP_MULTIPLIERS.length;
        while (ladderValue(rung + 1) <= value * (1.0 + EPSILON)) {
            rung++;
        }
        while (ladderValue(rung) > value * (1.0 + EPSILON)) {
            rung--;
        }
        return rung;
    }

    private int niceStepFloorIndex(double baseStep, double targetStep) {
        if (targetStep <= EPSILON) {
            return 0;
        }

        int index = 0;
        while (index < 120 && niceStep(baseStep, index + 1) <= targetStep * (1.0 + EPSILON)) {
            index++;
        }
        while (index > -120 && niceStep(baseStep, index) > targetStep * (1.0 + EPSILON)) {
            index--;
        }
        return index;
    }

    /**
     * Exit fade against the left plot edge. Only active once the camera has
     * started eating the left edge (the rail collapse): a static framing must
     * not dim ticks that merely sit near the edge — that read as "Apr 1 is
     * already half-faded" on the opening frame of the race scene.
     */
    private float leftVerticalFadeFactor(float x, float fadeWidthPx) {
        if (currentCollapseProgress <= 0f || fadeWidthPx <= 0f) {
            return 1f;
        }
        float raw;
        if (x <= plotLeft) {
            raw = 0f;
        } else if (x >= plotLeft + fadeWidthPx) {
            raw = 1f;
        } else {
            raw = smoothstep((x - plotLeft) / fadeWidthPx);
        }
        return interpolate(1f, raw, currentCollapseProgress);
    }

    /**
     * The moving world y-axis dissolves over a SHORT zone as it crosses the
     * plot edge, and is fully gone before it reaches the y-label text — the
     * rest of its journey across the rail is hidden under the left band
     * cover mask. (The old one-base-gap fade kept it ~40% visible all the
     * way across the rail, slicing through the pinned labels.)
     */
    private float yAxisFadeFactor(float x, float fadeWidthPx) {
        if (currentCollapseProgress <= 0f) {
            return 1f;
        }
        float fadeZone = Math.min(fadeWidthPx, Y_AXIS_EXIT_FADE_PX);
        if (fadeZone <= 0f) {
            return 1f;
        }
        if (x >= plotLeft) {
            return 1f;
        }
        if (x <= plotLeft - fadeZone) {
            return 0f;
        }
        return 1f - smoothstep((plotLeft - x) / fadeZone);
    }

    private void drawWorldYAxis(float plotBottom) {
        // Once the rail has fully collapsed under the ratchet, the axis is
        // gone for good — zooming back out must not redraw it at the edge.
        if (railCollapseRatchet && currentCollapseProgress >= 1f) {
            return;
        }
        float x = domainToCanvasX(xAnchor);
        float alphaFactor = yAxisFadeFactor(x, xBaseGapWidthPx());
        if (alphaFactor <= 0f) {
            return;
        }
        strokeWithAlpha(axisColor, alphaFactor);
        p.line(x, plotTop - topGridOverscan, x, plotBottom);
    }

    private boolean shouldDrawXLabel(double value, float x) {
        if (isAxisLineValue(value)) {
            return currentCollapseProgress > 0f && x >= viewportLeft - 1f;
        }
        return x >= plotLeft - 1f && !isLeftAxisLabel(x);
    }

    private float xLabelFadeFactor(double value, float x, float fadeWidthPx) {
        if (isAxisLineValue(value)) {
            return yAxisFadeFactor(x, fadeWidthPx);
        }
        return leftVerticalFadeFactor(x, fadeWidthPx);
    }

    private void strokeWithAlpha(Color color, float alphaFactor) {
        p.stroke(
                color.getHue().getValue(),
                color.getSaturation().getValue(),
                color.getBrightness().getValue(),
                color.getAlpha().getValue() * clamp01(alphaFactor)
        );
    }

    private double firstLineAtOrAfter(double min, double anchor, double step) {
        double index = Math.ceil((min - anchor - EPSILON) / step);
        return anchor + index * step;
    }

    private boolean isLeftAxisLabel(float x) {
        return Math.abs(x - plotLeft) < 1f;
    }

    private boolean isAxisLineValue(double value) {
        return Math.abs(cleanZero(value - xAnchor)) < 1e-4;
    }

    private float yLabelX() {
        return plotLeft - yLabelInset;
    }

    private float interpolate(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float smoothstep(float value) {
        return value * value * (3f - 2f * value);
    }

    private double cleanZero(double value) {
        return Math.abs(value) < EPSILON ? 0 : value;
    }

    private double requirePositive(double value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /** One merged tick; alphas accumulate as a capped sum over every family containing it. */
    private static final class Tick {
        private final double value;
        private final String label;
        private float gridAlpha;
        private float labelAlpha;

        private Tick(double value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    private enum CalendarFamily {
        DAY(1.0) {
            @Override
            LocalDate firstBoundaryOnOrAfter(LocalDate date) {
                return date;
            }

            @Override
            LocalDate next(LocalDate date) {
                return date.plusDays(1);
            }
        },
        WEEK(7.61) {
            // Week boundaries reset each month (1st, 8th, 15th, 22nd) so they
            // stay aligned to the month grid; the final "week" is the ~9-day
            // remainder up to the next 1st.
            @Override
            LocalDate firstBoundaryOnOrAfter(LocalDate date) {
                int d = date.getDayOfMonth();
                if (d == 1 || d == 8 || d == 15 || d == 22) {
                    return date;
                }
                if (d < 8) {
                    return date.withDayOfMonth(8);
                }
                if (d < 15) {
                    return date.withDayOfMonth(15);
                }
                if (d < 22) {
                    return date.withDayOfMonth(22);
                }
                return date.plusMonths(1).withDayOfMonth(1);
            }

            @Override
            LocalDate next(LocalDate date) {
                int d = date.getDayOfMonth();
                if (d < 8) {
                    return date.withDayOfMonth(8);
                }
                if (d < 15) {
                    return date.withDayOfMonth(15);
                }
                if (d < 22) {
                    return date.withDayOfMonth(22);
                }
                return date.plusMonths(1).withDayOfMonth(1);
            }
        },
        MONTH(30.44) {
            @Override
            LocalDate firstBoundaryOnOrAfter(LocalDate date) {
                return date.getDayOfMonth() == 1 ? date : date.plusMonths(1).withDayOfMonth(1);
            }

            @Override
            LocalDate next(LocalDate date) {
                return date.plusMonths(1);
            }
        },
        QUARTER(91.31) {
            @Override
            LocalDate firstBoundaryOnOrAfter(LocalDate date) {
                LocalDate monthStart = MONTH.firstBoundaryOnOrAfter(date);
                while ((monthStart.getMonthValue() - 1) % 3 != 0) {
                    monthStart = monthStart.plusMonths(1);
                }
                return monthStart;
            }

            @Override
            LocalDate next(LocalDate date) {
                return date.plusMonths(3);
            }
        },
        YEAR(365.25) {
            @Override
            LocalDate firstBoundaryOnOrAfter(LocalDate date) {
                LocalDate janFirst = date.withDayOfYear(1);
                return janFirst.isBefore(date) ? janFirst.plusYears(1) : janFirst;
            }

            @Override
            LocalDate next(LocalDate date) {
                return date.plusYears(1);
            }
        },
        TWO_YEARS(730.5) {
            @Override
            LocalDate firstBoundaryOnOrAfter(LocalDate date) {
                LocalDate year = YEAR.firstBoundaryOnOrAfter(date);
                return year.getYear() % 2 == 0 ? year : year.plusYears(1);
            }

            @Override
            LocalDate next(LocalDate date) {
                return date.plusYears(2);
            }
        },
        FIVE_YEARS(1826.25) {
            @Override
            LocalDate firstBoundaryOnOrAfter(LocalDate date) {
                LocalDate year = YEAR.firstBoundaryOnOrAfter(date);
                while (year.getYear() % 5 != 0) {
                    year = year.plusYears(1);
                }
                return year;
            }

            @Override
            LocalDate next(LocalDate date) {
                return date.plusYears(5);
            }
        },
        TEN_YEARS(3652.5) {
            @Override
            LocalDate firstBoundaryOnOrAfter(LocalDate date) {
                LocalDate year = YEAR.firstBoundaryOnOrAfter(date);
                while (year.getYear() % 10 != 0) {
                    year = year.plusYears(1);
                }
                return year;
            }

            @Override
            LocalDate next(LocalDate date) {
                return date.plusYears(10);
            }
        };

        final double averageDays;

        CalendarFamily(double averageDays) {
            this.averageDays = averageDays;
        }

        abstract LocalDate firstBoundaryOnOrAfter(LocalDate date);

        abstract LocalDate next(LocalDate date);
    }
}
