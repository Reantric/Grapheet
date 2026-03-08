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
    private static final double Y_MIN = 800;
    private static final double Y_MAX = 1800;
    private static final double FOLLOW_THRESHOLD_RATIO = 0.85;
    private static final double GRAPH_HEAD_SPEED = 3.2;
    private static final double GRAPH_SAMPLE_STEP = 0.05;
    private static final double GRAPH_MAX_X = 84;
    private static final double SINE_BASELINE = 1280;
    private static final double SINE_AMPLITUDE = 260;
    private static final double SINE_FREQUENCY = 0.58;

    private final DataGrid grid;
    private final Color graphColor = Color.fromCss("#6edbff");
    private final Color headColor = Color.fromCss("#f6fbff");

    private double visibleXMin = INITIAL_X_MIN;
    private double revealX;

    public TestScene(Applet p) {
        super(p);
        grid = new DataGrid(applet());
        grid.setDomain(INITIAL_X_MIN, INITIAL_X_MIN + VISIBLE_X_SPAN, Y_MIN, Y_MAX);
        grid.setAnchor(0, Y_MIN);
        grid.setXMajorStep(3);
        grid.setYMajorStep(150);
        grid.setMinorDivisions(2, 2);
    }

    @Override
    protected void onReset() {
        visibleXMin = INITIAL_X_MIN;
        revealX = INITIAL_X_MIN;
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

        double followStart = visibleXMin + VISIBLE_X_SPAN * FOLLOW_THRESHOLD_RATIO;
        if (revealX > followStart) {
            visibleXMin = revealX - VISIBLE_X_SPAN * FOLLOW_THRESHOLD_RATIO;
        }

        grid.setXRange(visibleXMin, visibleXMin + VISIBLE_X_SPAN);
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
}
