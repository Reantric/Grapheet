import core.Applet;
import core.FfmpegRecorder;
import core.RenderConfig;
import directions.SceneRegistry;
import directions.engine.Director;
import directions.engine.SceneContext;
import processing.core.PApplet;
import processing.event.MouseEvent;

public class Main extends Applet {
    private static float zoom = 1f;
    private FfmpegRecorder videoRecorder;
    private boolean recordVideo;
    private Director director;
    private boolean useP2DRenderer;
    private boolean useFullscreen;
    private boolean holdOnFinish;
    private boolean finishReported;
    private boolean movieClosed;
    private int maxFrames;

    public void setup(){
        String commonPath = "src/data/";
        setSharedFonts(
                createFont(commonPath + "cmunbmr.ttf", 150, true),
                createFont(commonPath + "cmunbmo.ttf", 150, true),
                createFont(commonPath + "Lato-Regular.ttf", 150, true),
                createFont(commonPath + "Lato-Bold.ttf", 150, true)
        );
        director = SceneRegistry.create(this);

        float exportFps = SceneContext.configuredExportFps();
        // Export implies recording unless explicitly disabled (draw-speed benchmarks).
        recordVideo = exportFps > 0f
                ? !"false".equalsIgnoreCase(System.getProperty("recordVideo", ""))
                : Boolean.getBoolean("recordVideo");
        // An export has no operator watching, so it always exits when done.
        holdOnFinish = Boolean.getBoolean("holdOnFinish") && exportFps <= 0f;
        maxFrames = Integer.getInteger("maxFrames", 0);
        if (recordVideo) {
            String videoPath = resolveVideoPath();
            videoRecorder = new FfmpegRecorder(this, videoPath);
            String ffmpegPath = System.getProperty("ffmpegPath", "").trim();
            if (!ffmpegPath.isEmpty()) {
                videoRecorder.setFfmpegPath(ffmpegPath);
            }
            videoRecorder.setQuality(85, 0);
            videoRecorder.setPreset(System.getProperty("ffmpegPreset", ""));
            videoRecorder.setFrameRate(exportFps > 0f ? exportFps : 60f);
            videoRecorder.startMovie();
            System.out.println("Recording video to " + videoPath);
        }

        if (exportFps > 0f) {
            // The scene clock is the fixed export timestep, so the draw loop
            // can run as fast as frames render without changing the video.
            frameRate(1000);
            System.out.println("Offline export: " + exportFps + "fps timeline, uncapped render speed");
        } else {
            frameRate(60);
        }
        // Exports hide the window by default; -DhideSurface=false keeps it
        // visible. P2D is never hidden: JOGL's animator stops driving a
        // hidden NEWT window, which stalls the sketch before the first frame.
        String hideSurface = System.getProperty("hideSurface", "").trim();
        boolean wantHide = hideSurface.isEmpty() ? exportFps > 0f : Boolean.parseBoolean(hideSurface);
        if (wantHide && !useP2DRenderer) {
            surface.setVisible(false);
        }
    }


    public void settings() {
        useP2DRenderer = "P2D".equalsIgnoreCase(System.getProperty("renderer", "JAVA2D"));
        useFullscreen = Boolean.parseBoolean(System.getProperty("fullscreen", "true"));

        // Explicit canvas size for windowed runs: lets non-retina machines
        // export at any resolution (e.g. 3840x2160 finals, 1280x720 previews).
        int renderWidth = Integer.getInteger("renderWidth", RenderConfig.DEFAULT_WIDTH);
        int renderHeight = Integer.getInteger("renderHeight", RenderConfig.DEFAULT_HEIGHT);
        if (renderWidth <= 0 || renderHeight <= 0) {
            throw new IllegalArgumentException(
                    "renderWidth/renderHeight must be positive: " + renderWidth + "x" + renderHeight);
        }

        if (useP2DRenderer) {
            if (useFullscreen) {
                fullScreen(P2D);
            } else {
                size(renderWidth, renderHeight, P2D);
            }
        } else {
            if (useFullscreen) {
                fullScreen();
            } else {
                size(renderWidth, renderHeight);
            }
        }
        // Retina capture (density 2) quadruples the pixels the software
        // renderer and the ffmpeg pipe must push, dropping the sketch below
        // the 60fps the recorder stamps frames at — the video plays fast.
        pixelDensity(Integer.getInteger("pixelDensity", 1));
        smooth(8);
    }


