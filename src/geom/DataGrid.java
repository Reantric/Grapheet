package geom;

import core.Applet;
import processing.core.PFont;
import storage.Color;
import storage.ColorType;

import java.text.DecimalFormat;
import java.util.Objects;
import java.util.function.DoubleFunction;

public final class DataGrid {
    private static final double EPSILON = 1e-6;
    private static final float LABEL_BAND_GAP = 14f;
    private static final float SLIM_RAIL_PADDING = 16f;

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

    private int xMinorDivisions = 2;
    private int yMinorDivisions = 2;

    private DoubleFunction<String> xLabelFormatter = this::formatDefaultLabel;
    private DoubleFunction<String> yLabelFormatter = this::formatDefaultLabel;

    private final Color axisColor = new Color(ColorType.WHITE);
    private final Color majorGridColor = new Color(0, 0, 46, 44);
    private final Color minorGridColor = new Color(0, 0, 28, 28);
    private final Color labelColor = new Color(ColorType.WHITE);
    private final Color minorLabelColor = new Color(0, 0, 72, 42);
    private final Color labelBackgroundColor = new Color(0, 0, 0, 92);

    private float axisStroke = 5f;
    private float majorGridStroke = 2.75f;
    private float minorGridStroke = 1.5f;
    private float majorLabelSize = 34f;
    private float minorLabelSize = 30f;
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
        updatePlotArea();
        if (plotWidth <= 0 || plotHeight <= 0 || xMax <= xMin || yMax <= yMin) {
            return;
        }

