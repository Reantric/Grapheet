package directions.engine;

import processing.core.PApplet;

import java.util.Objects;

final class VectorTweenAction extends ActionBase {
    private final VectorValue value;
    private final float targetX;
    private final float targetY;
    private final float durationSeconds;
    private final Easing easing;

    private float startX;
    private float startY;
    private float elapsedSeconds;

    VectorTweenAction(VectorValue value, float targetX, float targetY, float durationSeconds, Easing easing) {
        this.value = Objects.requireNonNull(value, "value");
        this.targetX = targetX;
        this.targetY = targetY;
        this.durationSeconds = Math.max(0f, durationSeconds);
        this.easing = Objects.requireNonNull(easing, "easing");
    }

    @Override
    protected void onBegin(SceneContext ctx) {
        startX = value.x();
        startY = value.y();
        if (durationSeconds == 0f) {
            value.set(targetX, targetY);
        }
    }

    @Override
    protected boolean onUpdate(SceneContext ctx) {
        if (durationSeconds == 0f) {
            return true;
        }

        elapsedSeconds = Math.min(durationSeconds, elapsedSeconds + ctx.dt());
        float progress = easing.apply(elapsedSeconds / durationSeconds);
        value.set(
                PApplet.lerp(startX, targetX, progress),
                PApplet.lerp(startY, targetY, progress)
        );

        if (elapsedSeconds >= durationSeconds) {
            value.set(targetX, targetY);
            return true;
        }
        return false;
    }

    @Override
    protected void onReset() {
        elapsedSeconds = 0f;
    }
}
