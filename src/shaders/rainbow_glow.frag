// Rainbow glow text shader (Processing P2D full-screen filter).
//
// Applied via filter(shader) over a frame that already contains white text on
// a black background. It reads the framebuffer as `texture`, uses luminance as
// the text mask, tints it with a hue that flows across x and drifts with time,
// and builds a soft glow halo by averaging the mask over rings of growing
// radius. The whole result fades up from black via `popAlpha`.
//
// Driven from directions.scenes.GlowTextScene, which sets `time`, `texelSize`,
// `glowStrength`, and `popAlpha` each frame.

#ifdef GL_ES
precision highp float;
precision highp int;
#endif

#define PROCESSING_TEXTURE_SHADER

uniform sampler2D texture;

uniform vec2  texelSize;    // 1.0 / screenResolution, for pixel-space offsets
uniform float time;         // seconds elapsed in the scene
uniform float glowStrength; // halo intensity (~0 = none, ~1.5 = bright bloom)
uniform float popAlpha;     // 0..1 entrance fade up from black

varying vec4 vertColor;
varying vec4 vertTexCoord;

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

// Text mask = brightness of the framebuffer (white glyphs on black == 1).
float maskAt(vec2 uv) {
    vec3 c = texture2D(texture, uv).rgb;
    return max(max(c.r, c.g), c.b);
}

void main() {
    vec2 uv = vertTexCoord.st;
    float core = maskAt(uv);

    // Halo: sample the glyph mask on rings of growing radius and accumulate,
    // weighting nearer rings more heavily so the glow falls off smoothly.
    float glow = 0.0;
    float wsum = 0.0;
    const int RINGS = 4;
    const int STEPS = 12;
    for (int r = 1; r <= RINGS; r++) {
        float radPx = float(r) * 5.0;     // ring radius in screen pixels
        float w = 1.0 / float(r);         // inner rings weigh more
        for (int i = 0; i < STEPS; i++) {
            float ang = (6.2831853 / float(STEPS)) * float(i);
            vec2 off = vec2(cos(ang), sin(ang)) * texelSize * radPx;
            glow += maskAt(uv + off) * w;
            wsum += w;
        }
    }
    glow = clamp(glow / wsum, 0.0, 1.0);

    // Flowing rainbow: hue scrolls across x and drifts over time.
    float hue = fract(uv.x * 1.4 - time * 0.18);
    vec3 rainbow = hsv2rgb(vec3(hue, 0.85, 1.0));

    vec3 glowCol = rainbow * glow * glowStrength;
    vec3 col = mix(glowCol, rainbow, core);   // solid letters over the halo
    col *= popAlpha;                          // fade up from black

    gl_FragColor = vec4(col, 1.0);
}
