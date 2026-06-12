package directions.scenes;

import core.Applet;
import directions.engine.Action;
import directions.engine.Actions;
import directions.engine.Nodes;
import directions.engine.Scene;
import directions.engine.SceneContext;
import geom.DataGrid;
import processing.core.PFont;
import processing.core.PImage;
import storage.Color;
import util.Pchip;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Animated race chart of the top CS2 players by 3-month rolling HLTV rating.
 *
 * <p>Data comes from {@code src/data/cs2/top_players_rolling.csv}
 * (player,team,color,date,rating — weekly rolling-average knots produced by
 * {@code tools/generate_cs2_mock_data.py} or {@code tools/scrape_hltv.py}).
 * Knots are PCHIP-interpolated, so lines stay smooth and overshoot-free at
 * any playback speed.
 *
 * <p>Timeline: one simulated day per {@code -DmsPerDay} milliseconds
 * (default {@value #DEFAULT_MS_PER_DAY}). The camera follows the head of the
 * race through a one-year window, then eases out to the full date range at
 * the end with the race head pinned in place. Top left shows a
 * reference-style leader header, top right the simulated date. Optional
 * avatar thumbnails are picked up from
 * {@code src/data/cs2/avatars/<player>.png}.
 */
public final class Cs2TopPlayersScene extends Scene {
    private static final String DATA_PATH = "src/data/cs2/top_players_rolling.csv";
    private static final String TEAM_LOGO_DIR = "src/data/cs2/team_logos";
    /** 85ms/day over the 2017..mid-2026 dataset lands the video at ~5:00. */
    private static final double DEFAULT_MS_PER_DAY = 85;
    private static final double WINDOW_DAYS = 365;
    /** Upper bound for the follow fraction — the actual fraction is derived
     *  every frame from the measured widest label (logo + name + rating) so
     *  the whole label block always fits between the head dots and the
     *  viewport edge; a fixed fraction left the column clamped onto the
     *  line tails whenever a long name entered the race. */
    private static final double MAX_FOLLOW_RATIO = 0.83;
    private static final float LABEL_MARGIN_EXTRA_PX = 56f;
    private static final double FINAL_ZOOM_DELAY = 0.6;
    private static final double FINAL_ZOOM_DURATION = 5.0;
    private static final double END_HOLD_SECONDS = 4.0;
    private static final double Y_BASE_STEP = 0.05;
    private static final double Y_MIN_SPAN = 0.18;
    private static final double Y_FIT_SAMPLE_DAYS = 5.0;
    private static final float Y_FIT_EASE_RATE = 2.2f;
    private static final float LABEL_EASE_RATE = 6f;
    private static final float LABEL_TEXT_SIZE = 38f;
    private static final float LABEL_MIN_GAP_PX = 46f;
    /** Horizontal gap between a head dot and the start of its label block. */
    private static final float LABEL_DOT_GAP_PX = 34f;
    /** Square box the head-label team logo is fitted into. */
    private static final float LABEL_LOGO_BOX_PX = 36f;
    private static final float LABEL_LOGO_SPACE_PX = LABEL_LOGO_BOX_PX + 9f;
    private static final float LINE_STROKE_PX = 5.2f;
    private static final float HEAD_DOT_PX = 15f;
    private static final float RETIRE_LINE_FADE_DAYS = 60f;
    private static final float RETIRE_LABEL_FADE_SECONDS = 1.0f;
    /**
     * Tracks whose data ends within this many days of "now" still count for
     * ranking/labels. Without it, players whose last knot lands a few days
     * before the global end of the dataset would all "retire" on the final
     * frames and hand #1 to whoever happens to have the latest knot.
     */
    private static final double RANK_GRACE_DAYS = 45;
    /**
     * A track counts as part of the race FRONT (shared label column, label
     * queue) only while its data reaches within this many days of "now".
     * The wider RANK_GRACE_DAYS is for ranking/label-fade only — using it
     * for the column made a freshly-retired line drag the whole label column
     * back onto the dots.
     */
    private static final double FRONT_TOLERANCE_DAYS = 10;
    /**
     * A label only moves down/up the queue once the rating difference
     * exceeds this margin — rating noise around a tie must not flap the
     * queue order every few frames (the labels of a flapping pair both
     * converge on the crossing point and render superimposed).
     */
    private static final double RANK_SWAP_HYSTERESIS = 0.006;
    /**
     * After a label trades places it cannot trade again for this long
     * unless the rating gap is decisive ({@link #RANK_SWAP_FORCE}). The
     * margin alone cannot stop near-ties whose noise swings beyond it
     * within a fraction of a second of video — the pair's targets then
     * exchange faster than the easing can follow and both labels converge
     * superimposed at the crossing midpoint.
     */
    private static final double SWAP_COOLDOWN_SECONDS = 1.2;
    private static final double RANK_SWAP_FORCE = 0.025;
    /** Alpha of the translucent panels behind the HUD blocks (2DGP look). */
    private static final float HUD_PANEL_ALPHA = 40f;
    private static final DateTimeFormatter DATE_READOUT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final DataGrid grid;
    private final List<Track> tracks;
    private final LocalDate dayZero;
    private final double endDay;
    private final double msPerDay;

    private PFont font;

    private double tDay;
    private double sceneSeconds;
    private double visibleXMin;
    private double visibleXSpan = WINDOW_DAYS;
    private double yShownMin = 1.0;
    private double yShownMax = 1.4;
    private boolean yFitInitialised;

    private Track leader;
    private double leaderSinceDay;
    /** Persistent head-label queue order (highest rating first). */
    private final List<Track> labelOrder = new ArrayList<>();
    private final Map<String, PImage> teamLogos = new LinkedHashMap<>();
    private final java.util.Set<String> missingTeamLogos = new java.util.HashSet<>();

    private boolean zoomOutStarted;
    private double zoomOutElapsed;
    private double zoomOutStartXMin;
    private double zoomOutStartXSpan;
    private double zoomOutHeadFraction = MAX_FOLLOW_RATIO;
    private double endHoldElapsed;
    /** Pixels needed right of the head dots for the widest label block,
     *  measured in drawHeadLabels and smoothed. */
    private float followMarginPx = 320f;

    public Cs2TopPlayersScene(Applet p) {
        super(p);
        msPerDay = readMsPerDay();
        tracks = loadTracks(DATA_PATH);
        if (tracks.isEmpty()) {
            throw new IllegalStateException(
                    "No player data in " + DATA_PATH + " — run tools/generate_cs2_mock_data.py first");
        }
        dayZero = tracks.stream()
                .map(track -> track.firstDate)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        double maxDay = 0;
        for (Track track : tracks) {
            track.rebase(dayZero);
            maxDay = Math.max(maxDay, track.lastDay);
        }
        endDay = maxDay;

        grid = new DataGrid(applet());
        grid.setXCalendarAxis(dayZero);
        grid.setAnchor(0, 1.0);
        grid.setYMajorStep(Y_BASE_STEP);
        // Adaptive precision: 2dp like HLTV, but half-step ticks (1.125)
        // must not round to a wrong-looking "1.13" if they ever fade in.
        grid.setYLabelFormatter(value -> {
            boolean needsThree = Math.abs(value * 100 - Math.round(value * 100)) > 1e-6;
            return String.format(Locale.ENGLISH, needsThree ? "%.3f" : "%.2f", value);
        });
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
        yShownMin = 1.0;
        yShownMax = 1.4;
        yFitInitialised = false;
        leader = null;
        leaderSinceDay = 0;
        zoomOutStarted = false;
        zoomOutElapsed = 0;
        zoomOutHeadFraction = MAX_FOLLOW_RATIO;
        endHoldElapsed = 0;
        followMarginPx = 320f;
        labelOrder.clear();
        for (Track track : tracks) {
            track.labelInitialised = false;
            track.labelAlpha = 0f;
            track.lastQueueSwapSeconds = Double.NEGATIVE_INFINITY;
            track.strokeBoost = 0f;
        }
        grid.setDomain(0, WINDOW_DAYS, yShownMin, yShownMax);
        grid.setRailCollapseRatchet(true);
    }

    @Override
    protected Action build() {
        addUpdater(this::updateTimeline);
        addNode(Nodes.of(grid::render));
        addNode(Nodes.of(this::drawSeries));
        addNode(this::drawHeadLabels);
        addNode(Nodes.of(this::drawLeaderHeader));
        addNode(Nodes.of(this::drawDateReadout));

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
        tDay = Math.min(endDay, tDay + dt * 1000.0 / msPerDay);

        if (tDay >= endDay) {
            updateFinalZoom(dt);
        } else {
            double ratio = followRatio();
            double followStart = visibleXMin + visibleXSpan * ratio;
            if (tDay > followStart) {
                visibleXMin = tDay - visibleXSpan * ratio;
            }
        }
        grid.setXRange(visibleXMin, visibleXMin + visibleXSpan);

        updateRanking(dt);
        updateYFit(dt);
    }

    /**
     * Head-dot fraction of the window that keeps the measured label block
     * (dot gap + logo + widest text) inside the viewport's right edge.
     */
    private double followRatio() {
        float plotWidth = grid.getPlotWidth();
        if (plotWidth <= 0f) {
            return MAX_FOLLOW_RATIO;
        }
        double headX = halfViewportWidth() - followMarginPx;
        double ratio = (headX - grid.getPlotLeft()) / plotWidth;
        return Math.max(0.5, Math.min(MAX_FOLLOW_RATIO, ratio));
    }

    private void updateFinalZoom(double dt) {
        if (!zoomOutStarted) {
            zoomOutStarted = true;
            zoomOutElapsed = 0;
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
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Track track : tracks) {
            double lo = Math.max(track.firstDay, xLo);
            double hi = Math.min(Math.min(tDay, track.lastDay), xHi);
            if (hi <= lo) {
                continue;
            }
            // Sample on an ABSOLUTE day grid plus the exact endpoints: a grid
            // anchored to the moving window edge shifts its sample set every
            // frame, and the resulting min/max wobble made the whole chart
            // (and the labels chasing it) jitter during the final zoom-out.
            double lov = track.spline.value(lo);
            double hiv = track.spline.value(hi);
            min = Math.min(min, Math.min(lov, hiv));
            max = Math.max(max, Math.max(lov, hiv));
            double first = Math.ceil(lo / Y_FIT_SAMPLE_DAYS) * Y_FIT_SAMPLE_DAYS;
            for (double d = first; d <= hi + 1e-9; d += Y_FIT_SAMPLE_DAYS) {
                double v = track.spline.value(d);
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

        if (!yFitInitialised) {
            // Start grounded: keep the window bottom at the rating-1.0 ground
            // line so the x axis is visible at first and then visibly falls
            // away as the camera lifts off with the eased fit.
            yShownMax = targetMax;
            yShownMin = Math.min(targetMin, yShownMin);
            yFitInitialised = true;
        } else {
            yShownMin = ease((float) yShownMin, (float) targetMin, dt, Y_FIT_EASE_RATE);
            yShownMax = ease((float) yShownMax, (float) targetMax, dt, Y_FIT_EASE_RATE);
        }
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
        float plotTop = grid.getPlotTop();
        // Soft floor: lines may dip a little below the plot (and below the
        // rating-1.0 ground axis when the window is grounded) instead of
        // visibly flattening against the boundary.
        float plotBottom = plotTop + grid.getPlotHeight() + 30f;

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
            for (double d = lo; d < hi; d += stepDays) {
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
        // ratings cross back and forth every few frames, and re-sorting on
        // the raw values flapped the pair's targets so fast that both eased
        // labels converged on the crossing point and sat superimposed. A
        // pair only trades places once the lower label's rating leads by a
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

        for (Track track : visible) {
            if (!track.labelInitialised) {
                track.labelY = track.labelTargetY;
                track.labelInitialised = true;
            } else {
                track.labelY = ease(track.labelY, track.labelTargetY, dt, LABEL_EASE_RATE);
            }
        }

        // One shared column for the front, anchored at the midpoint of its
        // head dots (the generator aligns every active line's final knot, so
        // they only differ while a line is mid-retirement). Labels may run
        // past the plot into the right margin — only the viewport edge
        // clips them.
        p.textSize(LABEL_TEXT_SIZE);
        boolean anyLogo = false;
        float maxLabelWidth = 0f;
        float minHeadX = Float.POSITIVE_INFINITY;
        float maxHeadX = Float.NEGATIVE_INFINITY;
        for (Track track : visible) {
            maxLabelWidth = Math.max(maxLabelWidth, p.textWidth(headLabelText(track)));
            anyLogo |= teamLogoFor(track.teamAt(Math.min(tDay, track.lastDay))) != null;
        }
        for (Track track : front) {
            minHeadX = Math.min(minHeadX, track.labelHeadX);
            maxHeadX = Math.max(maxHeadX, track.labelHeadX);
        }
        float logoSpace = anyLogo ? LABEL_LOGO_SPACE_PX : 0f;
        // Feed the camera the room this frame's labels actually need, so the
        // follow fraction keeps the whole block right of every line end.
        float requiredMargin = LABEL_DOT_GAP_PX + logoSpace + maxLabelWidth + LABEL_MARGIN_EXTRA_PX;
        followMarginPx = ease(followMarginPx, requiredMargin, dt, 2f);
        float clampX = halfViewportWidth() - maxLabelWidth - logoSpace - LABEL_MARGIN_EXTRA_PX;
        float columnX = minHeadX <= maxHeadX
                ? Math.min((minHeadX + maxHeadX) / 2f + LABEL_DOT_GAP_PX, clampX)
                : clampX;

        // Draw bottom rank first: during a swap the rising label has already
        // taken the higher rank, so it renders later — always in FRONT of
        // the label it is passing.
        for (int i = labelOrder.size() - 1; i >= 0; i--) {
            Track track = labelOrder.get(i);
            String label = headLabelText(track);
            float x = front.contains(track)
                    ? columnX
                    : Math.min(track.labelHeadX + LABEL_DOT_GAP_PX, clampX);

            PImage logo = teamLogoFor(track.teamAt(Math.min(tDay, track.lastDay)));
            if (logo != null) {
                drawTeamLogo(logo, x, track.labelY, LABEL_LOGO_BOX_PX, track.labelAlpha);
            }

            p.textAlign(Applet.LEFT, Applet.CENTER);
            p.noStroke();
            p.textSize(LABEL_TEXT_SIZE);
            p.fill(0, 0, 0, 62f * track.labelAlpha);
            p.text(label, x + logoSpace + 2f, track.labelY + 2f);
            fillTrack(track, 100f * track.labelAlpha);
            p.text(label, x + logoSpace, track.labelY);
        }
    }

    /** Fit a logo into a square box, centred vertically on the label line. */
    private void drawTeamLogo(PImage logo, float x, float centerY, float boxPx, float alpha) {
        if (logo.width <= 0 || logo.height <= 0) {
            return;
        }
        Applet p = applet();
        float fit = Math.min(boxPx / logo.width, boxPx / logo.height);
        float w = logo.width * fit;
        float h = logo.height * fit;
        // Tint only while actually fading: JAVA2D's tinted-image path
        // re-blits the source per draw and costs real frame time.
        boolean fading = alpha < 0.995f;
        if (fading) {
            p.tint(0, 0, 100, 100f * alpha);
        }
        p.image(logo, x + (boxPx - w) / 2f, centerY - h / 2f, w, h);
        if (fading) {
            p.noTint();
        }
    }

    /**
     * Pre-scale ceiling for loaded logos: comfortably above the largest box
     * they are drawn into (46px header box at pixelDensity 2 = 92 device px,
     * doubled for headroom). The Liquipedia source PNGs are ~3000px — drawing
     * those straight into a 32px label box resamples the full-resolution
     * image EVERY frame, which dropped the export from ~76 to ~14 fps.
     */
    private static final int TEAM_LOGO_MAX_DIM_PX = 200;

    /**
     * Team logos: {@code src/data/cs2/team_logos/<team>.png}, keyed by the
     * CSV team value of the knot at "now" — players carry their era-correct
     * team through transfers. Missing files are skipped quietly.
     */
    private PImage teamLogoFor(String team) {
        if (team == null || team.isEmpty() || missingTeamLogos.contains(team)) {
            return null;
        }
        PImage logo = teamLogos.get(team);
        if (logo == null) {
            Path path = Path.of(TEAM_LOGO_DIR, team + ".png");
            if (!Files.exists(path)) {
                missingTeamLogos.add(team);
                return null;
            }
            logo = applet().loadImage(path.toString());
            if (logo.width > TEAM_LOGO_MAX_DIM_PX || logo.height > TEAM_LOGO_MAX_DIM_PX) {
                if (logo.width >= logo.height) {
                    logo.resize(TEAM_LOGO_MAX_DIM_PX, 0);
                } else {
                    logo.resize(0, TEAM_LOGO_MAX_DIM_PX);
                }
            }
            teamLogos.put(team, logo);
        }
        return logo;
    }

    /** Reference style: {@code Name (1.31)} in the line color. */
    private String headLabelText(Track track) {
        return track.name + " (" + String.format(Locale.ENGLISH, "%.2f", ratingNow(track)) + ")";
    }

    /** Current rating; frozen at the final knot once the track has retired. */
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
     * {@code Leader: [avatar] Name (rating)} with
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

        // The avatar slot is always present: the player's PNG when it
        // exists, the anonymous-silhouette placeholder otherwise.
        PImage avatar = leader != null ? avatarFor(leader) : null;
        PImage teamLogo = leader != null ? teamLogoFor(leader.teamAt(tDay)) : null;
        float avatarSize = 88f;
        float avatarWidth = avatarSize + 20f;
        float teamLogoBox = 46f;
        p.textSize(48);
        float prefixWidth = p.textWidth(prefix);
        float titleWidth = p.textWidth(title);
        p.textSize(36);
        float tenureWidth = p.textWidth(tenure);
        float titleBlockWidth = titleWidth + (teamLogo != null ? teamLogoBox + 16f : 0f);
        float contentWidth = prefixWidth + avatarWidth + Math.max(titleBlockWidth, tenureWidth);

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
        if (teamLogo != null) {
            drawTeamLogo(teamLogo, nameX + titleWidth + 16f, y + 27f, teamLogoBox, 1f);
        }

        p.textSize(36);
        p.fill(0, 0, 92, 96);
        p.text(tenure, nameX, y + 58f);
    }

    /**
     * HLTV-style "unknown player" mark for tracks without an avatar PNG:
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

        LocalDate date = dayZero.plusDays((long) Math.floor(Math.min(tDay, endDay)));
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

        // 2DGP-style translucent backing panel.
        p.noStroke();
        p.fill(0, 0, 0, HUD_PANEL_ALPHA);
        p.rect(panelLeft, topY - 12f, rightX + 18f, topY + 112f);

        p.textAlign(Applet.RIGHT, Applet.TOP);
        p.textSize(48);
        p.fill(0, 0, 100, 100);
        p.text("Current Date:", rightX, topY);
        p.textAlign(Applet.CENTER, Applet.TOP);
        p.textSize(41);
        p.fill(0, 0, 92, 96);
        p.text(dateText, headingCenter, topY + 60f);
    }

    /**
     * Optional avatar thumbnails: drop {@code src/data/cs2/avatars/<name>.png}
     * into the repo and it shows up next to the head label and in the leader
     * header. Missing files are simply skipped.
     */
    private PImage avatarFor(Track track) {
        if (!track.avatarChecked) {
            track.avatarChecked = true;
            java.nio.file.Path path = java.nio.file.Path.of("src/data/cs2/avatars", track.name + ".png");
            if (!Files.exists(path)) {
                path = java.nio.file.Path.of("src/data/cs2/avatars",
                        track.name.toLowerCase(Locale.ROOT) + ".png");
            }
            if (Files.exists(path)) {
                track.avatar = applet().loadImage(path.toString());
            }
        }
        return track.avatar;
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

    private static List<Track> loadTracks(String path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not read " + path + " — run tools/generate_cs2_mock_data.py "
                            + "(or tools/scrape_hltv.py) from the repo root first", e);
        }

        Map<String, TrackBuilder> builders = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 5) {
                throw new IllegalStateException(path + " line " + (i + 1) + " is malformed: " + line);
            }
            TrackBuilder builder = builders.computeIfAbsent(parts[0],
                    name -> new TrackBuilder(name, parts[2]));
            builder.teams.add(parts[1]);
            builder.dates.add(LocalDate.parse(parts[3]));
            builder.ratings.add(Double.parseDouble(parts[4]));
        }

        List<Track> tracks = new ArrayList<>();
        for (TrackBuilder builder : builders.values()) {
            tracks.add(builder.build());
        }
        return tracks;
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

    private static final class TrackBuilder {
        private final String name;
        private final String color;
        private final List<String> teams = new ArrayList<>();
        private final List<LocalDate> dates = new ArrayList<>();
        private final List<Double> ratings = new ArrayList<>();

        private TrackBuilder(String name, String color) {
            this.name = name;
            this.color = color;
        }

        private Track build() {
            return new Track(name, Color.fromCss(color), teams, dates, ratings);
        }
    }

    private static final class Track {
        private final String name;
        private final Color color;
        private final LocalDate firstDate;
        private final List<String> teams;
        private final List<LocalDate> dates;
        private final List<Double> ratings;

        private Pchip spline;
        private double[] knotDays;
        private double firstDay;
        private double lastDay;

        private float labelY;
        private float labelTargetY;
        private float labelHeadX;
        private float labelAlpha;
        private boolean labelInitialised;
        private double lastQueueSwapSeconds = Double.NEGATIVE_INFINITY;
        private float strokeBoost;
        private PImage avatar;
        private boolean avatarChecked;

        private Track(String name, Color color, List<String> teams,
                      List<LocalDate> dates, List<Double> ratings) {
            if (dates.size() < 2) {
                throw new IllegalStateException("Track " + name + " needs at least two knots");
            }
            this.name = name;
            this.color = color;
            this.teams = teams;
            this.dates = dates;
            this.ratings = ratings;
            this.firstDate = dates.get(0);
        }

        private void rebase(LocalDate dayZero) {
            double[] xs = new double[dates.size()];
            double[] ys = new double[dates.size()];
            for (int i = 0; i < dates.size(); i++) {
                xs[i] = ChronoUnit.DAYS.between(dayZero, dates.get(i));
                ys[i] = ratings.get(i);
            }
            spline = new Pchip(xs, ys);
            knotDays = xs;
            firstDay = xs[0];
            lastDay = xs[xs.length - 1];
        }

        /** Team of the most recent knot at or before {@code day}. */
        private String teamAt(double day) {
            int index = java.util.Arrays.binarySearch(knotDays, day);
            if (index < 0) {
                index = -index - 2; // insertion point - 1 = last knot <= day
            }
            return teams.get(Math.max(0, Math.min(index, teams.size() - 1)));
        }

        private boolean isActiveAt(double day) {
            return day >= firstDay && day <= lastDay + RANK_GRACE_DAYS;
        }
    }
}
