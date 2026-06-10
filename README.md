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

Video export example:

```sh
./gradlew -DrecordVideo=true runWindowed
```

Recording uses the in-repo ffmpeg pipe recorder and requires `ffmpeg` to be available on `PATH`.
If needed, pass `-DffmpegPath=/absolute/path/to/ffmpeg`.
You can also override the output path with `-DvideoPath=/absolute/path/to/file.mp4`.

## CS2 top-players race

`Cs2TopPlayersScene` animates the top CS2 players by 3-month rolling HLTV rating
(calendar x-axis, auto-fitting y-axis, leader header, smooth label flips;
optional avatars via `src/data/cs2/avatars/<player>.png`):

```sh
./gradlew runCs2TopPlayersScene                  # full-speed (100 ms per simulated day)
./gradlew runCs2TopPlayersScene -DmsPerDay=40    # quick preview
```

Data lives in `src/data/cs2/top_players_rolling.csv`. Regenerate the bundled
mock data with `python3 tools/generate_cs2_mock_data.py`, or swap in real HLTV
data with `tools/scrape_hltv.py` (same CSV schema; needs a residential IP).
