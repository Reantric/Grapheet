package directions.scenes;

import core.Applet;
import directions.engine.Action;
import directions.engine.Actions;
import directions.engine.Nodes;
import directions.engine.Scene;
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
        return Actions.noop();
    }
}
