package directions.scenes;

import core.Applet;
import directions.engine.Action;
import directions.engine.Actions;
import directions.engine.Nodes;
import directions.engine.Scene;
import directions.engine.SceneContext;
import geom.DataGrid;
import storage.Color;

public final class TestScene extends Scene {
    private static final double INITIAL_X_MIN = 0;
    private static final double VISIBLE_X_SPAN = 24;
    private static final double GRAPH_MAX_X = 84;
    private static final double FINAL_X_SPAN = GRAPH_MAX_X - INITIAL_X_MIN;
    private static final double Y_MIN = 800;
    private static final double Y_MAX = 1800;
    private static final double FOLLOW_THRESHOLD_RATIO = 0.85;
    private static final double FINAL_ZOOM_DELAY = 0.5;
    private static final double FINAL_ZOOM_DURATION = 4.25;
    private static final double GRAPH_HEAD_SPEED = 3.2;
    private static final double GRAPH_SAMPLE_STEP = 0.05;
    private static final double SINE_BASELINE = 1280;
    private static final double SINE_AMPLITUDE = 260;
    private static final double SINE_FREQUENCY = 0.58;

    private final DataGrid grid;
    private final Color graphColor = Color.fromCss("#6edbff");
    private final Color headColor = Color.fromCss("#f6fbff");

    private double visibleXMin = INITIAL_X_MIN;
    private double visibleXSpan = VISIBLE_X_SPAN;
    private double revealX;
    private double zoomOutElapsed;
    private double zoomOutStartXMin;
    private double zoomOutStartXSpan = VISIBLE_X_SPAN;
    private boolean zoomOutStarted;

    public TestScene(Applet p) {
        super(p);
        grid = new DataGrid(applet());
        grid.setDomain(INITIAL_X_MIN, INITIAL_X_MIN + VISIBLE_X_SPAN, Y_MIN, Y_MAX);
        grid.setAnchor(0, Y_MIN);
        grid.setXMajorStep(3);
        grid.setYMajorStep(100);
    }

    @Override
    protected void onReset() {
        visibleXMin = INITIAL_X_MIN;
        visibleXSpan = VISIBLE_X_SPAN;
        revealX = INITIAL_X_MIN;
        zoomOutElapsed = 0;
        zoomOutStartXMin = INITIAL_X_MIN;
        zoomOutStartXSpan = VISIBLE_X_SPAN;
        zoomOutStarted = false;
        grid.setDomain(INITIAL_X_MIN, INITIAL_X_MIN + VISIBLE_X_SPAN, Y_MIN, Y_MAX);
    }

    @Override
    protected Action build() {
        addUpdater(this::updateSineFollow);
        addNode(Nodes.of(grid::render));
        addNode(Nodes.of(this::renderSineGraph));

        return Actions.waitSeconds(500);
    }

    private void updateSineFollow(SceneContext ctx) {
        revealX = Math.min(GRAPH_MAX_X, revealX + GRAPH_HEAD_SPEED * ctx.dt());

        if (revealX >= GRAPH_MAX_X) {
            updateFinalZoom(ctx);
        } else {
            visibleXSpan = VISIBLE_X_SPAN;
            double followStart = visibleXMin + visibleXSpan * FOLLOW_THRESHOLD_RATIO;
            if (revealX > followStart) {
                visibleXMin = revealX - visibleXSpan * FOLLOW_THRESHOLD_RATIO;
            }
        }

        grid.setXRange(visibleXMin, visibleXMin + visibleXSpan);
    }

    private void updateFinalZoom(SceneContext ctx) {
        if (!zoomOutStarted) {
            zoomOutStarted = true;
            zoomOutElapsed = 0;
            zoomOutStartXMin = visibleXMin;
            zoomOutStartXSpan = visibleXSpan;
        }

        zoomOutElapsed += ctx.dt();
        double progress = clamp01((zoomOutElapsed - FINAL_ZOOM_DELAY) / FINAL_ZOOM_DURATION);
        double easedProgress = smoothstep(progress);
        visibleXMin = interpolate(zoomOutStartXMin, INITIAL_X_MIN, easedProgress);
        visibleXSpan = interpolate(zoomOutStartXSpan, FINAL_X_SPAN, easedProgress);
    }

    private void renderSineGraph() {
        double drawMaxX = Math.min(revealX, GRAPH_MAX_X);
        if (drawMaxX <= INITIAL_X_MIN) {
            return;
        }

        double drawStartX = Math.max(INITIAL_X_MIN, visibleXMin - GRAPH_SAMPLE_STEP);

        Applet p = applet();
        p.noFill();
        p.stroke(graphColor);
        p.strokeWeight(5f);
        p.beginShape();

        // Sample in domain space so the curve scales cleanly with the chart window.
        for (double x = drawStartX; x <= drawMaxX; x += GRAPH_SAMPLE_STEP) {
            p.vertex(grid.domainToCanvasX(x), grid.domainToCanvasY(sineValue(x)));
        }

        if (drawMaxX - drawStartX > GRAPH_SAMPLE_STEP * 0.5) {
            p.vertex(grid.domainToCanvasX(drawMaxX), grid.domainToCanvasY(sineValue(drawMaxX)));
        }
        p.endShape();

        float headX = grid.domainToCanvasX(drawMaxX);
        float headY = grid.domainToCanvasY(sineValue(drawMaxX));
        p.noStroke();
        p.fill(headColor);
        p.circle(headX, headY, 12f);
    }

    private double sineValue(double x) {
        return SINE_BASELINE + SINE_AMPLITUDE * Math.sin(x * SINE_FREQUENCY);
    }

    private double interpolate(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private double smoothstep(double value) {
        return value * value * (3 - 2 * value);
    }
}
