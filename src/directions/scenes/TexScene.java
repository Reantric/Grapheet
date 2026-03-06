package directions.scenes;

import core.Applet;
import directions.engine.Action;
import directions.engine.Actions;
import directions.engine.Easings;
import directions.engine.Nodes;
import directions.engine.Scene;
import directions.engine.Values;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import text.ImmutableTex;
import util.map.MapEase;
import util.map.MapType;

public final class TexScene extends Scene {
    private static final String MATRIX_TEX = """
             \\[\\begin{bmatrix}
                               1 & -1 & 0 \\\\
                               1 & -1 & 1\\\\
                               0 & 1 & 1
                              \\end{bmatrix} \\begin{bmatrix}
                               4 \\\\
                               2 \\\\
                               2
                              \\end{bmatrix} \\]""";

    private final ImmutableTex primary;
    private final ImmutableTex secondary;

    public TexScene(Applet applet) {
        super(applet);
        primary = new ImmutableTex(applet, MATRIX_TEX, new Color(ColorType.YELLOW));
        secondary = new ImmutableTex(applet, MATRIX_TEX, new Color(ColorType.CYAN));
        primary.setScale(new Vector(1.4f, 1.4f));
        secondary.setScale(new Vector(1.4f, 1.4f));
    }

    @Override
    protected Action build() {
        Vector primaryPos = new Vector(-760f, -240f);
        Vector secondaryStart = new Vector(-120f, 120f);
        Vector secondaryEnd = new Vector(-940f, -520f);

        primary.setPos(new Vector(primaryPos));
        secondary.setPos(new Vector(secondaryStart));
        primary.getColor().setAlpha(0);
        secondary.getColor().setAlpha(0);

        addNodes(
                Nodes.of(primary::render),
                Nodes.of(secondary::render)
        );

        return Actions.sequence(
                Actions.tween(
                        Values.of(primary.getColor().getAlpha()),
                        100f,
                        0.8,
                        Easings.from(MapType.QUADRATIC, MapEase.EASE_IN_OUT)
                ),
                Actions.parallel(
                        Actions.tween(
                                Values.of(secondary.getColor().getAlpha()),
                                100f,
                                0.8,
                                Easings.from(MapType.QUADRATIC, MapEase.EASE_IN_OUT)
                        ),
                        Actions.tween(
                                Values.of(secondary.getPos()),
                                secondaryEnd,
                                1.2,
                                Easings.from(MapType.CUBIC, MapEase.EASE_OUT)
                        )
                ),
                Actions.waitSeconds(1.0)
        );
    }
}
