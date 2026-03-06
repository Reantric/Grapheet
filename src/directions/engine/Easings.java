package directions.engine;

import util.Mapper;
import util.map.MapEase;
import util.map.MapType;

public final class Easings {
    public static final Easing LINEAR = Easings::clamp;

    public static final Easing SMOOTHSTEP = progress -> {
        float t = clamp(progress);
        return t * t * (3f - (2f * t));
    };

    private Easings() {
    }

    public static Easing from(MapType type, MapEase ease) {
        return progress -> (float) Mapper.map2(clamp(progress), 0.0, 1.0, 0.0, 1.0, type, ease);
    }

    private static float clamp(float progress) {
        return Math.max(0f, Math.min(1f, progress));
    }
}
