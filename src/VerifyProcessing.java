import processing.core.PApplet;
import com.hamoid.VideoExport;

public class VerifyProcessing extends PApplet {
    private float x;
    private float speed = 3.0f;
    private float radius = 80.0f;
    private int maxFrames;

    private VideoExport videoExport;
    private boolean recordingStarted;

    public void settings() {
        size(800, 500);
        smooth(8);
    }

    public void setup() {
        x = -radius;
        textSize(14);

        maxFrames = Integer.getInteger("maxFrames", 0);
        boolean hideSurface = Boolean.getBoolean("hideSurface")
                || Boolean.parseBoolean(System.getenv().getOrDefault("CI", "false"));
        if (hideSurface) {
            surface.setVisible(false);
        }

        boolean recordVideo = Boolean.getBoolean("recordVideo");
        if (recordVideo) {
            videoExport = new VideoExport(this, "build/verifyprocessing.mp4");
            String ffmpegPath = System.getProperty("ffmpegPath", "").trim();
            if (!ffmpegPath.isEmpty()) {
                videoExport.setFfmpegPath(ffmpegPath);
            } else if (!canRunFfmpegFromPath()) {
                System.out.println("VideoExport disabled: ffmpeg not found on PATH. Install ffmpeg or pass -DffmpegPath=/path/to/ffmpeg");
                videoExport = null;
                return;
            }
            videoExport.setQuality(85, 0);
            videoExport.setFrameRate(60);
            videoExport.startMovie();
            recordingStarted = true;
        }
    }

    public void draw() {
        background(250);

        float y = height * 0.5f + sin(frameCount * 0.05f) * 70.0f;
        x += speed;
        if (x > width + radius) x = -radius;

        drawSmiley(x, y, radius);

        if (recordingStarted) {
            videoExport.saveFrame();
        }

        fill(30);
        noStroke();
        text("VerifyProcessing: moving smiley (ESC to quit)", 12, 22);
        text("If you see smooth motion, Processing is working.", 12, 42);

        if (maxFrames > 0 && frameCount >= maxFrames) {
            exit();
        }
    }

    private void drawSmiley(float centerX, float centerY, float r) {
        stroke(20);
        strokeWeight(4);
        fill(255, 220, 0);
        ellipse(centerX, centerY, r * 2, r * 2);

        // Eyes
        fill(20);
        noStroke();
        ellipse(centerX - r * 0.35f, centerY - r * 0.25f, r * 0.22f, r * 0.22f);
        ellipse(centerX + r * 0.35f, centerY - r * 0.25f, r * 0.22f, r * 0.22f);

        // Smile
        noFill();
        stroke(20);
        strokeWeight(6);
        arc(centerX, centerY + r * 0.10f, r * 1.20f, r * 1.00f, 0.15f * PI, 0.85f * PI);
    }

    public void exit() {
        if (recordingStarted) {
            recordingStarted = false;
            videoExport.endMovie();
        }
        super.exit();
    }

    private static boolean canRunFfmpegFromPath() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void main(String[] args) {
        PApplet.main(VerifyProcessing.class.getName());
    }
}
