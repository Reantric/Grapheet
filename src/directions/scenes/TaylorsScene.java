package directions.scenes;

import core.Applet;
import core.ShapeWrapper;
import directions.engine.Action;
import directions.engine.Actions;
import directions.engine.Nodes;
import directions.engine.Scene;
import directions.engine.Values;
import geom.Grid;
import geom.curve.Graph;
import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction;
import org.apache.commons.math3.analysis.function.Sin;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import text.ImmutableTex;

import java.util.ArrayList;
import java.util.List;

public final class TaylorsScene extends Scene {
    private final UnivariateDifferentiableFunction function = new Sin();
    private final List<DerivativeStructure> taylorValues = new ArrayList<>();

    private Grid plane;
    private Graph sin;
    private Graph taylor;
    private ImmutableTex sinText;
    private ImmutableTex taylorText;

    public TaylorsScene(Applet applet) {
        super(applet);
        for (int i = 0; i < 5; i++) {
            taylorValues.add(function.value(new DerivativeStructure(1, 2 * i + 1, 0, 0)));
        }
    }

    @Override
    protected Action build() {
        initializeSceneObjects();

        addNodes(
                Nodes.of(plane::render),
                Nodes.of(() -> {
                    if (sin != null) {
                        sin.render();
                    }
                }),
                Nodes.of(() -> {
                    if (taylor != null) {
                        taylor.render();
                    }
                }),
                Nodes.of(sinText::render),
                Nodes.of(taylorText::render)
        );

        return Actions.sequence(
                Actions.parallel(
                        Actions.tween(Values.of(plane.getSpacing()), halfViewportWidth(), halfViewportHeight(), 1.0),
                        Actions.tween(Values.of(plane.getTextColor().getAlpha()), 100f, 1.0),
                        Actions.tween(Values.of(plane.getIncrementor()), 150f, 150f, 3.6)
                ),
                Actions.call(this::showSinGraph),
                Actions.parallel(
                        Actions.update(() -> sin.advanceReveal(3f)),
                        Actions.tween(Values.of(sinText.getColor().getAlpha()), 100f, 1.0)
                ),
                Actions.call(this::showTaylorGraph),
                Actions.parallel(
                        Actions.update(() -> taylor.advanceReveal(3f)),
                        Actions.tween(Values.of(taylorText.getColor().getAlpha()), 100f, 1.0)
                ),
                Actions.call(() -> taylorText.setBoundingBoxIndex(8)),
                Actions.parallel(
                        fadeIn(taylorText.getSubtex(3, 8)),
                        Actions.update(() -> taylor.interpolate(taylorValues.get(1)::taylor)),
                        Actions.waitSeconds(2.0)
                ),
                Actions.call(() -> taylorText.setBoundingBoxIndex(14)),
                Actions.parallel(
                        fadeIn(taylorText.getSubtex(8, 15)),
                        Actions.update(() -> taylor.interpolate(taylorValues.get(2)::taylor))
                ),
                Actions.call(taylorText::setBoundingBoxIndex),
                Actions.parallel(
                        fadeIn(taylorText.getSubtex(15)),
                        Actions.update(() -> taylor.interpolate(taylorValues.get(3)::taylor))
                ),
                Actions.waitSeconds(1.0)
        );
    }

    private void initializeSceneObjects() {
        plane = new Grid(applet());
        plane.setColor(new Color(207, 97, 99));
        sin = null;
        taylor = null;

        sinText = new ImmutableTex(applet(), "y = \\sin(x)");
        sinText.setColor(new Color(ColorType.GREEN), true);
        sinText.setScale(new Vector(2, 2));

        taylorText = new ImmutableTex(applet(), "y = x - \\frac{x^3}{3!} + \\frac{x^5}{5!} - \\frac{x^7}{7!}");
        taylorText.setColor(new Color(ColorType.MAGENTA), true);
        taylorText.getSubtex(3).forEach(shape -> {
            Color color = new Color(shape.getColor());
            color.setAlpha(0);
            shape.setColor(color);
        });
        taylorText.setBoundingBoxIndex(3);
    }

    private void showSinGraph() {
        sin = plane.graph(function::value);
        sinText.setPos(plane.planeToCanvas(new Vector(-5, 2.4f)));
    }

    private void showTaylorGraph() {
        taylor = plane.graph(taylorValues.get(0)::taylor);
        taylor.setColor(new Color(ColorType.MAGENTA));
        taylorText.setPos(plane.planeToCanvas(new Vector(3, 2)));
    }

    private Action fadeIn(List<ShapeWrapper> shapes) {
        return Actions.update(() -> {
            boolean finished = true;
            for (ShapeWrapper shape : shapes) {
                finished &= shape.getColor().getAlpha().interpolate(100);
            }
            return finished;
        });
    }
}
