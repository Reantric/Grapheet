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
 * quarter, year, 2/5/10 years — when the x axis is in calendar mode). Each
 * family gets a continuous alpha from a bump curve over the log of its
 * screen-space spacing, so families fade in as their spacing approaches a
 * comfortable ideal and fade out as they get cramped or too sparse.
 *
 * <p>Families on the ladder are not mutually nested (500 is not a multiple of
 * 200), so families are NOT rendered independently. Instead all visible
 * families are merged into one tick list; a tick's alpha is the max over the
 * families that contain it, and its visual style (size, brightness, stroke)
 * derives continuously from that alpha. This is what makes zoom transitions
 * read as a clean crossfade instead of duplicated/cramped label bands.
 */
public final class DataGrid {
    private static final double EPSILON = 1e-6;
    private static final float LABEL_BAND_GAP = 14f;
    private static final float SLIM_RAIL_PADDING = 16f;
    private static final double[] NICE_STEP_MULTIPLIERS = {1.0, 2.0, 5.0};

    private static final float ALPHA_EPSILON = 0.02f;
    private static final float BOTTOM_AXIS_FALL_FADE_PX = 36f;
    private static final float Y_AXIS_EXIT_FADE_PX = 48f;
    // Label crossfades are TIME-based and exactly ONE family per axis is
    // the live "label band": the position-derived bump alphas only elect
    // the band (the incumbent keeps it unless a challenger clearly beats
    // it), and each family's rendered alpha eases toward in-band/not at a
    // fixed rate. Driving rendered alphas straight off pixel spacing left
    // labels stuck at mid-grey for tens of seconds when the camera crawled
    // through a crossfade, and any rule that allowed two strong NON-NESTED
    // families at once (5-year vs 2-year ticks) overprinted label text.
    private static final float LABEL_FADE_IN_RATE = 4.5f;
    /** X fade-out stays fast: during a zoom the dying calendar labels keep
     *  compressing toward their neighbours, so the ghost-overlap window
     *  must be short. The y axis has no such pressure and reads better
     *  with a gentler exit. */
    private static final float X_LABEL_FADE_OUT_RATE = 5.5f;
    private static final float Y_LABEL_FADE_OUT_RATE = 3.2f;
    /** Hysteresis for a COARSER challenger taking the band (and general
     *  anti-flap margin). */
    private static final float LABEL_BAND_STICKINESS = 0.12f;
    /** A FINER challenger only needs a tiny edge: refinements (fading
     *  detail back in when the window contracts) were effectively
     *  unreachable behind the full stickiness, because a sparse incumbent's
     *  raw alpha rarely drops far below a near-ideal finer family's. At
     *  0.02 (with ideal 175) the y band refines at ~127px base spacing and
     *  coarsens at ~118px — a clean hysteresis gap, no flap zone. */
    private static final float LABEL_BAND_REFINE_STICKINESS = 0.02f;
    /** Below this raw alpha the incumbent's labels are getting physically
     *  cramped — it loses its stickiness and even concedes a small bias to
     *  the challenger, so the hand-off fires BEFORE full-brightness labels
     *  compress into each other during a fast zoom-out. */
    private static final float LABEL_BAND_CRAMP_FLOOR = 0.5f;
    /** Shared left-edge exit window for x gridlines AND their labels (a
     *  fraction of the base gap), so both leave together and the leftmost
     *  date does not hang around half-faded for seconds. */
    private static final float X_LABEL_EXIT_FADE_FRACTION = 0.45f;
    /** Horizontal gridlines and y labels overshoot the plot bottom by this
     *  much and dissolve, instead of popping out at the boundary. */
    private static final float BOTTOM_GRID_OVERSCAN_PX = 30f;

    // Numeric-axis bump curves (log2 distance from the ideal spacing).
    // All *_SPACING_PX / *_RAMP_*_PX constants are tuned for a 1920px-wide
    // canvas and are scaled by densityScale() at use, so a 1280px rough cut
    // and a 3840px final pick the same tick families as the 1920px design.
    private static final float REFERENCE_CANVAS_WIDTH = 1920f;
    /** Calibrated against the user's two reference frames: the y band must
     *  coarsen by ~104px base spacing (raw < 0.88 vs a full challenger)
     *  and refine again above ~111px (incumbent raw < 0.98 + refine edge). */
    private static final float LABEL_IDEAL_SPACING_PX = 154f;
    private static final float LABEL_PLATEAU_LOG2 = 0.5f;
    private static final float LABEL_SUPPORT_LOG2 = 0.95f;

