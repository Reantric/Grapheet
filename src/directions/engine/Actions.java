package directions.engine;

import storage.Vector;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class Actions {
    private Actions() {
    }

    public static Action noop() {
        return new NoopAction();
    }

    public static Action call(Runnable callback) {
        return new CallAction(Objects.requireNonNull(callback, "callback"));
    }

    public static Action waitSeconds(double seconds) {
        return new WaitAction((float) seconds);
    }

    public static Action sequence(Action... actions) {
        return new SequenceAction(actions);
    }

    public static Action parallel(Action... actions) {
        return new ParallelAction(actions);
    }

    public static Action legacy(BooleanSupplier tickUntilDone) {
        return new LegacyAction(Objects.requireNonNull(tickUntilDone, "tickUntilDone"));
    }

    public static Action tween(FloatValue value, float target, double seconds) {
        return tween(value, target, seconds, Easings.SMOOTHSTEP);
    }

    public static Action tween(FloatValue value, float target, double seconds, Easing easing) {
        return new FloatTweenAction(value, target, (float) seconds, easing);
    }

    public static Action tween(VectorValue value, float targetX, float targetY, double seconds) {
        return tween(value, targetX, targetY, seconds, Easings.SMOOTHSTEP);
    }

    public static Action tween(VectorValue value, float targetX, float targetY, double seconds, Easing easing) {
        return new VectorTweenAction(value, targetX, targetY, (float) seconds, easing);
    }

    public static Action tween(VectorValue value, Vector target, double seconds) {
        return tween(value, target.x, target.y, seconds, Easings.SMOOTHSTEP);
    }

    public static Action tween(VectorValue value, Vector target, double seconds, Easing easing) {
        return tween(value, target.x, target.y, seconds, easing);
    }
}
