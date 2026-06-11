package directions.engine;

import core.Applet;

public final class SceneContext {
    private static final float MAX_DT_SECONDS = 0.1f;

    private final Applet applet;
    private final float fixedDeltaSeconds;
    private int lastMillis = -1;
    private boolean fixedClockStarted;
    private float deltaSeconds;
    private float elapsedSeconds;

    public SceneContext(Applet applet) {
        this.applet = applet;
        float exportFps = configuredExportFps();
        this.fixedDeltaSeconds = exportFps > 0f ? 1f / exportFps : 0f;
    }

    void advanceFrame() {
        if (fixedDeltaSeconds > 0f) {
            // Offline export: a constant sim step per frame keeps the
            // recording's timeline independent of how fast frames draw.
            deltaSeconds = fixedClockStarted ? fixedDeltaSeconds : 0f;
            fixedClockStarted = true;
            elapsedSeconds += deltaSeconds;
            return;
        }
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
        fixedClockStarted = false;
        deltaSeconds = 0f;
        elapsedSeconds = 0f;
    }

    /** Frames per second of the offline export timeline, or 0 for the realtime wall clock. */
    public static float configuredExportFps() {
        String raw = System.getProperty("exportFps", "").trim();
        if (raw.isEmpty()) {
            return 0f;
        }
        float parsed = Float.parseFloat(raw);
        if (parsed < 1f / MAX_DT_SECONDS) {
            // Below this the fixed step exceeds the realtime clock's dt clamp,
            // so the export could not match any realtime run frame-for-frame.
            throw new IllegalArgumentException(
                    "exportFps must be at least " + (int) (1f / MAX_DT_SECONDS) + ": " + raw);
        }
        return parsed;
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
