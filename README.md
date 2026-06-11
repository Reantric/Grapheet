# Grapheet
Graphing engine that can be used to visualize data and do fun stuff with math!

`main` is the active branch. The pre-modernization codebase is preserved by the `legacy-main` tag.

## Requirements

- macOS, Linux, or Windows
- JDK 17+ to start the Gradle wrapper

Gradle targets Java 21 for the app itself. With the Foojay toolchain resolver in `settings.gradle`, Gradle can auto-download that JDK for you when needed.

## Run

Windowed dev run:

```sh
./gradlew runWindowed
```

Windowed P2D run:

```sh
./gradlew runP2DWindowed
```

Default app run:

```sh
./gradlew run
```

Processing smoke test:

```sh
./gradlew -DmaxFrames=300 runSmiley
```

Scene override example:

```sh
./gradlew -Dscene=TexScene runWindowed
```

List discoverable scene classes:

```sh
./gradlew listScenes
```

Run a scene directly from the Gradle task list:

```sh
./gradlew runTestScene
```

Per-scene run tasks are generated automatically from `src/directions/scenes/*Scene.java`. They now default to fullscreen, record to `output/<SceneName>.mp4`, and keep the final frame alive until you press `q`. Pass `-Dfullscreen=false` when you want the scene in a window instead.

Export a scene without watching it:

```sh
./gradlew exportTestScene                                    # preview: 1920x1080, fast
./gradlew exportTestScene -DpixelDensity=2 -DffmpegPreset=medium   # final: 3840x2160, slower
```

`export<SceneName>` tasks render in a hidden window on a fixed 60fps timeline: the scene clock advances exactly 1/60s per frame instead of wall time, so the output video plays at the same speed as a realtime run no matter how fast or slow frames actually draw. Wall-clock speed varies with scene complexity (sparse early frames render faster than busy late ones) — the video timeline does not. The canvas is pinned to 1920x1080 so output does not depend on the attached display.

Quality tiers: the default is a fast preview (density 1, `veryfast` x264 preset). For the final render, `-DpixelDensity=2` doubles the backing resolution to 3840x2160 with identical layout and `-DffmpegPreset=medium` spends more encode effort — slower than realtime, but frame-perfect. (Fixed-timestep export is the only correct way to record at density 2: a realtime recording drops below 60fps at that resolution and plays back too fast.) Tune the timeline rate with `-DexportFps=<n>` (minimum 10).

Video export example:

```sh
./gradlew -DrecordVideo=true runWindowed
```

Recording uses the in-repo ffmpeg pipe recorder and requires `ffmpeg` to be available on `PATH`.
If needed, pass `-DffmpegPath=/absolute/path/to/ffmpeg`.
You can also override the output path with `-DvideoPath=/absolute/path/to/file.mp4`.
