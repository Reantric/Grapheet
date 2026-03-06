package directions.engine;

import core.Applet;

public final class SceneContext {
    private static final float MAX_DT_SECONDS = 0.1f;

    private final Applet applet;
    private int lastMillis = -1;
    private float deltaSeconds;
    private float elapsedSeconds;

    public SceneContext(Applet applet) {
        this.applet = applet;
    }

    void advanceFrame() {
        int now = applet.millis();
        if (lastMillis < 0) {
            deltaSeconds = 0f;
        } else {
            float dt = (now - lastMillis) / 1000f;
            if (dt < 0f) {
                dt = 0f;
            }
            deltaSeconds = Math.min(dt, MAX_DT_SECONDS);
            elapsedSeconds += deltaSeconds;
        }
        lastMillis = now;
    }

    void resetClock() {
        lastMillis = -1;
        deltaSeconds = 0f;
        elapsedSeconds = 0f;
    }

    public Applet applet() {
        return applet;
    }

    public float dt() {
        return deltaSeconds;
    }

    public float elapsed() {
        return elapsedSeconds;
    }

    public int frame() {
        return applet.frameCount;
    }
}