    // Calendar-axis label bump curve. Labels stay sparse (full
    // "Dec 1, 2013"-style text needs room, so quarters dominate a ~1-year
    // window and years take over after a zoom-out).
    private static final float DATE_LABEL_IDEAL_SPACING_PX = 430f;
    private static final float DATE_LABEL_PLATEAU_LOG2 = 0.6f;
    private static final float DATE_LABEL_SUPPORT_LOG2 = 1.5f;

    // GRIDLINES follow the label band: the band family's lines are the
    // majors, the family ONE step finer shows as soft minors gated by the
    // room the band's spacing leaves (SUB_BASE_GRID ramp), and every other
    // family fades out — lines whose labels died must die with them.
    private static final float MINOR_GRID_STRENGTH = 0.55f;

    // Steps finer than the configured base step only fade in once the base
    // family itself has become sparse (px spacing of the base family) —
    // matching the pre-rework branch, where sub-base lines stayed hidden at
    // ordinary zoom and the grid kept one clean dominant rhythm.
    // A sub-base family fades in only while its PARENT family (one ladder
    // step coarser) is sparse: gates accumulate multiplicatively with depth,
    // so one family of soft minors appears at a time. (A single gate keyed
    // on the base family's spacing once admitted EVERY finer family at a
    // tight window — 0.025 AND 0.01 under a 0.05 base — reading as a mesh
    // of gridlines at the race scene's opening frame.)
    private static final float SUB_BASE_GRID_RAMP_START_PX = 150f;
    private static final float SUB_BASE_GRID_RAMP_END_PX = 300f;
    // Sub-base LABELS need sparser parent spacing than gridlines before
    // they help. With the time-based crossfade the raw gate value must
    // exceed LABEL_FADE_ON_THRESHOLD before finer labels actually switch
    // on, so the effective trigger sits well above the ramp start — at the
    // race scene's tightest y-window (base gap ~250px of 1920) finer labels
    // stay off, while genuinely sparse spacing pulls them in. Scenes whose
    // formatter would round sub-base values badly should use an adaptive
    // precision formatter.
    private static final float SUB_BASE_LABEL_RAMP_START_PX = 210f;
    private static final float SUB_BASE_LABEL_RAMP_END_PX = 320f;

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

    private double xMin = 0;
    private double xMax = 24;
    private double yMin = 800;
    private double yMax = 1800;

    private double xAnchor = 0;
    private double yAnchor = 0;
    private double xMajorStep = 3;
    private double yMajorStep = 100;

    /** When non-null, x values are interpreted as days since this date. */
    private LocalDate xCalendarDayZero;

    private DoubleFunction<String> xLabelFormatter = this::formatDefaultLabel;
    private DoubleFunction<String> yLabelFormatter = this::formatDefaultLabel;

    private final Color axisColor = new Color(ColorType.WHITE);
    private final Color labelBackgroundColor = new Color(0, 0, 0, 92);

    private float axisStroke = 5f;
    private float majorGridStroke = 2.75f;
    private float minorGridStroke = 1.5f;
    private float majorLabelSize = 34f;
    private float minorLabelSize = 27f;
    private float xLabelInset = 40f;
    private float yLabelInset = 18f;
    private float topGridOverscan = 112f;
    private float rightGridOverscan = 116f;

    private boolean showMinorGrid = true;
    private boolean showLabels = true;
    private boolean showAxisBackgroundStrips = true;
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

        List<Tick> yTicks = buildNumericTicks(yMin, yMax, yAnchor, yMajorStep, plotHeight,
                topGridOverscan, yLabelFormatter, yLabelFadeStates, yGridFadeStates);

        currentCollapseProgress = railCollapseProgress();
        float currentLeftRailWidth = interpolate(leftInset, slimRailWidth(yTicks), currentCollapseProgress);
        plotLeft = viewportLeft + currentLeftRailWidth;
        plotWidth = p.width - currentLeftRailWidth - rightInset;
        if (plotWidth <= 0) {
            return;
        }

