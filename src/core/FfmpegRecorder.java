package core;

import processing.core.PApplet;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class FfmpegRecorder {
    private static final int IO_BUFFER_SIZE = 1 << 20;

    private final PApplet applet;
    private final String outputFilePath;

    private String ffmpegPath = "";
    private int crf = 15;
    private float frameRate = 30f;
    private byte[] rgbBuffer;
    private Process process;
    private OutputStream ffmpegInput;
    private boolean started;
    private boolean finished;

    // Processing 4 removed PApplet.frame, so the old bundled VideoExport jar
    // can crash when it falls back to its Swing-based ffmpeg picker path.
    // This recorder keeps the repo on a simple direct ffmpeg pipe instead.
    public FfmpegRecorder(PApplet applet, String outputFilePath) {
        this.applet = applet;
        this.outputFilePath = outputFilePath;
        this.applet.registerMethod("dispose", this);
    }

    public void setFfmpegPath(String ffmpegPath) {
        guardNotStarted("setFfmpegPath");
        this.ffmpegPath = ffmpegPath == null ? "" : ffmpegPath.trim();
    }

    public void setQuality(int qualityPercent, int ignoredAudioBitRate) {
        guardNotStarted("setQuality");
        int clamped = Math.max(0, Math.min(100, qualityPercent));
        this.crf = (100 - clamped) / 2;
    }

    public void setFrameRate(float frameRate) {
        guardNotStarted("setFrameRate");
        if (frameRate <= 0f) {
            throw new IllegalArgumentException("Frame rate must be positive");
        }
        this.frameRate = frameRate;
    }

    public void startMovie() {
        if (started) {
            return;
        }

        ensureParentDirectoryExists(outputFilePath);

        try {
            process = new ProcessBuilder(buildCommand(resolveFfmpegExecutable()))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            ffmpegInput = new BufferedOutputStream(process.getOutputStream(), IO_BUFFER_SIZE);
            started = true;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not start ffmpeg for recording. Install ffmpeg or pass -DffmpegPath=/absolute/path/to/ffmpeg",
                    e
            );
        }
    }

    public void saveFrame() {
        if (!started || finished) {
            return;
        }

        applet.loadPixels();
        int pixelCount = applet.pixelWidth * applet.pixelHeight;
        ensureBufferCapacity(pixelCount * 3);

        int[] pixels = applet.pixels;
        int bufferIndex = 0;
        for (int i = 0; i < pixelCount; i++) {
            int argb = pixels[i];
            rgbBuffer[bufferIndex++] = (byte) ((argb >> 16) & 0xff);
            rgbBuffer[bufferIndex++] = (byte) ((argb >> 8) & 0xff);
            rgbBuffer[bufferIndex++] = (byte) (argb & 0xff);
        }

        try {
            ffmpegInput.write(rgbBuffer, 0, bufferIndex);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write a frame to ffmpeg for " + outputFilePath, e);
        }
    }

    public void endMovie() {
        if (finished) {
            return;
        }
        finished = true;

        IOException closeFailure = null;
        if (ffmpegInput != null) {
            try {
                ffmpegInput.close();
            } catch (IOException e) {
                closeFailure = e;
            } finally {
                ffmpegInput = null;
            }
        }

        if (process != null) {
            waitForEncoderExit();
            process = null;
        }

        if (closeFailure != null) {
            throw new IllegalStateException("Failed while finalizing recording for " + outputFilePath, closeFailure);
        }
    }

    public void dispose() {
        if (finished) {
            return;
        }

        try {
            endMovie();
        } catch (RuntimeException e) {
            System.err.println("Failed to finalize recording for " + outputFilePath);
            e.printStackTrace(System.err);
        }
    }

    public static boolean canRunFfmpeg(String ffmpegPath) {
        String command = ffmpegPath == null || ffmpegPath.trim().isEmpty() ? "ffmpeg" : ffmpegPath.trim();
        try {
            Process process = new ProcessBuilder(command, "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<String> buildCommand(String ffmpegExecutable) {
        int width = applet.pixelWidth > 0 ? applet.pixelWidth : applet.width;
        int height = applet.pixelHeight > 0 ? applet.pixelHeight : applet.height;

        List<String> command = new ArrayList<>();
        command.add(ffmpegExecutable);
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-y");
        command.add("-f");
        command.add("rawvideo");
        command.add("-pix_fmt");
        command.add("rgb24");
        command.add("-s");
        command.add(width + "x" + height);
        command.add("-r");
        command.add(Float.toString(frameRate));
        command.add("-i");
        command.add("-");
        command.add("-an");
        command.add("-c:v");
        command.add("libx264");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-vf");
        command.add("pad=ceil(iw/2)*2:ceil(ih/2)*2");
        command.add("-crf");
        command.add(Integer.toString(crf));
        command.add(outputFilePath);
        return command;
    }

    private String resolveFfmpegExecutable() {
        String candidate = ffmpegPath == null ? "" : ffmpegPath.trim();
        return candidate.isEmpty() ? "ffmpeg" : candidate;
    }

    private void waitForEncoderExit() {
        try {
            boolean exited = process.waitFor(15, TimeUnit.SECONDS);
            if (!exited) {
                process.destroy();
                exited = process.waitFor(5, TimeUnit.SECONDS);
            }
            if (!exited) {
                process.destroyForcibly();
                exited = process.waitFor(5, TimeUnit.SECONDS);
            }
            if (!exited) {
                throw new IllegalStateException("ffmpeg did not exit cleanly while writing " + outputFilePath);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "ffmpeg exited with code " + process.exitValue() + " while writing " + outputFilePath
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Interrupted while finalizing recording for " + outputFilePath, e);
        }
    }

    private void ensureBufferCapacity(int size) {
        if (rgbBuffer == null || rgbBuffer.length != size) {
            rgbBuffer = new byte[size];
        }
    }

    private static void ensureParentDirectoryExists(String outputFilePath) {
        Path parent = Paths.get(outputFilePath).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create video output directory for " + outputFilePath, e);
        }
    }

    private void guardNotStarted(String methodName) {
        if (started) {
            throw new IllegalStateException("Cannot call " + methodName + " after startMovie()");
        }
    }
}
