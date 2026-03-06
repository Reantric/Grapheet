# Grapheet
Graphing engine that can be used to visualize data and do fun stuff with math!

`modernise` is the branch to keep working in. The old `mac` branch was a temporary Apple Silicon bring-up path; this branch now runs on macOS directly.

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
./gradlew -Dscene=ModernTexScene runWindowed
```

Video export example:

```sh
./gradlew -DrecordVideo=true -DffmpegPath=/path/to/ffmpeg runWindowed
```