        List<Tick> xTicks = xCalendarDayZero != null
                ? buildCalendarTicks()
                : buildNumericTicks(xMin, xMax, xAnchor, xMajorStep, plotWidth,
                        rightGridOverscan, xLabelFormatter, xLabelFadeStates, xGridFadeStates);

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
     * Switch the x axis to calendar mode: x values become days since
     * {@code dayZero}, gridlines/labels sit on month, quarter (Jan 1, Apr 1,
     * Jul 1, Oct 1) and year boundaries, and fade between granularities with
     * zoom. Also sets the x major step to an average quarter so the moving
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

    public void setXLabelFormatter(DoubleFunction<String> formatter) {
        this.xLabelFormatter = Objects.requireNonNull(formatter, "formatter");
    }

    public void setYLabelFormatter(DoubleFunction<String> formatter) {
        this.yLabelFormatter = Objects.requireNonNull(formatter, "formatter");
    }

    public void showMinorGrid(boolean showMinorGrid) {
        this.showMinorGrid = showMinorGrid;
    }

    public void showLabels(boolean showLabels) {
        this.showLabels = showLabels;
    }

    public void showAxisBackgroundStrips(boolean showAxisBackgroundStrips) {
        this.showAxisBackgroundStrips = showAxisBackgroundStrips;
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

        int firstIndex = niceStepFloorIndex(baseStep, span * 8.0 / pixelSpan) - 1;
        int lastIndex = niceStepFloorIndex(baseStep, span * 1500.0 / pixelSpan) + 2;

        float scale = densityScale();
        // Per-depth label gates: gate(index) = product over j in (index, 0]
        // of ramp(parent spacing of family j) — each finer family needs
        // every ancestor down from the base to have gone sparse first.
        int subDepth = Math.max(0, -firstIndex);
        float[] subLabelGate = new float[subDepth];
        float labelGateAcc = 1f;
        for (int index = -1; index >= firstIndex; index--) {
            float parentSpacingPx = (float) (pixelSpan * (niceStep(baseStep, index + 1) / span));
            labelGateAcc *= subBaseRamp(parentSpacingPx,
                    SUB_BASE_LABEL_RAMP_START_PX * scale, SUB_BASE_LABEL_RAMP_END_PX * scale);
            subLabelGate[-1 - index] = labelGateAcc;
        }

        int count = lastIndex - firstIndex + 1;
        double[] steps = new double[count];
        float[] rawLabelAlphas = new float[count];
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
            float labelAlpha = bumpAlpha(spacingPx, LABEL_IDEAL_SPACING_PX * scale,
                    LABEL_PLATEAU_LOG2, LABEL_SUPPORT_LOG2);
            if (index < 0) {
                labelAlpha *= subLabelGate[-1 - index];
            }
            rawLabelAlphas[i] = labelAlpha;
        }

        boolean isYAxis = labelFadeStates == yLabelFadeStates;
        Object previousBand = isYAxis ? yLabelBandKey : xLabelBandKey;
        Object lingering = isYAxis ? yLingeringBandKey : xLingeringBandKey;
        Object band = selectLabelBand(previousBand, fadeKeys, rawLabelAlphas);
        if (previousBand != null && !band.equals(previousBand)) {
            Nesting nesting = previousBand instanceof Integer oldIndex && band instanceof Integer newIndex
                    ? numericNesting(baseStep, oldIndex, newIndex)
                    : Nesting.NONE;
            lingering = applyBandTransition(
                    labelFadeStates, gridFadeStates, previousBand, band, lingering, nesting);
        }

        // Gridlines follow the band: band lines are the majors, the family
        // one step finer fades in as soft minors when the band's spacing
        // leaves room, everything else dies with its labels.
        int bandIndex = (Integer) band;
        float bandSpacingPx = (float) (pixelSpan * (niceStep(baseStep, bandIndex) / span));
        float minorTarget = MINOR_GRID_STRENGTH * subBaseRamp(bandSpacingPx,
                SUB_BASE_GRID_RAMP_START_PX * scale, SUB_BASE_GRID_RAMP_END_PX * scale);

