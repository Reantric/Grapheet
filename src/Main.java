import core.Applet;
import core.FfmpegRecorder;
import core.RenderConfig;
import directions.SceneRegistry;
import directions.engine.Director;
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

    public void setup(){
        String commonPath = "src/data/";
        setSharedFonts(
                createFont(commonPath + "cmunbmr.ttf", 150, true),
                createFont(commonPath + "cmunbmo.ttf", 150, true),
                createFont(commonPath + "Lato-Regular.ttf", 150, true),
                createFont(commonPath + "Lato-Bold.ttf", 150, true)
        );
        director = SceneRegistry.create(this);

        recordVideo = Boolean.getBoolean("recordVideo");
        holdOnFinish = Boolean.getBoolean("holdOnFinish");
        if (recordVideo) {
            String videoPath = resolveVideoPath();
            videoRecorder = new FfmpegRecorder(this, videoPath);
            String ffmpegPath = System.getProperty("ffmpegPath", "").trim();
            if (!ffmpegPath.isEmpty()) {
                videoRecorder.setFfmpegPath(ffmpegPath);
            }
            videoRecorder.setQuality(85, 0);
            videoRecorder.setFrameRate(60);
            videoRecorder.startMovie();
            System.out.println("Recording video to " + videoPath);
        }

        frameRate(60);
        //surface.setVisible(false);
    }


    public void settings() {
        useP2DRenderer = "P2D".equalsIgnoreCase(System.getProperty("renderer", "JAVA2D"));
        useFullscreen = Boolean.parseBoolean(System.getProperty("fullscreen", "true"));

        if (useP2DRenderer) {
            if (useFullscreen) {
                fullScreen(P2D);
            } else {
                size(RenderConfig.DEFAULT_WIDTH, RenderConfig.DEFAULT_HEIGHT, P2D);
            }
        } else {
            if (useFullscreen) {
                fullScreen();
            } else {
                size(RenderConfig.DEFAULT_WIDTH, RenderConfig.DEFAULT_HEIGHT);
            }
        }
        smooth(8);
    }


    public static void main(String[] passedArgs) {
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
            videoRecorder.saveFrame();
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
        if (recordVideo && !movieClosed) {
            videoRecorder.endMovie();
            movieClosed = true;
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