        drawMinorGrid();
        drawMajorGrid();
        if (showAxisBackgroundStrips) {
            drawLabelBands();
        }
        drawAxes();
        if (showLabels) {
            drawLabels();
        }
    }

    public void setDomain(double xMin, double xMax, double yMin, double yMax) {
        if (xMax <= xMin) {
            throw new IllegalArgumentException("xMax must be greater than xMin");
        }
        if (yMax <= yMin) {
            throw new IllegalArgumentException("yMax must be greater than yMin");
        }
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
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

    public void setMinorDivisions(int xMinorDivisions, int yMinorDivisions) {
        if (xMinorDivisions < 1 || yMinorDivisions < 1) {
            throw new IllegalArgumentException("Minor divisions must be at least 1");
        }
        this.xMinorDivisions = xMinorDivisions;
        this.yMinorDivisions = yMinorDivisions;
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

    public float domainToCanvasX(double value) {
        double t = (value - xMin) / (xMax - xMin);
        return plotLeft + (float) (t * plotWidth);
    }

    public float domainToCanvasY(double value) {
        double t = (value - yMin) / (yMax - yMin);
        return plotTop + plotHeight - (float) (t * plotHeight);
    }

    private void updatePlotArea() {
        viewportLeft = -p.width / 2f;
        float viewportTop = -p.height / 2f;
        plotTop = viewportTop + topInset;
        plotHeight = p.height - topInset - bottomInset;
        currentCollapseProgress = railCollapseProgress();
        float currentLeftRailWidth = interpolate(leftInset, slimRailWidth(), currentCollapseProgress);
        plotLeft = viewportLeft + currentLeftRailWidth;
        plotWidth = p.width - currentLeftRailWidth - rightInset;
    }

    private void drawMinorGrid() {
        if (!showMinorGrid) {
            return;
        }

        drawVerticalFamily(xMajorStep / xMinorDivisions, minorGridColor, minorGridStroke, xMajorStep);
        drawHorizontalFamily(yMajorStep / yMinorDivisions, minorGridColor, minorGridStroke, yMajorStep);
    }

    private void drawMajorGrid() {
        drawVerticalFamily(xMajorStep, majorGridColor, majorGridStroke, Double.NaN);
        drawHorizontalFamily(yMajorStep, majorGridColor, majorGridStroke, Double.NaN);
    }

    private void drawVerticalFamily(double step, Color color, float strokeWeight, double majorStep) {
        if (step <= 0) {
            return;
        }

        p.strokeWeight(strokeWeight);
        p.noFill();

        float plotRight = plotLeft + plotWidth;
        float fadeWidthPx = xMajorGapWidthPx();
        double first = firstLineAtOrAfter(xMin, xAnchor, step);
        for (double value = first; ; value += step) {
            if (isAxisLineValue(value)) {
                continue;
            }
            if (!Double.isNaN(majorStep) && isAligned(value, xAnchor, majorStep)) {
                continue;
            }
            float x = domainToCanvasX(value);
            if (x > plotRight + rightGridOverscan + 1f) {
                break;
            }
            if (x < plotLeft - 1f) {
                continue;
            }
            strokeWithAlpha(color, leftVerticalFadeFactor(x, fadeWidthPx));
            p.line(x, plotTop - topGridOverscan, x, plotTop + plotHeight);
        }
    }

    private void drawHorizontalFamily(double step, Color color, float strokeWeight, double majorStep) {
        if (step <= 0) {
            return;
        }

        p.stroke(color);
        p.strokeWeight(strokeWeight);
        p.noFill();

        float plotRight = plotLeft + plotWidth;
        double first = firstLineAtOrAfter(yMin, yAnchor, step);
        for (double value = first; ; value += step) {
            if (!Double.isNaN(majorStep) && isAligned(value, yAnchor, majorStep)) {
                continue;
            }
            float y = domainToCanvasY(value);
            if (y < plotTop - topGridOverscan - 1f) {
                break;
            }
            p.line(plotLeft, y, plotRight + rightGridOverscan, y);
        }
    }

    private void drawAxes() {
        float plotRight = plotLeft + plotWidth;
        float plotBottom = plotTop + plotHeight;
        p.strokeWeight(axisStroke);
        drawWorldYAxis(plotBottom);
        p.stroke(axisColor);
        p.line(plotLeft, plotBottom, plotRight + rightGridOverscan, plotBottom);
    }

    private void drawLabelBands() {
        float plotRight = plotLeft + plotWidth;
        float plotBottom = plotTop + plotHeight;
        float leftBandLeft = viewportLeft;
        float leftBandRight = leftBandRight();
        float bottomBandTop = plotBottom + LABEL_BAND_GAP;
        float bottomBandBottom = p.height / 2f;

        p.noStroke();
        p.fill(labelBackgroundColor);
        p.rect(leftBandLeft, plotTop - topGridOverscan, leftBandRight, plotBottom);
        p.rect(plotLeft, bottomBandTop, plotRight + rightGridOverscan, bottomBandBottom);
    }

    private void drawLabels() {
        ensureFont();
        p.textFont(font);
        p.noStroke();

        drawMinorYLabels();
        drawMinorXLabels();
        drawYLabels();
        drawXLabels();
    }

    private void drawYLabels() {
        p.textSize(majorLabelSize);
        p.fill(labelColor);
        p.textAlign(Applet.RIGHT, Applet.CENTER);
        double first = firstLineAtOrAfter(yMin, yAnchor, yMajorStep);
        for (double value = first; ; value += yMajorStep) {
            float y = domainToCanvasY(value);
            if (y < plotTop - topGridOverscan - 1f) {
                break;
            }
            if (y > plotTop + plotHeight + 1f) {
                continue;
            }
            if (isBottomAxisLabel(y)) {
                continue;
            }
            p.text(yLabelFormatter.apply(cleanZero(value)), yLabelX(), y);
        }
    }

    private void drawXLabels() {
        drawXLabelsForStep(xMajorStep, Double.NaN, majorLabelSize, labelColor);
    }

    private void drawMinorYLabels() {
        if (!showMinorGrid) {
            return;
        }

        p.textSize(minorLabelSize);
        p.fill(minorLabelColor);
        p.textAlign(Applet.RIGHT, Applet.CENTER);
        double step = yMajorStep / yMinorDivisions;
        double first = firstLineAtOrAfter(yMin, yAnchor, step);
        for (double value = first; ; value += step) {
            if (isAligned(value, yAnchor, yMajorStep)) {
                continue;
            }
            float y = domainToCanvasY(value);
            if (y < plotTop - topGridOverscan - 1f) {
                break;
            }
            if (y > plotTop + plotHeight + 1f || isBottomAxisLabel(y)) {
                continue;
            }
            p.text(yLabelFormatter.apply(cleanZero(value)), yLabelX(), y);
        }
    }

    private void drawMinorXLabels() {
        if (!showMinorGrid) {
            return;
        }

        drawXLabelsForStep(xMajorStep / xMinorDivisions, xMajorStep, minorLabelSize, minorLabelColor);
    }

    private void ensureFont() {
        if (font == null) {
            font = p.createFont("src/data/cmunbmr.ttf", 150, true);
        }
    }

    private String formatDefaultLabel(double value) {
        return numberFormat.format(cleanZero(value));
    }

    private float xMajorGapWidthPx() {
        if (xMajorStep <= 0 || xMax <= xMin) {
            return 1f;
        }
        float width = (float) (plotWidth * (xMajorStep / (xMax - xMin)));
        return Math.max(1f, width);
    }

    private float railCollapseProgress() {
        if (xMajorStep <= EPSILON) {
            return 0f;
        }
        float progress = (float) ((xMin - xAnchor) / xMajorStep);
        return smoothstep(clamp01(progress));
    }

    private float slimRailWidth() {
        float minRailWidth = yLabelInset + SLIM_RAIL_PADDING;
        if (!showLabels) {
            return minRailWidth;
        }

        ensureFont();
        p.textFont(font);

        float maxLabelWidth = maxVisibleYLabelWidth(yMajorStep, Double.NaN, majorLabelSize);
        if (showMinorGrid) {
            maxLabelWidth = Math.max(
                    maxLabelWidth,
                    maxVisibleYLabelWidth(yMajorStep / yMinorDivisions, yMajorStep, minorLabelSize)
            );
        }
        return Math.max(minRailWidth, maxLabelWidth + yLabelInset + SLIM_RAIL_PADDING);
    }

    private float maxVisibleYLabelWidth(double step, double skipAlignedStep, float textSize) {
        if (step <= 0) {
            return 0f;
        }

        p.textSize(textSize);
        float maxWidth = 0f;
        double first = firstLineAtOrAfter(yMin, yAnchor, step);
        for (double value = first; ; value += step) {
            if (!Double.isNaN(skipAlignedStep) && isAligned(value, yAnchor, skipAlignedStep)) {
                continue;
            }
            float y = domainToCanvasY(value);
            if (y < plotTop - topGridOverscan - 1f) {
                break;
            }
            if (y > plotTop + plotHeight + 1f || isBottomAxisLabel(y)) {
                continue;
            }
            maxWidth = Math.max(maxWidth, p.textWidth(yLabelFormatter.apply(cleanZero(value))));
        }
        return maxWidth;
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

        float fadeEnd = leftBandRight();
        float fadeLead = Math.max(12f, Math.min(48f, fadeWidthPx * 0.35f));
        float fadeStart = plotLeft + fadeLead;

        if (x >= fadeStart) {
            return 1f;
        }
        if (x <= fadeEnd) {
            return 0f;
        }
        return (x - fadeEnd) / (fadeStart - fadeEnd);
    }

    private void drawWorldYAxis(float plotBottom) {
        float x = domainToCanvasX(xAnchor);
        float alphaFactor = yAxisFadeFactor(x, xMajorGapWidthPx());
        if (alphaFactor <= 0f) {
            return;
        }
        strokeWithAlpha(axisColor, alphaFactor);
        p.line(x, plotTop - topGridOverscan, x, plotBottom);
    }

    private void drawXLabelsForStep(double step, double skipAlignedStep, float textSize, Color color) {
        if (step <= 0) {
            return;
        }

        p.textSize(textSize);
        p.textAlign(Applet.CENTER, Applet.CENTER);
        float labelY = plotTop + plotHeight + xLabelInset;
        float plotRight = plotLeft + plotWidth;
        float fadeWidthPx = xMajorGapWidthPx();
        double first = firstLineAtOrAfter(xMin, xAnchor, step);
        for (double value = first; ; value += step) {
            if (!Double.isNaN(skipAlignedStep) && isAligned(value, xAnchor, skipAlignedStep)) {
                continue;
            }
            float x = domainToCanvasX(value);
            if (x > plotRight + rightGridOverscan + 1f) {
                break;
            }
            if (!shouldDrawXLabel(value, x)) {
                continue;
            }

            float alphaFactor = xLabelFadeFactor(value, x, fadeWidthPx);
            if (alphaFactor <= 0f) {
                continue;
            }

            fillWithAlpha(color, alphaFactor);
            p.text(xLabelFormatter.apply(cleanZero(value)), x, labelY);
        }
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
                color.getAlpha().getValue() * Math.max(0f, Math.min(1f, alphaFactor))
        );
    }

    private void fillWithAlpha(Color color, float alphaFactor) {
        p.fill(
                color.getHue().getValue(),
                color.getSaturation().getValue(),
                color.getBrightness().getValue(),
                color.getAlpha().getValue() * Math.max(0f, Math.min(1f, alphaFactor))
        );
    }

    private double firstLineAtOrAfter(double min, double anchor, double step) {
        double index = Math.ceil((min - anchor - EPSILON) / step);
        return anchor + index * step;
    }

    private boolean isBottomAxisLabel(float y) {
        return Math.abs(y - (plotTop + plotHeight)) < 1f;
    }

    private boolean isLeftAxisLabel(float x) {
        return Math.abs(x - plotLeft) < 1f;
    }

    private boolean isAxisLineValue(double value) {
        return Math.abs(cleanZero(value - xAnchor)) < 1e-4;
    }

    private float leftBandRight() {
        return plotLeft - LABEL_BAND_GAP;
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

    // Keep each line family phase-locked to a shared anchor while the viewport moves.
    private boolean isAligned(double value, double anchor, double step) {
        double offset = (value - anchor) / step;
        return Math.abs(offset - Math.rint(offset)) < 1e-4;
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
}
