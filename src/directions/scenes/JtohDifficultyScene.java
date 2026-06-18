package directions.scenes;

import core.Applet;
import directions.engine.Action;
import directions.engine.Actions;
import directions.engine.Nodes;
import directions.engine.Scene;
import directions.engine.SceneContext;
import geom.DataGrid;
import geom.ValueBand;
import processing.core.PFont;
import processing.core.PImage;
import storage.Color;
import util.Pchip;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Animated race chart of three real JToH (Juke's Towers of Hell) players over
 * time, with the difficulty-tier colour bands (Easy .. Extreme, ratings 1..9)
 * painted behind the lines.
 *
 * <p>Each line is a player's <em>personal-record</em> progression: the running
 * maximum of the difficulty they have cleared, so the line only ever climbs.
 * The PR knots are PCHIP-interpolated (smooth, overshoot-free) and the tail is
 * held flat out to the global end date. A player's hardest tower can exceed the
 * top tier (Extreme = 9); the open-ended Extreme band absorbs anything above 9.
 *
 * <p>Data comes from {@code src/data/jtoh/completions.csv}
 * (player,color,datetime,code,difficulty — one row per tower completion,
 * produced by {@code tools/generate_jtoh_pr_data.py} from the three workbooks).
 * Every completion also feeds the right-column LEDGER: a feed of
 * "{@code Username beat <code>}" rows tinted by the tower's difficulty tier,
 * newest on top, that pushes the oldest rows off the bottom as new ones land.
 * The race head and its labels are kept left of the ledger gutter.
 *
 * <p>Unlike the Codeforces scene, the difficulty tiers are evenly spaced
 * integers, so the y-axis uses the natural 1/2/5 tick ladder (a major step of
 * 1, exactly like the CS2 race) instead of pinned semantic ticks — the integer
 * gridlines fall on the tier boundaries. The bands show only the difficulty
 * numbers on the axis; the colours carry the tier identity.
 *
 * <p>Timeline: one simulated day per {@code -DmsPerDay} milliseconds
 * (default {@value #DEFAULT_MS_PER_DAY}). The camera follows the head of the
 * race through a one-year window, then eases out to the full date range at
 * the end with the race head pinned in place. Top left shows the leader
 * header, top right the simulated date.
 */
public final class JtohDifficultyScene extends Scene {
    private static final String DATA_PATH = "src/data/jtoh/completions.csv";
    /** 85ms/day over the 2023..mid-2026 dataset lands the video at ~1:35. */
    private static final double DEFAULT_MS_PER_DAY = 85;
    private static final double WINDOW_DAYS = 365;
    /** Upper bound for the follow fraction — the actual fraction is derived
     *  every frame from the measured widest label (badge + name + difficulty)
     *  so the whole label block always fits between the head dots and the
     *  viewport edge; a fixed fraction left the column clamped onto the
     *  line tails whenever a long name entered the race. */
    private static final double MAX_FOLLOW_RATIO = 0.83;
    private static final float LABEL_MARGIN_EXTRA_PX = 56f;
    private static final double FINAL_ZOOM_DELAY = 0.6;
    private static final double FINAL_ZOOM_DURATION = 5.0;
    private static final double END_HOLD_SECONDS = 4.0;
    /** Difficulty tiers are unit-spaced integers, so the natural tick ladder
     *  steps by 1 — the integer gridlines land on the tier boundaries. */
    private static final double Y_BASE_STEP = 1;
    private static final double Y_MIN_SPAN = 4.0;
    private static final double Y_FIT_SAMPLE_DAYS = 5.0;
    private static final float Y_FIT_EASE_RATE = 2.2f;
    /**
     * Age-weighted y-fit: data this close to "now" gets full framing
     * weight; older extremes decay toward the recent range with the time
     * constant below, so the camera follows the CURRENT race instead of
     * holding the window open for a spike that happened months ago (it
     * arcs out through the top of the frame as it ages). The discount
     * fades off during the final zoom-out, which must frame everything.
     */
    private static final double Y_FIT_RECENT_DAYS = 45;
    private static final double Y_FIT_AGE_DECAY_DAYS = 75;
    private static final float LABEL_EASE_RATE = 6f;
    private static final float LABEL_TEXT_SIZE = 38f;
    private static final float LABEL_MIN_GAP_PX = 46f;
    /** Horizontal gap between a head dot and the start of its label block. */
    private static final float LABEL_DOT_GAP_PX = 34f;
    /** Square box the head-label badge is fitted into. */
    private static final float LABEL_LOGO_BOX_PX = 36f;
    private static final float LABEL_LOGO_SPACE_PX = LABEL_LOGO_BOX_PX + 9f;
    private static final float LINE_STROKE_PX = 5.2f;
    private static final float HEAD_DOT_PX = 15f;
    private static final float RETIRE_LINE_FADE_DAYS = 60f;
    private static final float RETIRE_LABEL_FADE_SECONDS = 1.0f;
    /**
     * Tracks whose data ends within this many days of "now" still count for
     * ranking. Without it, climbers whose last knot lands a few days
     * before the global end of the dataset would all "retire" on the final
     * frames and hand #1 to whoever happens to have the latest knot.
     */
    private static final double RANK_GRACE_DAYS = 45;
    /**
     * A track counts as part of the race FRONT (shared label column, label
     * queue) only while its data reaches within this many days of "now".
     * The wider RANK_GRACE_DAYS is for ranking only — using it
     * for the column made a freshly-retired line drag the whole label column
     * back onto the dots.
     */
    private static final double FRONT_TOLERANCE_DAYS = 10;
    /**
     * A label only moves down/up the queue once the difficulty difference
     * exceeds this margin — difficulty noise around a tie must not flap the
     * queue order every few frames (the labels of a flapping pair both
     * converge on the crossing point and render superimposed).
     */
    private static final double RANK_SWAP_HYSTERESIS = 0.012;
    /**
     * After a label trades places it cannot trade again for this long
     * unless the difficulty gap is decisive ({@link #RANK_SWAP_FORCE}). The
     * margin alone cannot stop near-ties whose noise swings beyond it
     * within a fraction of a second of video — the pair's targets then
     * exchange faster than the easing can follow and both labels converge
     * superimposed at the crossing midpoint.
     */
    private static final double SWAP_COOLDOWN_SECONDS = 1.2;
    private static final double RANK_SWAP_FORCE = 0.05;
    /** Alpha of the translucent panels behind the HUD blocks (2DGP look). */
    private static final float HUD_PANEL_ALPHA = 40f;
    private static final DateTimeFormatter DATE_READOUT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    // ---- Ledger: a right-column feed of recent tower completions ----
    /** Reserved right gutter for the ledger. The race head and its labels stay
     *  left of it; the plot (and its bands) still span the full canvas behind. */
    private static final float LEDGER_WIDTH = 400f;
    private static final int LEDGER_MAX_ROWS = 11;
    private static final float LEDGER_ROW_H = 46f;
    private static final float LEDGER_TEXT_SIZE = 30f;
    /** Panel top, measured down from the viewport top — sits below the Current
     *  Date panel (which ends ~136px down) with a clear gap. */
    private static final float LEDGER_PANEL_TOP = 210f;
    private static final float LEDGER_Y_EASE = 11f;
    private static final float LEDGER_FADE_IN = 9f;
    private static final float LEDGER_FADE_OUT = 7f;
    /** New rows slide in from the right (px) as they fade in. */
    private static final float LEDGER_ENTER_SLIDE = 55f;
    private static final float LEDGER_ENTER_EASE = 12f;
    /** Darker than the other HUD panels so colour-coded text keeps contrast no
     *  matter which band sits behind it. */
    private static final float LEDGER_PANEL_ALPHA = 62f;
    /** Floors so the dark tiers (maroon Challenging, near-black Intense) stay
     *  legible: lift brightness, cap saturation. */
    private static final float LEDGER_TEXT_MIN_BRI = 88f;
    private static final float LEDGER_TEXT_MAX_SAT = 82f;

    // ---- Bullet-time: slow + zoom in on bursts of activity ----
    /** A merged activity window must hold at least this many completions to
     *  earn the slow-mo treatment — keeps it to the few dramatic spikes. */
    private static final int BURST_MIN_EVENTS = 20;
    /** Local-density test used to flag burst cores. */
    private static final int BURST_DENSITY_COUNT = 8;
    private static final double BURST_DENSITY_WINDOW = 7;   // days
    /** Sim-day ramps before/after the burst for the dive-in / pull-out. */
    private static final double BURST_LEAD = 30;
    private static final double BURST_TAIL = 30;
    /** Peak effect at full intensity: playback this many times slower, and the
     *  x-window squeezed from WINDOW_DAYS down to this many days. */
    private static final double BURST_MAX_SLOW = 25.0;
    private static final double BURST_SPAN = 38;
    /** Smallest qualifying burst still gets this fraction of the full effect. */
    private static final double BURST_MIN_INTENSITY = 0.4;

    private final DataGrid grid;
    private final List<ValueBand> tierBands = buildTierBands();
    private final List<Track> tracks;
    /** Every completion, sorted by day — drives the ledger feed. */
    private final List<Event> events;
    /** Precomputed activity spikes that get the slow-mo + zoom treatment. */
    private final List<Burst> bursts;
    private final LocalDate dayZero;
    private final double endDay;
    private final double msPerDay;

    /** Ledger entries, newest at index 0; those past {@link #LEDGER_MAX_ROWS}
     *  fade out as they are pushed off the bottom. */
    private final List<LedgerEntry> ledger = new ArrayList<>();
    /** Index of the next completion (in {@link #events}) not yet shown. */
    private int eventCursor;

    private PFont font;

    private double tDay;
    private double sceneSeconds;
    /** Current bullet-time strength (0..1); drives the slow-mo clock readout. */
    private double currentDrama;
    private double visibleXMin;
    private double visibleXSpan = WINDOW_DAYS;
    private double yShownMin = 1;
    private double yShownMax = 6;

    private Track leader;
    private double leaderSinceDay;
    /** Persistent head-label queue order (highest difficulty first). */
    private final List<Track> labelOrder = new ArrayList<>();

    private boolean zoomOutStarted;
    private double zoomOutElapsed;
    private double zoomOutStartXMin;
    private double zoomOutStartXSpan;
    private double zoomOutHeadFraction = MAX_FOLLOW_RATIO;
    private double endHoldElapsed;
    /** Pixels needed right of the head dots for the widest label block,
     *  measured in drawHeadLabels and smoothed. */
    private float followMarginPx = 320f;

    public JtohDifficultyScene(Applet p) {
        super(p);
        msPerDay = readMsPerDay();
        Loaded data = loadData(DATA_PATH);
        tracks = data.tracks;
        events = data.events;
        bursts = data.bursts;
        dayZero = data.dayZero;
        endDay = data.endDay;

        grid = new DataGrid(applet());
        grid.setXCalendarAxis(dayZero);
        grid.setAnchor(0, 0);
        // Same setup as the CS2 race: hand the grid a base major step and let
        // its adaptive 1/2/5 ladder pick the cadence. The base is 1 here (the
        // tier width) so the ladder's gridlines land on the integer tier
        // boundaries; nothing else about the tick/minor-grid density is touched.
        grid.setYMajorStep(Y_BASE_STEP);
        grid.setYLabelFormatter(value -> String.format(Locale.ENGLISH, "%.0f", value));
        grid.setValueBands(tierBands);
        // Tier names off — the axis shows just the difficulty numbers; the band
        // colours carry the tier identity.
        grid.showValueBandLabels(false);
        grid.setDomain(0, WINDOW_DAYS, yShownMin, yShownMax);
        // The follow camera is one-way: once the y-axis has collapsed away it
        // must not reappear during the final zoom-out.
        grid.setRailCollapseRatchet(true);
    }

    @Override
    protected void onReset() {
        tDay = 0;
        sceneSeconds = 0;
        visibleXMin = 0;
        visibleXSpan = WINDOW_DAYS;
        yShownMin = 1;
        yShownMax = 6;
        leader = null;
        leaderSinceDay = 0;
        zoomOutStarted = false;
        zoomOutElapsed = 0;
        zoomOutHeadFraction = MAX_FOLLOW_RATIO;
        endHoldElapsed = 0;
        followMarginPx = 320f;
        currentDrama = 0;
        labelOrder.clear();
        ledger.clear();
        eventCursor = 0;
        for (Track track : tracks) {
            track.labelInitialised = false;
            track.labelAlpha = 0f;
            track.labelOffset = 0f;
            track.lastQueueSwapSeconds = Double.NEGATIVE_INFINITY;
            track.strokeBoost = 0f;
        }
        grid.setDomain(0, WINDOW_DAYS, yShownMin, yShownMax);
        grid.setRailCollapseRatchet(true);
    }

    @Override
    protected Action build() {
        addUpdater(this::updateTimeline);
        // ensureFont() hands the grid its Lato label font; run it before the
        // grid's first render or frame 1 draws the axis labels in the grid's
        // own fallback font (Computer Modern) and pops to Lato on frame 2.
        addNode(Nodes.of(() -> {
            ensureFont();
            grid.render();
        }));
        addNode(Nodes.of(this::drawSeries));
        addNode(this::drawHeadLabels);
        addNode(Nodes.of(this::drawLeaderHeader));
        addNode(Nodes.of(this::drawDateReadout));
        addNode(Nodes.of(this::drawLedger));

        return Actions.update(this::isFinished);
    }

    private boolean isFinished() {
        return zoomOutStarted
                && zoomOutElapsed >= FINAL_ZOOM_DELAY + FINAL_ZOOM_DURATION
                && endHoldElapsed >= END_HOLD_SECONDS;
    }

    // ------------------------------------------------------------------
    // Simulation / camera
    // ------------------------------------------------------------------

    private void updateTimeline(SceneContext ctx) {
        double dt = ctx.dt();
        sceneSeconds += dt;
        grid.setLabelFadeTimeStep(dt);

        // Bullet-time: near an activity burst, advance the sim slower and
        // squeeze the x-window so the flurry is readable; ease back after.
        double drama = dramaAt(tDay);
        currentDrama = drama;
        double rateScale = 1.0 + drama * (BURST_MAX_SLOW - 1.0);
        tDay = Math.min(endDay, tDay + dt * 1000.0 / (msPerDay * rateScale));

        if (tDay >= endDay) {
            updateFinalZoom(dt);
        } else {
            double ratio = followRatio();
            visibleXSpan = WINDOW_DAYS - drama * (WINDOW_DAYS - BURST_SPAN);
            double followStart = visibleXMin + visibleXSpan * ratio;
            if (drama > 0.001) {
                // Pin the head while zoomed: the span changes both ways, so the
                // one-way follow ratchet can't keep the head in place here.
                visibleXMin = tDay - visibleXSpan * ratio;
            } else if (tDay > followStart) {
                visibleXMin = tDay - visibleXSpan * ratio;
            }
        }
        grid.setXRange(visibleXMin, visibleXMin + visibleXSpan);

        updateRanking(dt);
        updateYFit(dt);
        updateLedger(dt);
    }

    /** Drama envelope at a sim-day: 0 normally, ramping to a burst's intensity
     *  over BURST_LEAD before it and BURST_TAIL after, held at full inside. */
    private double dramaAt(double day) {
        if (zoomOutStarted) {
            return 0;
        }
        double best = 0;
        for (Burst b : bursts) {
            double ramp;
            if (day < b.startDay - BURST_LEAD || day > b.endDay + BURST_TAIL) {
                continue;
            } else if (day < b.startDay) {
                ramp = smoothstep((day - (b.startDay - BURST_LEAD)) / BURST_LEAD);
            } else if (day <= b.endDay) {
                ramp = 1.0;
            } else {
                ramp = smoothstep(1.0 - (day - b.endDay) / BURST_TAIL);
            }
            best = Math.max(best, ramp * b.intensity);
        }
        return best;
    }

    /**
     * Head-dot fraction of the window that keeps the measured label block
     * (dot gap + badge + widest text) inside the viewport's right edge.
     */
    private double followRatio() {
        float plotWidth = grid.getPlotWidth();
        if (plotWidth <= 0f) {
            return MAX_FOLLOW_RATIO;
        }
        double headX = raceRightEdge() - followMarginPx;
        double ratio = (headX - grid.getPlotLeft()) / plotWidth;
        return Math.max(0.5, Math.min(MAX_FOLLOW_RATIO, ratio));
    }

    private void updateFinalZoom(double dt) {
        if (!zoomOutStarted) {
            zoomOutStarted = true;
            zoomOutElapsed = 0;
            // Final follow step at the handover: the forward phase scrolls the
            // window left every frame to hold the race head at the follow
            // fraction. The frame that clamps tDay to endDay advances the head
            // a few days without that scroll, so freezing the window here would
            // jut the head right. Apply the scroll the follow would have, so
            // the head is already at its follow fraction when it gets pinned.
            double ratio = followRatio();
            if (endDay > visibleXMin + visibleXSpan * ratio) {
                visibleXMin = endDay - visibleXSpan * ratio;
            }
            zoomOutStartXMin = visibleXMin;
            zoomOutStartXSpan = visibleXSpan;
            // Pin the race head to its current screen position: only the
            // history behind it compresses as the window stretches back to
            // day zero, so the dots and labels never move horizontally.
            zoomOutHeadFraction = clamp01((endDay - zoomOutStartXMin) / zoomOutStartXSpan);
            zoomOutHeadFraction = Math.max(0.5, Math.min(0.95, zoomOutHeadFraction));
        }
        zoomOutElapsed += dt;
        double progress = clamp01((zoomOutElapsed - FINAL_ZOOM_DELAY) / FINAL_ZOOM_DURATION);
        double eased = smoothstep(progress);
        visibleXMin = interpolate(zoomOutStartXMin, 0, eased);
        visibleXSpan = (endDay - visibleXMin) / zoomOutHeadFraction;
        if (progress >= 1.0) {
            endHoldElapsed += dt;
        }
    }

    private void updateRanking(double dt) {
        Track best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Track track : tracks) {
            if (!track.isActiveAt(tDay)) {
                continue;
            }
            double value = track.spline.value(tDay);
            if (value > bestValue) {
                bestValue = value;
                best = track;
            }
        }
        if (best != null && best != leader) {
            leader = best;
            leaderSinceDay = tDay;
        }
        if (leader != null && !leader.isActiveAt(tDay)) {
            leader = null;
        }
        for (Track track : tracks) {
            float target = track == leader ? 1f : 0f;
            track.strokeBoost = ease(track.strokeBoost, target, dt, 6f);
        }
    }

    private void updateYFit(double dt) {
        double xLo = visibleXMin;
        double xHi = visibleXMin + visibleXSpan;

        // Pass 1: full-weight range over recent data (and line heads).
        double recentMin = Double.POSITIVE_INFINITY;
        double recentMax = Double.NEGATIVE_INFINITY;
        double recentCutoff = tDay - Y_FIT_RECENT_DAYS;
        for (Track track : tracks) {
            double lo = Math.max(Math.max(track.firstDay, xLo), recentCutoff);
            double hi = Math.min(Math.min(tDay, track.lastDay), xHi);
            if (hi <= lo) {
                continue;
            }
            for (double d = lo; d <= hi + 1e-9; d += Y_FIT_SAMPLE_DAYS) {
                double v = track.spline.value(Math.min(d, hi));
                recentMin = Math.min(recentMin, v);
                recentMax = Math.max(recentMax, v);
            }
            double hiv = track.spline.value(hi);
            recentMin = Math.min(recentMin, hiv);
            recentMax = Math.max(recentMax, hiv);
        }

        // The age discount releases old extremes during the follow, but the
        // final zoom-out must frame the whole history again.
        double discountStrength = 1.0;
        if (zoomOutStarted) {
            discountStrength = 1.0 - smoothstep(clamp01(
                    (zoomOutElapsed - FINAL_ZOOM_DELAY) / FINAL_ZOOM_DURATION));
        }

        // Pass 2: all visible data, with extremes older than the recent
        // window decayed toward the recent range by age. Samples sit on an
        // ABSOLUTE day grid plus exact endpoints: a grid anchored to the
        // moving window edge shifts its sample set every frame, and the
        // resulting min/max wobble made the chart jitter during the zoom-out.
        boolean haveRecent = recentMin <= recentMax;
        double min = haveRecent ? recentMin : Double.POSITIVE_INFINITY;
        double max = haveRecent ? recentMax : Double.NEGATIVE_INFINITY;
        for (Track track : tracks) {
            double lo = Math.max(track.firstDay, xLo);
            double hi = Math.min(Math.min(tDay, track.lastDay), xHi);
            if (hi <= lo) {
                continue;
            }
            for (double endpoint : new double[]{lo, hi}) {
                double v = ageDiscounted(track.spline.value(endpoint), tDay - endpoint,
                        recentMin, recentMax, haveRecent ? discountStrength : 0.0);
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            double first = Math.ceil(lo / Y_FIT_SAMPLE_DAYS) * Y_FIT_SAMPLE_DAYS;
            for (double d = first; d <= hi + 1e-9; d += Y_FIT_SAMPLE_DAYS) {
                double v = ageDiscounted(track.spline.value(d), tDay - d,
                        recentMin, recentMax, haveRecent ? discountStrength : 0.0);
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        if (min > max) {
            return; // nothing visible yet — keep the current window
        }

        // Moderate top headroom: the leader visibly climbs before the
        // rescale catches up, but stays clear of the Current Date panel.
        double span = Math.max(Y_MIN_SPAN, (max - min) / 0.65);
        double targetMax = max + span * 0.18;
        double targetMin = targetMax - span;

        // Ease toward the fit every frame, including the first frame data
        // appears. Snapping the window onto the data there (the old behaviour)
        // lurched the whole band stack in a single frame, because the default
        // window starts far from where the JToH data begins; easing glides the
        // camera onto the race instead.
        yShownMin = ease((float) yShownMin, (float) targetMin, dt, Y_FIT_EASE_RATE);
        yShownMax = ease((float) yShownMax, (float) targetMax, dt, Y_FIT_EASE_RATE);
        grid.setYRange(yShownMin, yShownMax);
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private void drawSeries() {
        Applet p = applet();
        double xLo = grid.getXMin();
        double xHi = grid.getXMax();
        double stepDays = Math.max(0.4, (xHi - xLo) / Math.max(1f, grid.getPlotWidth()) * 2.0);
        // Lines may exit through the TOP of the frame (an age-discounted
        // historical spike arcs out as the camera re-frames the present) —
        // clamp far above the viewport, not at the plot edge, or the spike
        // flattens into a plateau. Soft floor below: lines may dip a little
        // past the plot (and below the grounded Easy floor) instead of
        // visibly flattening against the boundary.
        float plotTop = grid.getPlotTop() - 400f;
        float plotBottom = grid.getPlotTop() + grid.getPlotHeight() + 30f;

        for (Track track : tracks) {
            double headDay = Math.min(tDay, track.lastDay);
            double lo = Math.max(track.firstDay, xLo);
            double hi = Math.min(headDay, xHi);
            if (hi <= lo) {
                continue;
            }

            float lineAlpha = 100f;
            if (tDay > track.lastDay) {
                lineAlpha = 100f - 56f * clamp01F((float) ((tDay - track.lastDay) / RETIRE_LINE_FADE_DAYS));
            }

            p.noFill();
            strokeTrack(track, lineAlpha);
            p.strokeWeight(LINE_STROKE_PX + 1.8f * track.strokeBoost);
            p.beginShape();
            // Sample on an ABSOLUTE day grid (plus the exact endpoints) so the
            // polyline stays put as the window pans. Anchoring samples to the
            // moving left edge slid them across the fixed PCHIP every frame, so
            // sharp PR risers re-approximated differently each frame — the
            // "history changing" shimmer.
            p.vertex(grid.domainToCanvasX(lo),
                    clamp(grid.domainToCanvasY(track.spline.value(lo)), plotTop, plotBottom));
            for (double d = Math.ceil(lo / stepDays) * stepDays; d < hi; d += stepDays) {
                p.vertex(grid.domainToCanvasX(d),
                        clamp(grid.domainToCanvasY(track.spline.value(d)), plotTop, plotBottom));
            }
            p.vertex(grid.domainToCanvasX(hi),
                    clamp(grid.domainToCanvasY(track.spline.value(hi)), plotTop, plotBottom));
            p.endShape();

            if (headDay >= xLo && headDay <= xHi && tDay >= track.firstDay) {
                float hx = grid.domainToCanvasX(headDay);
                float hy = clamp(grid.domainToCanvasY(track.spline.value(headDay)), plotTop, plotBottom);
                // Reference style: plain white head dot.
                p.noStroke();
                p.fill(0, 0, 100, lineAlpha);
                p.circle(hx, hy, HEAD_DOT_PX + 3f * track.strokeBoost);
            }
        }
    }

    private void drawHeadLabels(SceneContext ctx) {
        Applet p = applet();
        ensureFont();
        p.textFont(font);

        float plotTop = grid.getPlotTop();
        float plotBottom = plotTop + grid.getPlotHeight();
        double dt = ctx.dt();

        // Collect visible labels with their natural (line-head) positions.
        // Only tracks whose data reaches "now" form the race FRONT — they
        // share one label column and the collision queue. A retired line's
        // label starts fading the moment it drops out of the front (waiting
        // for the wider RANK_GRACE_DAYS left it parked at full alpha in the
        // path of the advancing column), and keeps sitting at its own line
        // end so it never drags the column backward onto the dots.
        List<Track> visible = new ArrayList<>();
        List<Track> front = new ArrayList<>();
        for (Track track : tracks) {
            boolean inFront = tDay >= track.firstDay && tDay <= track.lastDay + FRONT_TOLERANCE_DAYS;
            float targetAlpha = inFront ? 1f : 0f;
            track.labelAlpha = ease(track.labelAlpha, targetAlpha,
                    dt, targetAlpha > track.labelAlpha ? 4f : 1f / (RETIRE_LABEL_FADE_SECONDS * 0.45f));
            double headDay = Math.min(tDay, track.lastDay);
            if (track.labelAlpha <= 0.02f || headDay < grid.getXMin() || tDay < track.firstDay) {
                continue;
            }
            track.labelTargetY = clamp(grid.domainToCanvasY(track.spline.value(headDay)),
                    plotTop + 26f, plotBottom - 22f);
            track.labelDotY = track.labelTargetY; // live head-dot Y, before stacking
            track.labelHeadX = grid.domainToCanvasX(Math.min(headDay, grid.getXMax()));
            visible.add(track);
            if (inFront) {
                front.add(track);
            }
        }

        // The queue: every visible label (front AND still-fading retirees —
        // a fresh retiree's line end sits right in the column's path) is
        // ranked and the TARGETS are spread to the minimum gap in that
        // order. Displayed positions just ease toward those targets: on an
        // overtake the ranks swap and the two labels visibly slide past
        // each other. (Re-running the gap solve on the eased positions in
        // the new rank order — the old behaviour — teleported the pair into
        // the swapped arrangement in a single frame.)
        //
        // The order itself is persistent with hysteresis: two near-tied
        // difficulties cross back and forth every few frames, and re-sorting
        // on the raw values flapped the pair's targets so fast that both eased
        // labels converged on the crossing point and sat superimposed. A
        // pair only trades places once the lower label's difficulty leads by a
        // real margin, so ties hold a stable stack and a genuine overtake
        // fires exactly one clean slide.
        labelOrder.retainAll(visible);
        for (Track track : visible) {
            if (!labelOrder.contains(track)) {
                labelOrder.add(track);
            }
        }
        boolean reordered = true;
        while (reordered) {
            reordered = false;
            for (int i = 0; i + 1 < labelOrder.size(); i++) {
                Track upper = labelOrder.get(i);
                Track lower = labelOrder.get(i + 1);
                double lead = ratingNow(lower) - ratingNow(upper);
                boolean cooled = sceneSeconds - upper.lastQueueSwapSeconds > SWAP_COOLDOWN_SECONDS
                        && sceneSeconds - lower.lastQueueSwapSeconds > SWAP_COOLDOWN_SECONDS;
                if (lead > RANK_SWAP_HYSTERESIS && (cooled || lead > RANK_SWAP_FORCE)) {
                    labelOrder.set(i, lower);
                    labelOrder.set(i + 1, upper);
                    upper.lastQueueSwapSeconds = sceneSeconds;
                    lower.lastQueueSwapSeconds = sceneSeconds;
                    reordered = true;
                }
            }
        }

        float queueTop = plotTop + 26f;
        float queueBottom = plotBottom - 22f;
        float[] targets = new float[labelOrder.size()];
        for (int i = 0; i < labelOrder.size(); i++) {
            targets[i] = labelOrder.get(i).labelTargetY;
        }
        stackWithGaps(targets, queueTop, queueBottom);
        for (int i = 0; i < labelOrder.size(); i++) {
            labelOrder.get(i).labelTargetY = targets[i];
        }

        // Ease only the collision displacement (stacked target minus the live
        // head-dot Y) and add it back onto the dot Y, so the label tracks the
        // head exactly. Easing the absolute canvas Y instead left the label
        // trailing the head whenever the y-window rescaled quickly (e.g. the
        // intro ease) — only label-vs-label spacing should ease, not the dot.
        for (Track track : visible) {
            float displacement = track.labelTargetY - track.labelDotY;
            if (!track.labelInitialised) {
                track.labelOffset = displacement;
                track.labelInitialised = true;
            } else {
                track.labelOffset = ease(track.labelOffset, displacement, dt, LABEL_EASE_RATE);
            }
            track.labelY = track.labelDotY + track.labelOffset;
        }

        // One shared column for the front, anchored to the live race head.
        // Retiring tracks can keep fading in the column during the short front
        // tolerance, but their frozen line ends must not pull the column left.
        // Labels may run past the plot into the right margin — only the
        // viewport edge clips them.
        p.textSize(LABEL_TEXT_SIZE);
        float maxLabelWidth = 0f;
        for (Track track : visible) {
            maxLabelWidth = Math.max(maxLabelWidth, p.textWidth(headLabelText(track)));
        }
        // Feed the camera the room this frame's labels actually need, so the
        // follow fraction keeps the whole block right of every line end and
        // left of the ledger gutter.
        float requiredMargin = LABEL_DOT_GAP_PX + maxLabelWidth + LABEL_MARGIN_EXTRA_PX;
        followMarginPx = ease(followMarginPx, requiredMargin, dt, 2f);
        // Clamp the column off the EASED margin, not the raw label width — a
        // label that suddenly widens (Lintahlo gaining a minus sign as it
        // crosses 0) would otherwise jerk the whole column left in one frame.
        float clampX = raceRightEdge() - followMarginPx + LABEL_DOT_GAP_PX;
        float frontX = grid.domainToCanvasX(Math.min(tDay, grid.getXMax()));
        float columnX = Math.min(frontX + LABEL_DOT_GAP_PX, clampX);

        // Draw bottom rank first: during a swap the rising label has already
        // taken the higher rank, so it renders later — always in FRONT of
        // the label it is passing.
        for (int i = labelOrder.size() - 1; i >= 0; i--) {
            Track track = labelOrder.get(i);
            String label = headLabelText(track);
            float x = front.contains(track)
                    ? columnX
                    : Math.min(track.labelHeadX + LABEL_DOT_GAP_PX, clampX);

            p.textAlign(Applet.LEFT, Applet.CENTER);
            p.noStroke();
            p.textSize(LABEL_TEXT_SIZE);
            p.fill(0, 0, 0, 62f * track.labelAlpha);
            p.text(label, x + 2f, track.labelY + 2f);
            fillTrack(track, 100f * track.labelAlpha);
            p.text(label, x, track.labelY);
        }
    }

    /** Head label: {@code Username (7.43)} — current PR — in the line colour. */
    private String headLabelText(Track track) {
        return track.name + " (" + String.format(Locale.ENGLISH, "%.2f", ratingNow(track)) + ")";
    }

    /** Current difficulty; frozen at the final knot once the track has retired. */
    private double ratingNow(Track track) {
        return track.spline.value(Math.min(tDay, track.lastDay));
    }

    /**
     * Enforce the minimum gap with the least total displacement
     * (pool-adjacent-violators): an isolated label sits exactly at its dot,
     * and a conflicting group centres on the mean of its dots instead of
     * always being pushed downward — so the top of a cluster floats slightly
     * above its dot and the bottom slightly below, keeping names visually
     * attached to their lines.
     */
    private static void stackWithGaps(float[] ys, float top, float bottom) {
        int n = ys.length;
        if (n == 0) {
            return;
        }

        // Substituting z_i = y_i - i*gap turns "gaps >= gap" into "z is
        // non-decreasing"; isotonic regression via pool-adjacent-violators.
        float[] blockSum = new float[n];
        int[] blockCount = new int[n];
        int blocks = 0;
        for (int i = 0; i < n; i++) {
            blockSum[blocks] = ys[i] - i * LABEL_MIN_GAP_PX;
            blockCount[blocks] = 1;
            blocks++;
            while (blocks > 1 && blockSum[blocks - 2] / blockCount[blocks - 2]
                    >= blockSum[blocks - 1] / blockCount[blocks - 1]) {
                blockSum[blocks - 2] += blockSum[blocks - 1];
                blockCount[blocks - 2] += blockCount[blocks - 1];
                blocks--;
            }
        }
        int index = 0;
        for (int b = 0; b < blocks; b++) {
            float mean = blockSum[b] / blockCount[b];
            for (int k = 0; k < blockCount[b]; k++) {
                ys[index] = mean + index * LABEL_MIN_GAP_PX;
                index++;
            }
        }

        // Keep the whole stack inside the plot (bottom bound wins if the
        // stack is taller than the plot, which cannot happen in practice).
        float shiftDown = top - ys[0];
        if (shiftDown > 0) {
            for (int i = 0; i < n; i++) {
                ys[i] += shiftDown;
            }
        }
        float shiftUp = ys[n - 1] - bottom;
        if (shiftUp > 0) {
            for (int i = 0; i < n; i++) {
                ys[i] -= shiftUp;
            }
        }
    }

    /**
     * Reference-style header, no card box:
     * {@code Leader: [avatar] Name (difficulty)} with
     * {@code For N days (~Y.YY years)} underneath.
     */
    private void drawLeaderHeader() {
        Applet p = applet();
        ensureFont();
        p.textFont(font);

        float x = grid.getPlotLeft() + 28f;
        float y = -halfViewportHeight() + 24f;

        String prefix = "Leader:  ";
        String rating = leader != null
                ? String.format(Locale.ENGLISH, "%.2f", leader.spline.value(tDay))
                : "--";
        String title = (leader != null ? leader.name : "?") + " (" + rating + ")";
        int days = (int) Math.max(0, Math.floor(tDay - leaderSinceDay));
        String tenure = "For " + days + (days == 1 ? " day" : " days")
                + String.format(Locale.ENGLISH, " (~%.2f years)", days / 365.25);

        // The avatar slot is always present: the climber's PNG when it
        // exists, the anonymous-silhouette placeholder otherwise.
        PImage avatar = leader != null ? avatarFor(leader) : null;
        float avatarSize = 88f;
        float avatarWidth = avatarSize + 20f;
        p.textSize(48);
        float prefixWidth = p.textWidth(prefix);
        float titleWidth = p.textWidth(title);
        p.textSize(36);
        float tenureWidth = p.textWidth(tenure);
        float contentWidth = prefixWidth + avatarWidth + Math.max(titleWidth, tenureWidth);

        // 2DGP-style translucent backing panel.
        p.noStroke();
        p.fill(0, 0, 0, HUD_PANEL_ALPHA);
        p.rect(x - 18f, y - 12f, x + contentWidth + 18f, y + 106f);

        p.textAlign(Applet.LEFT, Applet.TOP);
        p.textSize(48);
        p.fill(0, 0, 100, 100);
        p.text(prefix, x, y);
        float nameX = x + prefixWidth;

        if (avatar != null) {
            p.image(avatar, nameX, y - 6f, avatarSize, avatarSize);
        } else {
            drawAvatarPlaceholder(nameX, y - 6f, avatarSize);
        }
        nameX += avatarWidth;

        p.textAlign(Applet.LEFT, Applet.TOP);
        p.textSize(48);
        p.fill(0, 0, 100, 100);
        p.text(title, nameX, y);

        p.textSize(36);
        p.fill(0, 0, 92, 96);
        p.text(tenure, nameX, y + 58f);
    }

    /**
     * "Unknown climber" mark for tracks without an avatar PNG:
     * ringed disc, light head-and-shoulders silhouette, "?" on the face.
     */
    private void drawAvatarPlaceholder(float x, float y, float size) {
        Applet p = applet();
        float cx = x + size / 2f;
        float cy = y + size / 2f;
        float d = size - 4f;

        p.noStroke();
        p.fill(0, 0, 16, 100);
        p.circle(cx, cy, d);

        p.fill(0, 0, 88, 100);
        p.circle(cx, cy - size * 0.13f, size * 0.30f);
        p.arc(cx, cy + size * 0.38f, size * 0.56f, size * 0.50f,
                (float) Math.PI, (float) (2 * Math.PI));

        p.noFill();
        p.stroke(0, 0, 92, 100);
        p.strokeWeight(2.5f);
        p.circle(cx, cy, d);
        p.noStroke();

        p.fill(0, 0, 16, 100);
        p.textFont(font);
        p.textSize(size * 0.20f);
        p.textAlign(Applet.CENTER, Applet.CENTER);
        p.text("?", cx, cy - size * 0.13f);
    }

    private void drawDateReadout() {
        Applet p = applet();
        ensureFont();
        p.textFont(font);

        double shownDay = Math.min(tDay, endDay);
        LocalDate date = dayZero.plusDays((long) Math.floor(shownDay));
        String dateText = DATE_READOUT.format(date);
        float rightX = halfViewportWidth() - 48f;
        float topY = -halfViewportHeight() + 24f;

        p.textSize(48);
        float headingWidth = p.textWidth("Current Date:");
        p.textSize(41);
        float dateWidth = p.textWidth(dateText);
        // The date line is centred under the heading, not right-justified.
        float headingCenter = rightX - headingWidth / 2f;
        float panelLeft = Math.min(rightX - headingWidth, headingCenter - dateWidth / 2f) - 18f;

        // 2DGP-style translucent backing panel. The clock recess grows down
        // FAST (full by drama ~0.1) so the box is already there before the
        // time text fades in — otherwise the time clipped below a half-grown box.
        p.noStroke();
        p.fill(0, 0, 0, HUD_PANEL_ALPHA);
        float clockRecess = 48f * clamp01F((float) currentDrama * 10f);
        p.rect(panelLeft, topY - 12f, rightX + 18f, topY + 112f + clockRecess);

        p.textAlign(Applet.RIGHT, Applet.TOP);
        p.textSize(48);
        p.fill(0, 0, 100, 100);
        p.text("Current Date:", rightX, topY);
        p.textAlign(Applet.CENTER, Applet.TOP);
        p.textSize(41);
        p.fill(0, 0, 92, 96);
        p.text(dateText, headingCenter, topY + 60f);

        // Slow-mo clock: time-of-day, fading in only while bullet-time is
        // active, so the viewer feels the sim crawl through the hours.
        // Time text only after the box is fully open (drama > 0.12), so it
        // never spills past the panel edge.
        if (currentDrama > 0.12) {
            double frac = shownDay - Math.floor(shownDay);
            int totalMin = (int) Math.round(frac * 24 * 60) % (24 * 60);
            String timeText = String.format(Locale.ENGLISH, "%02d:%02d", totalMin / 60, totalMin % 60);
            p.textAlign(Applet.CENTER, Applet.TOP);
            p.textSize(40);
            p.fill(0, 0, 100, 96f * clamp01F(((float) currentDrama - 0.12f) * 5f));
            p.text(timeText, headingCenter, topY + 110f);
        }
    }

    /**
     * Optional avatar thumbnails: drop {@code src/data/jtoh/avatars/<name>.png}
     * into the repo and it shows up next to the head label and in the leader
     * header. Missing files are simply skipped.
     */
    private PImage avatarFor(Track track) {
        if (!track.avatarChecked) {
            track.avatarChecked = true;
            java.nio.file.Path path = java.nio.file.Path.of("src/data/jtoh/avatars", track.name + ".png");
            if (!Files.exists(path)) {
                path = java.nio.file.Path.of("src/data/jtoh/avatars",
                        track.name.toLowerCase(Locale.ROOT) + ".png");
            }
            if (Files.exists(path)) {
                track.avatar = applet().loadImage(path.toString());
            }
        }
        return track.avatar;
    }

    // ------------------------------------------------------------------
    // Ledger (right-column feed of recent tower completions)
    // ------------------------------------------------------------------

    /** Right edge available to the race: the viewport edge minus the ledger
     *  gutter, so head dots and labels never run under the feed. */
    private float raceRightEdge() {
        return halfViewportWidth() - LEDGER_WIDTH;
    }

    /** Brightened tier colour for a difficulty value — the band's edge colour,
     *  legible as text where the dark fill colour would not be. */
    private Color bandEdgeColorFor(double value) {
        for (ValueBand band : tierBands) {
            if (value >= band.lo() && value < band.hi()) {
                return band.edge();
            }
        }
        return tierBands.get(tierBands.size() - 1).edge();
    }

    /** Admit completions whose time has arrived (newest to the top), then ease
     *  every entry toward its slot; rows past the cap fade out as they go. */
    private void updateLedger(double dt) {
        while (eventCursor < events.size() && events.get(eventCursor).day <= tDay) {
            LedgerEntry entry = new LedgerEntry(events.get(eventCursor));
            entry.xOffset = LEDGER_ENTER_SLIDE; // start off the right edge, slide in
            ledger.add(0, entry);
            eventCursor++;
        }
        for (int i = 0; i < ledger.size(); i++) {
            LedgerEntry e = ledger.get(i);
            float targetY = i * LEDGER_ROW_H;
            boolean alive = i < LEDGER_MAX_ROWS;
            if (!e.yInitialised) {
                e.y = targetY;
                e.yInitialised = true;
            } else {
                e.y = ease(e.y, targetY, dt, LEDGER_Y_EASE);
            }
            e.alpha = ease(e.alpha, alive ? 1f : 0f, dt, alive ? LEDGER_FADE_IN : LEDGER_FADE_OUT);
            e.xOffset = ease(e.xOffset, 0f, dt, LEDGER_ENTER_EASE);
        }
        for (int i = ledger.size() - 1; i >= LEDGER_MAX_ROWS; i--) {
            if (ledger.get(i).alpha < 0.02f) {
                ledger.remove(i);
            }
        }
    }

    private void drawLedger() {
        Applet p = applet();
        ensureFont();
        p.textFont(font);

        float panelLeft = halfViewportWidth() - LEDGER_WIDTH;
        float panelRight = halfViewportWidth() - 18f;
        float top = -halfViewportHeight() + LEDGER_PANEL_TOP;
        float listTop = top + 40f;
        float panelBottom = listTop + LEDGER_MAX_ROWS * LEDGER_ROW_H + 6f;

        // Backing panel — darker than the other HUD blocks so colour-coded
        // text stays legible over whatever band sits behind it.
        p.noStroke();
        p.fill(0, 0, 0, LEDGER_PANEL_ALPHA);
        p.rect(panelLeft, top - 6f, panelRight, panelBottom);

        p.textAlign(Applet.LEFT, Applet.TOP);
        p.textSize(24);
        p.fill(0, 0, 70, 82);
        p.text("RECENT TOWERS", panelLeft + 20f, top + 4f);

        float textX = panelLeft + 20f;
        float valX = panelRight - 14f;
        for (int i = 0; i < ledger.size() && i < LEDGER_MAX_ROWS + 2; i++) {
            LedgerEntry e = ledger.get(i);
            float cy = listTop + e.y + LEDGER_ROW_H / 2f;
            if (e.alpha <= 0.01f || cy > panelBottom + LEDGER_ROW_H) {
                continue;
            }
            Color c = bandEdgeColorFor(e.event.difficulty);
            float h = c.getHue().getValue();
            float s = Math.min(c.getSaturation().getValue(), LEDGER_TEXT_MAX_SAT);
            float b = Math.max(c.getBrightness().getValue(), LEDGER_TEXT_MIN_BRI);
            String label = e.event.track.name + " beat " + e.event.code;
            String val = String.format(Locale.ENGLISH, "%.2f", e.event.difficulty);
            p.textAlign(Applet.LEFT, Applet.CENTER);
            p.textSize(LEDGER_TEXT_SIZE);
            p.fill(0, 0, 0, 60f * e.alpha);
            p.text(label, textX + e.xOffset + 1.5f, cy + 1.5f);
            p.fill(h, s, b, 100f * e.alpha);
            p.text(label, textX + e.xOffset, cy);
            p.textAlign(Applet.RIGHT, Applet.CENTER);
            p.fill(h, s, b, 70f * e.alpha);
            p.text(val, valX + e.xOffset, cy);
        }
    }

    private void strokeTrack(Track track, float alpha) {
        Color c = track.color;
        p.stroke(c.getHue().getValue(), c.getSaturation().getValue(),
                c.getBrightness().getValue(), alpha);
    }

    private void fillTrack(Track track, float alpha) {
        Color c = track.color;
        p.fill(c.getHue().getValue(), c.getSaturation().getValue(),
                c.getBrightness().getValue(), alpha);
    }

    private void ensureFont() {
        if (font == null) {
            // The reference look is Lato; Main loads the bundled TTFs as
            // shared fonts, so the installed-face scan is only a fallback for
            // running the scene without Main's setup.
            PFont latoBold = applet().getLatoBoldFont();
            PFont lato = applet().getLatoFont();
            font = latoBold != null ? latoBold : applet().createFont(pickFontFace(
                    "Lato Bold", "Lato", "Helvetica Neue Bold", "HelveticaNeue-Bold",
                    "Arial Bold", "DejaVu Sans Bold", "Verdana Bold"), 150, true);
            grid.setLabelFont(lato != null ? lato : applet().createFont(pickFontFace(
                    "Lato", "Helvetica Neue", "Arial", "DejaVu Sans", "Verdana"), 150, true));
        }
    }

    /** First installed font face/family from the preference list, else logical SansSerif. */
    private static String pickFontFace(String... preferred) {
        java.util.Set<String> available = new java.util.HashSet<>();
        for (java.awt.Font installed
                : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
            available.add(installed.getFontName(Locale.ENGLISH).toLowerCase(Locale.ROOT));
            available.add(installed.getFamily(Locale.ENGLISH).toLowerCase(Locale.ROOT));
        }
        for (String candidate : preferred) {
            if (available.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return "SansSerif";
    }

    // ------------------------------------------------------------------
    // Data loading
    // ------------------------------------------------------------------

    /**
     * The JToH difficulty tiers (ratings 1..13: Easy .. Unreal), as dark
     * backdrop tints + brighter edges. Official chart colours from the JToH
     * wiki. Each tier spans one rating unit; the band's lower bound is the
     * tier's threshold (Medium starts at 2, Hard at 3, ...).
     */
    private static List<ValueBand> buildTierBands() {
        // Outer tiers are open-ended (huge bounds) so their fill always reaches
        // the plot edge — no black gap above the top line when the y-window
        // adds headroom, or below the bottom line.
        Object[][] tiers = {
                {"Easy", "#5b9a4c", -1.0e6, 2.0},
                {"Medium", "#ffb000", 2.0, 3.0},
                {"Hard", "#aa5500", 3.0, 4.0},
                {"Difficult", "#c4281c", 4.0, 5.0},
                {"Challenging", "#750000", 5.0, 6.0},
                {"Intense", "#1b2a35", 6.0, 7.0},
                {"Remorseless", "#ff00bf", 7.0, 8.0},
                {"Insane", "#0000ff", 8.0, 9.0},
                {"Extreme", "#2154b9", 9.0, 10.0},
                {"Terrifying", "#00ffff", 10.0, 11.0},
                {"Catastrophic", "#ffffff", 11.0, 12.0},
                {"Horrific", "#a75e9b", 12.0, 13.0},
                {"Unreal", "#7b007b", 13.0, 1.0e6},
        };
        List<ValueBand> bands = new ArrayList<>();
        for (int i = 0; i < tiers.length; i++) {
            String label = (String) tiers[i][0];
            Color base = Color.fromCss((String) tiers[i][1]);
            double lo = (Double) tiers[i][2];
            double hi = (Double) tiers[i][3];
            float h = base.getHue().getValue();
            float s = base.getSaturation().getValue();
            float b = base.getBrightness().getValue();
            // Gentle brightness ramp up the stack keeps the colours legible as
            // backdrops (and nudges near-black Intense / the two blues apart)
            // without shouting over the lines.
            float fillBri = Math.min(54f, 30f + i * 2.4f);
            Color fill = new Color(h, s * 0.9f, fillBri, 42f);
            Color edge = new Color(h, s, Math.min(100f, b + 36f), 58f);
            bands.add(new ValueBand(lo, hi, fill, edge, label));
        }
        return bands;
    }

    /**
     * Read the completions CSV ({@code player,color,datetime,code,difficulty})
     * and build one PR-progression track per player plus the global event feed.
     * Each track's line is the PCHIP of that player's PR knots (the running max
     * of difficulty), flattened out to the global end date; the ledger uses
     * every completion.
     */
    private static Loaded loadData(String path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not read " + path + " — run tools/generate_jtoh_pr_data.py "
                            + "from the repo root first", e);
        }

        List<String> rowPlayer = new ArrayList<>();
        List<LocalDateTime> rowWhen = new ArrayList<>();
        List<String> rowCode = new ArrayList<>();
        List<Double> rowValue = new ArrayList<>();
        Map<String, String> colorOf = new LinkedHashMap<>();
        Map<String, String> modeOf = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 6) {
                throw new IllegalStateException(path + " line " + (i + 1) + " is malformed: " + line);
            }
            colorOf.putIfAbsent(parts[0], parts[1]);
            modeOf.putIfAbsent(parts[0], parts[2]);
            rowPlayer.add(parts[0]);
            rowWhen.add(LocalDateTime.parse(parts[3]));
            rowCode.add(parts[4]);
            rowValue.add(Double.parseDouble(parts[5]));
        }
        if (rowWhen.isEmpty()) {
            throw new IllegalStateException(
                    "No completions in " + path + " — run tools/generate_jtoh_pr_data.py first");
        }

        LocalDate dayZero = rowWhen.get(0).toLocalDate();
        for (LocalDateTime when : rowWhen) {
            if (when.toLocalDate().isBefore(dayZero)) {
                dayZero = when.toLocalDate();
            }
        }
        LocalDateTime base = dayZero.atStartOfDay();
        double[] day = new double[rowWhen.size()];
        double endDay = 0;
        for (int i = 0; i < rowWhen.size(); i++) {
            day[i] = Duration.between(base, rowWhen.get(i)).toMillis() / 86_400_000.0;
            endDay = Math.max(endDay, day[i]);
        }

        Map<String, List<Integer>> rowsByPlayer = new LinkedHashMap<>();
        for (int i = 0; i < rowPlayer.size(); i++) {
            rowsByPlayer.computeIfAbsent(rowPlayer.get(i), k -> new ArrayList<>()).add(i);
        }

        Map<String, Track> trackOf = new LinkedHashMap<>();
        List<Track> tracks = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : rowsByPlayer.entrySet()) {
            String player = entry.getKey();
            List<Integer> idx = entry.getValue();
            idx.sort(Comparator.comparingDouble(i -> day[i]));
            boolean lineMode = "line".equals(modeOf.get(player));
            List<double[]> knots = new ArrayList<>();
            double lastDay = Double.NEGATIVE_INFINITY;
            double lastValue = 0;
            if (lineMode) {
                // Explicit line: plot the rows directly, no running-max filter.
                for (int i : idx) {
                    double d = day[i];
                    if (!knots.isEmpty() && d <= lastDay) {
                        d = lastDay + 1e-4; // PCHIP needs strictly increasing x
                    }
                    knots.add(new double[]{d, rowValue.get(i)});
                    lastDay = d;
                    lastValue = rowValue.get(i);
                }
            } else {
                // PR knots = the running-max-of-difficulty subsequence.
                double max = Double.NEGATIVE_INFINITY;
                for (int i : idx) {
                    double v = rowValue.get(i);
                    if (v > max + 1e-9) {
                        double d = day[i];
                        if (!knots.isEmpty() && d <= lastDay) {
                            d = lastDay + 1e-4;
                        }
                        knots.add(new double[]{d, v});
                        max = v;
                        lastDay = d;
                        lastValue = v;
                    }
                }
            }
            // Flatten the tail: hold the final value out to the global end date.
            if (endDay > lastDay + 1e-6) {
                knots.add(new double[]{endDay, lastValue});
            } else if (knots.size() < 2) {
                knots.add(new double[]{lastDay + 1.0, lastValue});
            }
            double[] kd = new double[knots.size()];
            double[] kv = new double[knots.size()];
            for (int j = 0; j < knots.size(); j++) {
                kd[j] = knots.get(j)[0];
                kv[j] = knots.get(j)[1];
            }
            Track track = new Track(player, Color.fromCss(colorOf.get(player)), kd, kv);
            trackOf.put(player, track);
            tracks.add(track);
        }

        List<Event> events = new ArrayList<>(rowPlayer.size());
        for (int i = 0; i < rowPlayer.size(); i++) {
            if ("line".equals(modeOf.get(rowPlayer.get(i)))) {
                continue; // line-mode players raise no ledger events
            }
            events.add(new Event(day[i], trackOf.get(rowPlayer.get(i)),
                    rowCode.get(i), rowValue.get(i)));
        }
        events.sort(Comparator.comparingDouble(e -> e.day));

        return new Loaded(tracks, events, computeBursts(events), dayZero, endDay);
    }

    /** Find the dramatic activity spikes: flag dense cores, merge them into
     *  windows (bridging short gaps), keep the ones above a count threshold,
     *  and scale each window's intensity by how many completions it holds. */
    private static List<Burst> computeBursts(List<Event> events) {
        int n = events.size();
        List<Burst> bursts = new ArrayList<>();
        if (n == 0) {
            return bursts;
        }
        double half = BURST_DENSITY_WINDOW / 2.0;
        boolean[] dense = new boolean[n];
        for (int i = 0; i < n; i++) {
            int count = 0;
            for (int j = 0; j < n; j++) {
                if (Math.abs(events.get(j).day - events.get(i).day) <= half) {
                    count++;
                }
            }
            dense[i] = count >= BURST_DENSITY_COUNT;
        }
        List<double[]> raw = new ArrayList<>(); // {startDay, endDay, count}
        double maxCount = BURST_MIN_EVENTS;
        int i = 0;
        while (i < n) {
            if (!dense[i]) {
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < n && (dense[j + 1] || events.get(j + 1).day - events.get(j).day < 5.0)) {
                j++;
            }
            int count = j - i + 1;
            raw.add(new double[]{events.get(i).day, events.get(j).day, count});
            maxCount = Math.max(maxCount, count);
            i = j + 1;
        }
        for (double[] r : raw) {
            if (r[2] < BURST_MIN_EVENTS) {
                continue;
            }
            double t = clamp01((r[2] - BURST_MIN_EVENTS) / Math.max(1.0, maxCount - BURST_MIN_EVENTS));
            bursts.add(new Burst(r[0], r[1], BURST_MIN_INTENSITY + t * (1.0 - BURST_MIN_INTENSITY)));
        }
        return bursts;
    }

    private static double readMsPerDay() {
        String raw = System.getProperty("msPerDay", "").trim();
        if (raw.isEmpty()) {
            return DEFAULT_MS_PER_DAY;
        }
        double parsed = Double.parseDouble(raw);
        if (parsed <= 0) {
            throw new IllegalArgumentException("msPerDay must be positive: " + raw);
        }
        return parsed;
    }

    /** Decay a sample's framing influence toward the recent range by age. */
    private static double ageDiscounted(double value, double age,
                                        double recentMin, double recentMax, double strength) {
        if (strength <= 0 || age <= Y_FIT_RECENT_DAYS) {
            return value;
        }
        double weight = 1.0 - strength
                * (1.0 - Math.exp(-(age - Y_FIT_RECENT_DAYS) / Y_FIT_AGE_DECAY_DAYS));
        if (value > recentMax) {
            return recentMax + (value - recentMax) * weight;
        }
        if (value < recentMin) {
            return recentMin + (value - recentMin) * weight;
        }
        return value;
    }

    // ------------------------------------------------------------------
    // Small math helpers
    // ------------------------------------------------------------------

    private static float ease(float current, float target, double dt, float rate) {
        return current + (target - current) * (1f - (float) Math.exp(-rate * dt));
    }

    private static double ease(double current, double target, double dt, float rate) {
        return current + (target - current) * (1.0 - Math.exp(-rate * dt));
    }

    private static double interpolate(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static float clamp01F(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double smoothstep(double value) {
        return value * value * (3 - 2 * value);
    }

    private static final class Loaded {
        private final List<Track> tracks;
        private final List<Event> events;
        private final List<Burst> bursts;
        private final LocalDate dayZero;
        private final double endDay;

        private Loaded(List<Track> tracks, List<Event> events, List<Burst> bursts,
                       LocalDate dayZero, double endDay) {
            this.tracks = tracks;
            this.events = events;
            this.bursts = bursts;
            this.dayZero = dayZero;
            this.endDay = endDay;
        }
    }

    /** A precomputed activity spike that earns the slow-mo + zoom treatment. */
    private static final class Burst {
        private final double startDay;
        private final double endDay;
        private final double intensity; // BURST_MIN_INTENSITY .. 1.0

        private Burst(double startDay, double endDay, double intensity) {
            this.startDay = startDay;
            this.endDay = endDay;
            this.intensity = intensity;
        }
    }

    /** One tower completion — a ledger event. */
    private static final class Event {
        private final double day;
        private final Track track;
        private final String code;
        private final double difficulty;

        private Event(double day, Track track, String code, double difficulty) {
            this.day = day;
            this.track = track;
            this.code = code;
            this.difficulty = difficulty;
        }
    }

    /** A live row in the ledger feed: eased slot position + fade. */
    private static final class LedgerEntry {
        private final Event event;
        private float y;
        private float alpha;
        private float xOffset;
        private boolean yInitialised;

        private LedgerEntry(Event event) {
            this.event = event;
        }
    }

    /** One player's PR-progression line: a PCHIP through their PR knots. */
    private static final class Track {
        private final String name;
        private final Color color;
        private final Pchip spline;
        private final double firstDay;
        private final double lastDay;

        private float labelY;
        private float labelTargetY;
        private float labelDotY;
        private float labelOffset;
        private float labelHeadX;
        private float labelAlpha;
        private boolean labelInitialised;
        private double lastQueueSwapSeconds = Double.NEGATIVE_INFINITY;
        private float strokeBoost;
        private PImage avatar;
        private boolean avatarChecked;

        private Track(String name, Color color, double[] knotDays, double[] knotValues) {
            if (knotDays.length < 2) {
                throw new IllegalStateException("Track " + name + " needs at least two knots");
            }
            this.name = name;
            this.color = color;
            this.spline = new Pchip(knotDays, knotValues);
            this.firstDay = knotDays[0];
            this.lastDay = knotDays[knotDays.length - 1];
        }

        private boolean isActiveAt(double day) {
            return day >= firstDay && day <= lastDay + RANK_GRACE_DAYS;
        }
    }
}
