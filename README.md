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

Per-scene dev run tasks are generated automatically from `src/directions/scenes/*Scene.java`.

Video export example:

```sh
./gradlew -DrecordVideo=true runWindowed
```

Video export requires `ffmpeg` to be available on `PATH`.
If needed, pass `-DffmpegPath=/absolute/path/to/ffmpeg`.
