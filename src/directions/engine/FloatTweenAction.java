package directions.engine;

import processing.core.PApplet;

import java.util.Objects;

final class FloatTweenAction extends ActionBase {
    private final FloatValue value;
    private final float target;
    private final float durationSeconds;
    private final Easing easing;

    private float start;
    private float elapsedSeconds;

    FloatTweenAction(FloatValue value, float target, float durationSeconds, Easing easing) {
        this.value = Objects.requireNonNull(value, "value");
        this.target = target;
        this.durationSeconds = Math.max(0f, durationSeconds);
        this.easing = Objects.requireNonNull(easing, "easing");
    }

    @Override
    protected void onBegin(SceneContext ctx) {
        start = value.get();
        if (durationSeconds == 0f) {
            value.set(target);
        }
    }

    @Override
    protected boolean onUpdate(SceneContext ctx) {
        if (durationSeconds == 0f) {
            return true;
        }

        elapsedSeconds = Math.min(durationSeconds, elapsedSeconds + ctx.dt());
        float progress = easing.apply(elapsedSeconds / durationSeconds);
        value.set(PApplet.lerp(start, target, progress));
        if (elapsedSeconds >= durationSeconds) {
            value.set(target);
            return true;
        }
        return false;
    }

    @Override
    protected void onReset() {
        elapsedSeconds = 0f;
    }
}