        float outRate = isYAxis ? Y_LABEL_FADE_OUT_RATE : X_LABEL_FADE_OUT_RATE;
        float[] labelAlphas = new float[count];
        float[] gridAlphas = new float[count];
        for (int i = 0; i < count; i++) {
            int index = firstIndex + i;
            boolean on = fadeKeys[i].equals(band) || fadeKeys[i].equals(lingering);
            labelAlphas[i] = fadedAlpha(labelFadeStates, fadeKeys[i], on ? 1f : 0f, outRate);
            float gridTarget = on ? 1f : (index == bandIndex - 1 ? minorTarget : 0f);
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

        double low = Math.min(min, anchor);
        double high = max + span * (overscanPx / pixelSpan);

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
                tick.gridAlpha = Math.max(tick.gridAlpha, gridAlpha);
                tick.labelAlpha = Math.max(tick.labelAlpha, labelAlpha);
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

        float scale = densityScale();
        // Physical collision guard: the bump curve alone happily keeps a
        // family at full alpha while its ~160px label texts compress to
        // zero gap during the final zoom-out. Measure the real text width
        // and kill a family's label alpha as its spacing reaches it, which
        // also trips the election's cramp floor right at first contact.
        ensureFont();
        p.textFont(font);
        p.textSize(majorLabelSize);
        // textWidth() returns the advance width; the visible ink is ~10%
        // narrower (side bearings). Using the raw advance dented the final
        // two-year band (183px spacing vs ~185px advance) enough to flip
        // the election to sparse five-year labels even though the rendered
        // texts had clear gaps.
        float labelInkPx = 0.9f * p.textWidth("Jan 1, 2028");
        CalendarFamily[] families = CalendarFamily.values();
        float[] rawLabelAlphas = new float[families.length];
        for (int i = 0; i < families.length; i++) {
            float spacingPx = (float) (plotWidth * (families[i].averageDays / span));
            float cramp = smoothstep(clamp01((spacingPx - labelInkPx) / (14f * scale)));
            rawLabelAlphas[i] = cramp * bumpAlpha(spacingPx, DATE_LABEL_IDEAL_SPACING_PX * scale,
                    DATE_LABEL_PLATEAU_LOG2, DATE_LABEL_SUPPORT_LOG2);
        }
        Object previousBand = xLabelBandKey;
        Object band = selectLabelBand(previousBand, families, rawLabelAlphas);
        if (previousBand != null && !band.equals(previousBand)) {
            Nesting nesting = previousBand instanceof CalendarFamily oldFamily
                    && band instanceof CalendarFamily newFamily
                    ? calendarNesting(oldFamily, newFamily)
                    : Nesting.NONE;
            xLingeringBandKey = applyBandTransition(xLabelFadeStates, xGridFadeStates,
                    previousBand, band, xLingeringBandKey, nesting);
        }
        xLabelBandKey = band;

        // Gridlines follow the band (majors = band, soft minors = the next
        // finer family while the band's spacing leaves room, rest fade out).
        CalendarFamily bandFamily = (CalendarFamily) band;
        float bandSpacingPx = (float) (plotWidth * (bandFamily.averageDays / span));
        float minorTarget = MINOR_GRID_STRENGTH * subBaseRamp(bandSpacingPx,
                SUB_BASE_GRID_RAMP_START_PX * scale, SUB_BASE_GRID_RAMP_END_PX * scale);
        CalendarFamily minorFamily = bandFamily.ordinal() > 0
                ? families[bandFamily.ordinal() - 1]
                : null;

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
                tick.gridAlpha = Math.max(tick.gridAlpha, gridAlpha);
                tick.labelAlpha = Math.max(tick.labelAlpha, labelAlpha);
                date = family.next(date);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * Elect the single live label-band family for an axis: the candidate
     * with the strongest raw bump alpha, except a sitting incumbent keeps
     * the band unless the challenger clearly beats it (stickiness). One
     * band at a time is what prevents two strong NON-NESTED families
     * (5-year vs 2-year ticks, 500 vs 200) from overprinting label text;
     * nested transitions share tick positions and crossfade invisibly.
     */
    private Object selectLabelBand(Object incumbentKey, Object[] keys, float[] rawAlphas) {
        int best = 0;
        int incumbent = -1;
        for (int i = 0; i < keys.length; i++) {
            if (rawAlphas[i] > rawAlphas[best]) {
                best = i;
            }
            if (keys[i].equals(incumbentKey)) {
                incumbent = i;
            }
        }
        if (incumbent >= 0) {
            // Keys are ordered finest-first: a challenger at a smaller index
            // is a refinement and only needs a small edge.
            float stickiness = best < incumbent
                    ? LABEL_BAND_REFINE_STICKINESS
                    : LABEL_BAND_STICKINESS;
            float bias = rawAlphas[incumbent] >= LABEL_BAND_CRAMP_FLOOR
                    ? stickiness
                    : -0.05f;
            if (rawAlphas[incumbent] + bias >= rawAlphas[best]) {
                return keys[incumbent];
            }
        }
        return keys[best];
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

    // Full reference-style date labels: "Dec 1, 2013", "Mar 1, 2014", ...
    private static String calendarLabel(LocalDate date) {
        String month = date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return month + " " + date.getDayOfMonth() + ", " + date.getYear();
    }

    /**
     * Continuous fade curve: full alpha while the family's pixel spacing is
     * within {@code plateauLog2} octaves of the ideal spacing, easing to
     * zero at {@code supportLog2} octaves. Symmetric: rendered alphas are
     * band-binary nowadays, so this curve only drives the band ELECTION —
     * and there a sparse-side leniency is harmful, because a sparse coarse
     * incumbent whose raw never decays can never be unseated by a finer
     * family when the window contracts (the "reverse" fade-in).
     */
    private float bumpAlpha(float spacingPx, float idealPx, float plateauLog2, float supportLog2) {
        if (spacingPx <= 0f) {
            return 0f;
        }
        float u = Math.abs((float) (Math.log(spacingPx / idealPx) / Math.log(2)));
        if (u <= plateauLog2) {
            return 1f;
        }
        if (u >= supportLog2) {
            return 0f;
        }
        return 1f - smoothstep((u - plateauLog2) / (supportLog2 - plateauLog2));
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private void drawVerticalGrid(List<Tick> ticks) {
        float plotRight = plotLeft + plotWidth;
        // Same exit window as the x labels, so a line and its label leave
        // together instead of the line dimming first.
        float fadeWidthPx = xBaseGapWidthPx() * X_LABEL_EXIT_FADE_FRACTION;
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
        p.stroke(0, 0, brightness, alpha);
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
        float fadeWidthPx = xBaseGapWidthPx() * X_LABEL_EXIT_FADE_FRACTION;

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

    /** Tick-density constants are tuned on a 1920px canvas; scale them so any
     *  render width picks the same families at the same domain window. */
    private float densityScale() {
        return p.width / REFERENCE_CANVAS_WIDTH;
    }

    private float subBaseRamp(float spacingPx, float startPx, float endPx) {
        return smoothstep(clamp01((spacingPx - startPx) / (endPx - startPx)));
    }

    private float xBaseGapWidthPx() {
        if (xMajorStep <= 0 || xMax <= xMin || plotWidth <= 0) {
            return 1f;
        }
        return Math.max(1f, (float) (plotWidth * (xMajorStep / (xMax - xMin))));
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

        float maxLabelWidth = 0f;
        for (Tick tick : yTicks) {
            if (tick.labelAlpha <= ALPHA_EPSILON) {
                continue;
            }
            float y = domainToCanvasY(tick.value);
            if (y < plotTop - topGridOverscan - 1f || y > plotTop + plotHeight + 1f) {
                continue;
            }
            p.textSize(interpolate(minorLabelSize, majorLabelSize, tick.labelAlpha));
            maxLabelWidth = Math.max(maxLabelWidth, p.textWidth(tick.label));
        }
        return Math.max(minRailWidth, maxLabelWidth + yLabelInset + SLIM_RAIL_PADDING);
    }

    private double niceStep(double baseStep, int index) {
        int power = Math.floorDiv(index, NICE_STEP_MULTIPLIERS.length);
        int multiplierIndex = Math.floorMod(index, NICE_STEP_MULTIPLIERS.length);
        return baseStep * NICE_STEP_MULTIPLIERS[multiplierIndex] * Math.pow(10, power);
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

    /** One merged tick; alphas are the max over every family containing it. */
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
