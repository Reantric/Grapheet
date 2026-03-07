package directions.engine;

import util.map.MapEase;
import util.map.MapType;

public final class MotionDefaults {
    public static final Easing STANDARD = Easings.from(MapType.QUADRATIC, MapEase.EASE_IN_OUT);

    private MotionDefaults() {
    }
}
