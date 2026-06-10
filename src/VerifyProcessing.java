import processing.core.PApplet;
import core.FfmpegRecorder;

public class VerifyProcessing extends PApplet {
    private float x;
    private float speed = 3.0f;
    private float radius = 80.0f;
    private int maxFrames;

    private FfmpegRecorder videoRecorder;
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
            String ffmpegPath = System.getProperty("ffmpegPath", "").trim();
            if (!FfmpegRecorder.canRunFfmpeg(ffmpegPath)) {
                System.out.println("Recording disabled: ffmpeg not found on PATH. Install ffmpeg or pass -DffmpegPath=/path/to/ffmpeg");
                return;
            }
            videoRecorder = new FfmpegRecorder(this, "build/verifyprocessing.mp4");
            if (!ffmpegPath.isEmpty()) {
                videoRecorder.setFfmpegPath(ffmpegPath);
            }
            videoRecorder.setQuality(85, 0);
            videoRecorder.setFrameRate(60);
            videoRecorder.startMovie();
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
            videoRecorder.saveFrame();
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
            videoRecorder.endMovie();
        }
        super.exit();
    }

    public static void main(String[] args) {
        PApplet.main(VerifyProcessing.class.getName());
    }
}
