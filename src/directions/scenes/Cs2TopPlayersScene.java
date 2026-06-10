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
 * the end. Top left shows a reference-style leader header, top right the
 * simulated date. Optional avatar thumbnails are picked up from
 * {@code src/data/cs2/avatars/<player>.png}.
 */
public final class Cs2TopPlayersScene extends Scene {
    private static final String DATA_PATH = "src/data/cs2/top_players_rolling.csv";
    private static final double DEFAULT_MS_PER_DAY = 100;
    private static final double WINDOW_DAYS = 365;
    /** The race head rides at this fraction of the window — close to the
     *  right edge so the dots and name labels live in the right margin. */
    private static final double FOLLOW_THRESHOLD_RATIO = 0.86;
    private static final double FINAL_ZOOM_DELAY = 0.6;
    private static final double FINAL_ZOOM_DURATION = 5.0;
    private static final double END_HOLD_SECONDS = 4.0;
    private static final double Y_BASE_STEP = 0.05;
    private static final double Y_MIN_SPAN = 0.18;
    private static final double Y_FIT_SAMPLE_DAYS = 5.0;
    private static final float Y_FIT_EASE_RATE = 2.2f;
    private static final float LABEL_EASE_RATE = 9f;
    private static final float LABEL_MIN_GAP_PX = 38f;
    private static final float RETIRE_LINE_FADE_DAYS = 60f;
    private static final float RETIRE_LABEL_FADE_SECONDS = 1.6f;
    /**
     * Tracks whose data ends within this many days of "now" still count for
     * ranking/labels. Without it, players whose last knot lands a few days
     * before the global end of the dataset would all "retire" on the final
     * frames and hand #1 to whoever happens to have the latest knot.
     */
    private static final double RANK_GRACE_DAYS = 45;
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
    private double visibleXMin;
    private double visibleXSpan = WINDOW_DAYS;
    private double yShownMin = 1.0;
    private double yShownMax = 1.4;
    private boolean yFitInitialised;

    private Track leader;
    private double leaderSinceDay;