    public static void main(String[] passedArgs) {
        // On macOS, JOGL's NEWT marshals window operations to the AppKit main
        // thread and traps if no NSApplication owns it yet. AWT's Toolkit init
        // claims AppKit exactly once, first — JOGL then routes onto its loop.
        if ("P2D".equalsIgnoreCase(System.getProperty("renderer", ""))
                && System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            java.awt.Toolkit.getDefaultToolkit();
        }
        String[] appletArgs = new String[]{Main.class.getCanonicalName()};
        if (passedArgs != null) {
            PApplet.main(concat(appletArgs, passedArgs));
        } else {
            PApplet.main(appletArgs);
        }
    }

    public void draw(){
        //beginRecord(SVG, "frame-####.svg");
        scale(zoom);
        init();
        boolean finished = director.drawFrame();
        if (recordVideo) {
            try {
                videoRecorder.saveFrame();
            } catch (RuntimeException e) {
                // A dead recorder must end the run: an escaped exception only
                // kills the animation thread, leaving a hidden window and a
                // live JVM — an unattended export would hang forever.
                e.printStackTrace();
                System.exit(1);
            }
        }

        if (maxFrames > 0 && frameCount >= maxFrames) {
            System.out.println("Reached maxFrames=" + maxFrames + " after " + millis() + "ms");
            stopRecordingAndExit();
            return;
        }

        if (finished) {
            if (!finishReported) {
                System.out.println(holdOnFinish
                        ? "Scene finished. Press q to stop recording and exit."
                        : "Goodbye");
                finishReported = true;
            }
            if (!holdOnFinish) {
                stopRecordingAndExit();
            }
        }
        //saveFrame("test/line-" + frameCount + ".png");
      //  endRecord();
    }

    private void init(){
        colorMode(HSB, 360, 100, 100,100);
        if (!useP2DRenderer) {
            hint(ENABLE_STROKE_PURE);
        }
        translate(width / 2f, height / 2f);
        background(0);
        shapeMode(CENTER);
        rectMode(CORNERS);
        strokeCap(ROUND);
        strokeJoin(ROUND);
        ellipseMode(CENTER);
    }


    public void mouseWheel(MouseEvent event) {
        zoom += -0.07f * event.getCount();
    }

    public void keyPressed() {
        if (key == 'q') {
            stopRecordingAndExit();
        }
    }

    private void stopRecordingAndExit() {
        try {
            if (recordVideo && !movieClosed) {
                videoRecorder.endMovie();
                movieClosed = true;
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            System.exit(1);
        }
        exit();
    }

    private static String resolveVideoPath() {
        String configuredPath = System.getProperty("videoPath", "").trim();
        if (!configuredPath.isEmpty()) {
            return configuredPath;
        }
        return "output/" + resolveSceneName() + ".mp4";
    }

    private static String resolveSceneName() {
        String sceneClassName = System.getProperty("sceneClass", "").trim();
        if (!sceneClassName.isEmpty()) {
            int separatorIndex = sceneClassName.lastIndexOf('.');
            String simpleName = separatorIndex >= 0
                    ? sceneClassName.substring(separatorIndex + 1)
                    : sceneClassName;
            return sanitizePathSegment(simpleName);
        }

        String sceneName = System.getProperty("scene", "TaylorsScene").trim();
        if (sceneName.isEmpty()) {
            return "TaylorsScene";
        }
        return sanitizePathSegment(sceneName);
    }

    private static String sanitizePathSegment(String rawValue) {
        String sanitized = rawValue.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isEmpty() ? "scene" : sanitized;
    }
}
