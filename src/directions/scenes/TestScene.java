package directions.scenes;

import core.Applet;
import directions.engine.Action;
import directions.engine.Actions;
import directions.engine.Nodes;
import directions.engine.Scene;
import directions.engine.Values;
import geom.Grid;
import storage.Color;

public final class TestScene extends Scene {
    private final Grid plane;

    public TestScene(Applet p) {
        super(p);
        plane = new Grid(applet());
        plane.setColor(Color.fromCss("#088efc"));
    }

    @Override
    protected Action build() {
        addNode(Nodes.of(plane::render));
        
        return Actions.sequence(
            Actions.parallel(
                        Actions.tween(Values.of(plane.getSpacing()), halfViewportWidth(), halfViewportHeight(), 1.0),
                        Actions.tween(Values.of(plane.getTextColor().getAlpha()), 100f, 1.0),
                        Actions.tween(Values.of(plane.getGridSpacing()), 150f, 150f, 3.6)
                ),
            
            Actions.waitSeconds(3)
        );
    }
}
