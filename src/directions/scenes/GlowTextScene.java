package directions.scenes;

import core.Applet;
import directions.engine.Action;
import directions.engine.Actions;
import directions.engine.Scene;
import directions.engine.SceneContext;

import processing.core.PConstants;
import processing.core.PFont;
import processing.opengl.PShader;

/**
 * Animated "pop up" text rendered through a rainbow-glow fragment shader.
 *
 * <p>Each frame draws the message as plain white text on the (already black)
 * canvas, then runs {@code src/shaders/rainbow_glow.frag} as a full-screen
 * {@link Applet#filter(PShader) filter}: the shader reads the framebuffer,
 * uses luminance as the glyph mask, and paints a flowing rainbow plus a soft
 * glow halo. The entrance (scale overshoot + fade-in) is driven directly from
 * {@link SceneContext} elapsed time, so it needs no engine tweens.
 *
 * <p>Shaders only exist under the P2D (OpenGL) renderer, so run with:
 * <pre>./gradlew runGlowTextScene -Drenderer=P2D -Dfullscreen=false</pre>
 * Under JAVA2D the scene falls back to plain popped text and prints a hint.
 *
 * <p>The message is overridable: {@code -Dtext="hello world"}.
 */
public final class GlowTextScene extends Scene {
    private static final String SHADER_PATH = "src/shaders/rainbow_glow.frag";
    private static final String DEFAULT_MESSAGE = "GRAPHEET";

    /** Total scene length in seconds (the rainbow keeps flowing throughout). */
    private static final double SCENE_SECONDS = 6.0;

    /** Entrance timing. */
    private static final float POP_SECONDS = 0.7f;
    private static final float FADE_SECONDS = 0.45f;

    private static final float GLOW_STRENGTH = 1.35f;

    private final String message;

    private boolean initialised;
    private boolean shaderReady;
    private boolean fallbackHintPrinted;

    private PShader shader;
    private PFont font;
    private float screenW;
    private float screenH;
    private float fittedTextSize;

    public GlowTextScene(Applet applet) {
        super(applet);
        String override = System.getProperty("text", "").trim();
        this.message = override.isEmpty() ? DEFAULT_MESSAGE : override;
    }

    @Override
    protected Action build() {
        addNode(this::drawFrame);
        return Actions.sequence(Actions.waitSeconds(SCENE_SECONDS));
    }

    @Override
    protected void onReset() {
        initialised = false;
        shaderReady = false;
        shader = null;
    }

    private void drawFrame(SceneContext ctx) {
        if (!initialised) {
            initialise();
        }

        float elapsed = ctx.elapsed();
        float scale = popScale(elapsed);
        float popAlpha = clamp01(elapsed / FADE_SECONDS);

        // Draw the message as a white mask on the already-black canvas.
        Applet p = applet();
        p.pushMatrix();
        p.scale(scale);
        if (font != null) {
            p.textFont(font);
        }
        p.textAlign(PConstants.CENTER, PConstants.CENTER);
        p.textSize(fittedTextSize);
        p.fill(0, 0, 100);   // HSB white (main applet runs in HSB)
        p.noStroke();
        p.text(message, 0f, 0f);
        p.popMatrix();

        if (!shaderReady) {
            applyFallbackFade(popAlpha);
            return;
        }

        // Full-screen post-process: framebuffer -> rainbow + glow.
        shader.set("time", elapsed);
        shader.set("texelSize", 1f / screenW, 1f / screenH);
        shader.set("glowStrength", GLOW_STRENGTH);
        shader.set("popAlpha", popAlpha);
        p.filter(shader);
    }

    private void initialise() {
        Applet p = applet();
        screenW = p.width;
        screenH = p.height;
        font = p.getLatoBoldFont();
        fittedTextSize = fitTextSize(p);

        if (isP2D()) {
            // loadShader compiles on first use; a bad shader throws here.
            shader = p.loadShader(SHADER_PATH);
            shaderReady = shader != null;
        } else if (!fallbackHintPrinted) {
            System.out.println(
                    "GlowTextScene: no P2D shader (renderer="
                            + System.getProperty("renderer", "JAVA2D")
                            + "). Run with -Drenderer=P2D for the rainbow glow.");
            fallbackHintPrinted = true;
        }
        initialised = true;
    }

    /** Size the message to ~80% of the canvas width, capped on height. */
    private float fitTextSize(Applet p) {
        if (font != null) {
            p.textFont(font);
        }
        p.textSize(200f);
        float measured = Math.max(p.textWidth(message), 1f);
        float widthFit = (screenW * 0.8f) / measured * 200f;
        return Math.min(widthFit, screenH * 0.45f);
    }

    /** JAVA2D fallback: the white text is already drawn; just fade it in. */
    private void applyFallbackFade(float popAlpha) {
        if (popAlpha >= 1f) {
            return;
        }
        Applet p = applet();
        // Veil the whole frame with black at (1 - popAlpha) to fade text in.
        p.pushMatrix();
        p.resetMatrix();
        p.fill(0, 0, 0, 100f * (1f - popAlpha));
        p.noStroke();
        p.rect(0, 0, screenW, screenH);
        p.popMatrix();
    }

    /** Ease-out-back: a quick overshoot so the text "pops" into place. */
    private static float popScale(float elapsed) {
        float t = clamp01(elapsed / POP_SECONDS);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        float eased = 1f + c3 * u * u * u + c1 * u * u;
        return 0.2f + 0.8f * eased;   // grow from 20% to ~100% with overshoot
    }

    private static boolean isP2D() {
        return "P2D".equalsIgnoreCase(System.getProperty("renderer", "JAVA2D"));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