    private boolean zoomOutStarted;
    private double zoomOutElapsed;
    private double zoomOutStartXMin;
    private double zoomOutStartXSpan;
    private double endHoldElapsed;

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
        grid.setYLabelFormatter(value -> String.format(Locale.ENGLISH, "%.2f", value));
        grid.setDomain(0, WINDOW_DAYS, yShownMin, yShownMax);
    }

    @Override
    protected void onReset() {
        tDay = 0;
        visibleXMin = 0;
        visibleXSpan = WINDOW_DAYS;
        yShownMin = 1.0;
        yShownMax = 1.4;
        yFitInitialised = false;
        leader = null;
        leaderSinceDay = 0;
        zoomOutStarted = false;
        zoomOutElapsed = 0;
        endHoldElapsed = 0;
        for (Track track : tracks) {
            track.labelInitialised = false;
            track.labelAlpha = 0f;
            track.strokeBoost = 0f;
        }
        grid.setDomain(0, WINDOW_DAYS, yShownMin, yShownMax);
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
        tDay = Math.min(endDay, tDay + dt * 1000.0 / msPerDay);

        if (tDay >= endDay) {
            updateFinalZoom(dt);
        } else {
            double followStart = visibleXMin + visibleXSpan * FOLLOW_THRESHOLD_RATIO;
            if (tDay > followStart) {
                visibleXMin = tDay - visibleXSpan * FOLLOW_THRESHOLD_RATIO;
            }
        }
        grid.setXRange(visibleXMin, visibleXMin + visibleXSpan);

        updateRanking(dt);
        updateYFit(dt);
    }

    private void updateFinalZoom(double dt) {
        if (!zoomOutStarted) {
            zoomOutStarted = true;
            zoomOutElapsed = 0;
            zoomOutStartXMin = visibleXMin;
            zoomOutStartXSpan = visibleXSpan;
        }
        zoomOutElapsed += dt;
        double progress = clamp01((zoomOutElapsed - FINAL_ZOOM_DELAY) / FINAL_ZOOM_DURATION);
        double eased = smoothstep(progress);
        visibleXMin = interpolate(zoomOutStartXMin, 0, eased);
        visibleXSpan = interpolate(zoomOutStartXSpan, endDay + 14, eased);
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
            for (double d = lo; d <= hi + 1e-9; d += Y_FIT_SAMPLE_DAYS) {
                double v = track.spline.value(Math.min(d, hi));
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        if (min > max) {
            return; // nothing visible yet — keep the current window
        }

        double span = Math.max(Y_MIN_SPAN, (max - min) / 0.62);
        // Generous headroom up top so the race stays clear of the leader header.
        double targetMax = max + span * 0.26;
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
        float plotBottom = plotTop + grid.getPlotHeight();

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
            p.strokeWeight(4f + 1.8f * track.strokeBoost);
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
                p.circle(hx, hy, 13f + 3f * track.strokeBoost);
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
        List<Track> visible = new ArrayList<>();
        for (Track track : tracks) {
            float targetAlpha = track.isActiveAt(tDay) && tDay >= track.firstDay ? 1f : 0f;
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
        }

        // The queue: labels are ranked by current rating and spread to the
        // minimum gap in that order — first on the targets, then again on the
        // displayed positions after easing, so two labels can never render
        // intersecting. On an overtake the ranks swap, the pair squeezes to
        // exactly the gap and slides past — the smooth name flip.
        visible.sort((a, b) -> Double.compare(
                b.spline.value(Math.min(tDay, b.lastDay)),
                a.spline.value(Math.min(tDay, a.lastDay))));

        float queueTop = plotTop + 26f;
        float queueBottom = plotBottom - 22f;
        float[] targets = new float[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            targets[i] = visible.get(i).labelTargetY;
        }
        stackWithGaps(targets, queueTop, queueBottom);
        for (int i = 0; i < visible.size(); i++) {
            visible.get(i).labelTargetY = targets[i];
        }

        float[] shown = new float[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            Track track = visible.get(i);
            if (!track.labelInitialised) {
                track.labelY = track.labelTargetY;
                track.labelInitialised = true;
            } else {
                track.labelY = ease(track.labelY, track.labelTargetY, dt, LABEL_EASE_RATE);
            }
            shown[i] = track.labelY;
        }
        stackWithGaps(shown, queueTop, queueBottom);
        for (int i = 0; i < visible.size(); i++) {
            visible.get(i).labelY = shown[i];
        }

        // One shared column for every label: near the end of the dataset the
        // heads stop at slightly different last knots, so per-head x would
        // split the stack into ragged clusters. Anchor the column on the
        // midpoint of the active heads instead. Retired lines (whose heads
        // sit far back) keep their own x while their label fades out. Labels
        // may run past the plot into the right margin — only the viewport
        // edge clips them.
        p.textSize(30);
        boolean anyAvatar = false;
        float maxLabelWidth = 0f;
        float minHeadX = Float.POSITIVE_INFINITY;
        float maxHeadX = Float.NEGATIVE_INFINITY;
        for (Track track : visible) {
            maxLabelWidth = Math.max(maxLabelWidth, p.textWidth(headLabelText(track)));
            anyAvatar |= avatarFor(track) != null;
            if (track.isActiveAt(tDay)) {
                minHeadX = Math.min(minHeadX, track.labelHeadX);
                maxHeadX = Math.max(maxHeadX, track.labelHeadX);
            }
        }
        float avatarSpace = anyAvatar ? 40f : 0f;
        float clampX = halfViewportWidth() - maxLabelWidth - avatarSpace - 24f;
        float columnX = minHeadX <= maxHeadX
                ? Math.min((minHeadX + maxHeadX) / 2f + 16f, clampX)
                : clampX;

        for (Track track : visible) {
            String label = headLabelText(track);
            float x = track.isActiveAt(tDay)
                    ? columnX
                    : Math.min(track.labelHeadX + 16f, clampX);

            PImage avatar = avatarFor(track);
            if (avatar != null) {
                p.tint(0, 0, 100, 100f * track.labelAlpha);
                p.image(avatar, x, track.labelY - 17f, 34f, 34f);
                p.noTint();
            }

            p.textAlign(Applet.LEFT, Applet.CENTER);
            p.noStroke();
            p.textSize(30);
            p.fill(0, 0, 0, 62f * track.labelAlpha);
            p.text(label, x + avatarSpace + 2f, track.labelY + 2f);
            fillTrack(track, 100f * track.labelAlpha);
            p.text(label, x + avatarSpace, track.labelY);
        }
    }

    /** Reference style: {@code Name (1.31)} in the line color. */
    private String headLabelText(Track track) {
        return track.name + " (" + String.format(Locale.ENGLISH, "%.2f",
                track.spline.value(Math.min(tDay, track.lastDay))) + ")";
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
        if (leader == null) {
            return;
        }
        Applet p = applet();
        ensureFont();
        p.textFont(font);

        float x = grid.getPlotLeft() + 28f;
        float y = -halfViewportHeight() + 24f;

        String prefix = "Leader:  ";
        String rating = String.format(Locale.ENGLISH, "%.2f", leader.spline.value(tDay));
        String title = leader.name + " (" + rating + ")";
        int days = (int) Math.max(0, Math.floor(tDay - leaderSinceDay));
        String tenure = "For " + days + (days == 1 ? " day" : " days")
                + String.format(Locale.ENGLISH, " (~%.2f years)", days / 365.25);

        PImage avatar = avatarFor(leader);
        float avatarWidth = avatar != null ? 88f + 20f : 0f;
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
            p.image(avatar, nameX, y - 6f, 88f, 88f);
            nameX += avatarWidth;
        }

        p.textSize(48);
        p.fill(0, 0, 100, 100);
        p.text(title, nameX, y);

        p.textSize(36);
        p.fill(0, 0, 92, 96);
        p.text(tenure, nameX, y + 58f);
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
        float panelLeft = rightX - Math.max(headingWidth, dateWidth) - 18f;

        // 2DGP-style translucent backing panel.
        p.noStroke();
        p.fill(0, 0, 0, HUD_PANEL_ALPHA);
        p.rect(panelLeft, topY - 12f, rightX + 18f, topY + 112f);

        p.textAlign(Applet.RIGHT, Applet.TOP);
        p.textSize(48);
        p.fill(0, 0, 100, 100);
        p.text("Current Date:", rightX, topY);
        p.textSize(41);
        p.fill(0, 0, 92, 96);
        p.text(dateText, rightX, topY + 60f);
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
            // The reference look is Lato; fall back through common sans faces
            // so the scene renders the same everywhere without bundling a font.
            font = applet().createFont(pickFontFace(
                    "Lato Bold", "Lato", "Helvetica Neue Bold", "HelveticaNeue-Bold",
                    "Arial Bold", "DejaVu Sans Bold", "Verdana Bold"), 150, true);
            grid.setLabelFont(applet().createFont(pickFontFace(
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
                    name -> new TrackBuilder(name, parts[1], parts[2]));
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
        private final String team;
        private final String color;
        private final List<LocalDate> dates = new ArrayList<>();
        private final List<Double> ratings = new ArrayList<>();

        private TrackBuilder(String name, String team, String color) {
            this.name = name;
            this.team = team;
            this.color = color;
        }

        private Track build() {
            return new Track(name, team, Color.fromCss(color), dates, ratings);
        }
    }

    private static final class Track {
        private final String name;
        private final String team;
        private final Color color;
        private final LocalDate firstDate;
        private final List<LocalDate> dates;
        private final List<Double> ratings;

        private Pchip spline;
        private double firstDay;
        private double lastDay;

        private float labelY;
        private float labelTargetY;
        private float labelHeadX;
        private float labelAlpha;
        private boolean labelInitialised;
        private float strokeBoost;
        private PImage avatar;
        private boolean avatarChecked;

        private Track(String name, String team, Color color,
                      List<LocalDate> dates, List<Double> ratings) {
            if (dates.size() < 2) {
                throw new IllegalStateException("Track " + name + " needs at least two knots");
            }
            this.name = name;
            this.team = team;
            this.color = color;
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
            firstDay = xs[0];
            lastDay = xs[xs.length - 1];
        }

        private boolean isActiveAt(double day) {
            return day >= firstDay && day <= lastDay + RANK_GRACE_DAYS;
        }
    }
}
