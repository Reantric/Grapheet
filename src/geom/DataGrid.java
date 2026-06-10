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
import java.util.List;
import java.util.Locale;
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

    // Numeric-axis bump curves (log2 distance from the ideal spacing).
    private static final float GRID_IDEAL_SPACING_PX = 84f;
    private static final float GRID_PLATEAU_LOG2 = 0.75f;
    private static final float GRID_SUPPORT_LOG2 = 1.55f;
    private static final float LABEL_IDEAL_SPACING_PX = 112f;
    private static final float LABEL_PLATEAU_LOG2 = 0.5f;
    private static final float LABEL_SUPPORT_LOG2 = 1.1f;

    // Calendar-axis bump curves. Ideals are wide: full "Dec 1, 2013"-style
    // labels need room, so quarters dominate at a ~1-year window and years
    // take over after a zoom-out, with months/quarters fading to faint minors.
    private static final float DATE_GRID_IDEAL_SPACING_PX = 340f;
    private static final float DATE_GRID_PLATEAU_LOG2 = 0.6f;
    private static final float DATE_GRID_SUPPORT_LOG2 = 1.5f;
    private static final float DATE_LABEL_IDEAL_SPACING_PX = 430f;
    private static final float DATE_LABEL_PLATEAU_LOG2 = 0.6f;
    private static final float DATE_LABEL_SUPPORT_LOG2 = 1.5f;

    // Steps finer than the configured base step only fade in once the base
    // family itself has become sparse (px spacing of the base family).
    private static final float SUB_BASE_GRID_RAMP_START_PX = 110f;
    private static final float SUB_BASE_GRID_RAMP_END_PX = 190f;
    private static final float SUB_BASE_LABEL_RAMP_START_PX = 130f;
    private static final float SUB_BASE_LABEL_RAMP_END_PX = 230f;

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
                topGridOverscan, yLabelFormatter);

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
                        rightGridOverscan, xLabelFormatter);

        drawVerticalGrid(xTicks);
        drawHorizontalGrid(yTicks);
        if (showAxisBackgroundStrips) {
            drawLabelBands();
        }
        drawAxes();
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

    /** Overrides the default (Computer Modern) axis label font. */
    public void setLabelFont(PFont labelFont) {
        this.font = Objects.requireNonNull(labelFont, "labelFont");
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
            DoubleFunction<String> formatter
    ) {
        double span = max - min;
        if (baseStep <= EPSILON || span <= EPSILON || pixelSpan <= 0f) {
            return new ArrayList<>();
        }

        List<double[]> families = new ArrayList<>(); // {step, gridAlpha, labelAlpha}
        int firstIndex = niceStepFloorIndex(baseStep, span * 8.0 / pixelSpan) - 1;
        int lastIndex = niceStepFloorIndex(baseStep, span * 1500.0 / pixelSpan) + 2;

        float baseSpacingPx = (float) (pixelSpan * (baseStep / span));
        float subBaseGrid = smoothstep(clamp01((baseSpacingPx - SUB_BASE_GRID_RAMP_START_PX)
                / (SUB_BASE_GRID_RAMP_END_PX - SUB_BASE_GRID_RAMP_START_PX)));
        float subBaseLabel = smoothstep(clamp01((baseSpacingPx - SUB_BASE_LABEL_RAMP_START_PX)
                / (SUB_BASE_LABEL_RAMP_END_PX - SUB_BASE_LABEL_RAMP_START_PX)));

        int bestLabelIndex = Integer.MIN_VALUE;
        float bestLabelAlpha = 0f;
        for (int index = firstIndex; index <= lastIndex; index++) {
            double step = niceStep(baseStep, index);
            if (step <= EPSILON) {
                continue;
            }
            float spacingPx = (float) (pixelSpan * (step / span));
            float gridAlpha = bumpAlpha(spacingPx, GRID_IDEAL_SPACING_PX, GRID_PLATEAU_LOG2, GRID_SUPPORT_LOG2);
            float labelAlpha = bumpAlpha(spacingPx, LABEL_IDEAL_SPACING_PX, LABEL_PLATEAU_LOG2, LABEL_SUPPORT_LOG2);
            if (index < 0) {
                gridAlpha *= subBaseGrid;
                labelAlpha *= subBaseLabel;
            }
            if (labelAlpha > bestLabelAlpha) {
                bestLabelAlpha = labelAlpha;
                bestLabelIndex = index;
            }
            if (Math.max(gridAlpha, labelAlpha) > ALPHA_EPSILON) {
                families.add(new double[]{step, gridAlpha, labelAlpha});
            }
        }

        // Never leave an axis unlabeled: if everything faded out (possible at
        // extreme zoom-in with sub-base suppression), force the best family.
        if (bestLabelAlpha <= ALPHA_EPSILON && bestLabelIndex != Integer.MIN_VALUE) {
            families.add(new double[]{niceStep(baseStep, bestLabelIndex), 1.0, 1.0});
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

        TreeMap<Long, Tick> merged = new TreeMap<>();
        for (CalendarFamily family : CalendarFamily.values()) {
            float spacingPx = (float) (plotWidth * (family.averageDays / span));
            float gridAlpha = bumpAlpha(spacingPx, DATE_GRID_IDEAL_SPACING_PX,
                    DATE_GRID_PLATEAU_LOG2, DATE_GRID_SUPPORT_LOG2);
            float labelAlpha = bumpAlpha(spacingPx, DATE_LABEL_IDEAL_SPACING_PX,
                    DATE_LABEL_PLATEAU_LOG2, DATE_LABEL_SUPPORT_LOG2);
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

    // Full reference-style date labels: "Dec 1, 2013", "Mar 1, 2014", ...
    private static String calendarLabel(LocalDate date) {
        String month = date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return month + " " + date.getDayOfMonth() + ", " + date.getYear();
    }

    /**
     * Continuous fade curve: full alpha while the family's pixel spacing is
     * within {@code plateauLog2} octaves of the ideal spacing, easing to zero
     * at {@code supportLog2} octaves (both toward cramped and toward sparse).
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
        float fadeWidthPx = xBaseGapWidthPx();
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
            if (y > plotTop + plotHeight + 1f) {
                continue;
            }
            applyGridStroke(t, 1f);
            p.line(plotLeft, y, plotRight + rightGridOverscan, y);
        }
    }

    /** Gridline style is a continuous function of the tick's fade state. */
    private void applyGridStroke(float t, float extraFade) {
        p.strokeWeight(interpolate(minorGridStroke, majorGridStroke, t));
        float brightness = interpolate(26f, 46f, t);
        float alpha = interpolate(20f, 44f, t) * clamp01(t / 0.3f) * extraFade;
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

    private void drawLabelBands() {
        float plotRight = plotLeft + plotWidth;
        float plotBottom = plotTop + plotHeight;
        float leftBandLeft = viewportLeft;
        float leftBandRight = plotLeft - LABEL_BAND_GAP;
        float bottomBandTop = plotBottom + LABEL_BAND_GAP;
        float bottomBandBottom = p.height / 2f;

        p.noStroke();
        p.fill(labelBackgroundColor);
        p.rect(leftBandLeft, plotTop - topGridOverscan, leftBandRight, plotBottom);
        p.rect(plotLeft, bottomBandTop, plotRight + rightGridOverscan, bottomBandBottom);
    }

    private void drawYLabels(List<Tick> ticks) {
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
            if (y > plotTop + plotHeight + 1f || isOnWorldXAxis(y)) {
                continue;
            }
            applyLabelStyle(t, 1f);
            p.text(tick.label, yLabelX(), y);
        }
    }

    private void drawXLabels(List<Tick> ticks) {
        float labelY = plotTop + plotHeight + xLabelInset;
        float plotRight = plotLeft + plotWidth;
        float fadeWidthPx = xBaseGapWidthPx();

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
        float alpha = 100f * (float) Math.pow(t, 1.5) * extraFade;
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

    private float railCollapseProgress() {
        if (xMajorStep <= EPSILON) {
            return 0f;
        }
        float progress = (float) ((xMin - xAnchor) / xMajorStep);
        return smoothstep(clamp01(progress));
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

    private float leftVerticalFadeFactor(float x, float fadeWidthPx) {
        if (fadeWidthPx <= 0f) {
            return 1f;
        }
        if (x <= plotLeft) {
            return 0f;
        }
        if (x >= plotLeft + fadeWidthPx) {
            return 1f;
        }
        return (x - plotLeft) / fadeWidthPx;
    }

    private float yAxisFadeFactor(float x, float fadeWidthPx) {
        if (currentCollapseProgress <= 0f) {
            return 1f;
        }
        if (fadeWidthPx <= 0f) {
            return 1f;
        }
        if (x >= plotLeft) {
            return 1f;
        }
        if (x <= plotLeft - fadeWidthPx) {
            return 0f;
        }
        return 1f - (plotLeft - x) / fadeWidthPx;
    }

    private void drawWorldYAxis(float plotBottom) {
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

    /** Suppress the y label that sits exactly on the (visible) ground axis. */
    private boolean isOnWorldXAxis(float y) {
        float axisY = domainToCanvasY(yAnchor);
        return axisY <= plotTop + plotHeight + 1f && Math.abs(y - axisY) < 1f;
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
