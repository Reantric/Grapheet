# Notes

## Purpose
This file is the handoff context for future Codex sessions. Read it before starting work.

## Current Direction
- `main` now carries the modernization work.
- The pre-modernization codebase is preserved by the `legacy-main` tag.
- The project has moved away from the legacy `directions.Scene` + `step[]` flow.
- The active path lives under `src/directions/engine/` and `src/directions/scenes/`.
- A real legacy scene has been ported to the new engine: `src/directions/scenes/TaylorsScene.java`.
- `TaylorsScene` no longer uses the old `Actions.legacy(...)` bridge.

## What Exists Now
- New engine primitives:
  - `Director`
  - `Scene`
  - `SceneContext`
  - `Action` and composable actions in `Actions`
  - typed tween adapters in `Values`
- Default `Actions.tween(...)` overloads now use the shared quadratic ease-in-out in `src/directions/engine/MotionDefaults.java`
- `Scene` now exposes viewport helpers (`viewportWidth()`, `viewportHeight()`, and half-size variants) for scene layout/animation code
- Scene registry:
  - `src/directions/SceneRegistry.java`
- Scene selection can be overridden with `-Dscene=...`
- Supported scene names are the canonical class names only: `TaylorsScene`, `TexScene`
- Dev-only scene selection can be overridden with `-DsceneClass=...`
- `-DsceneClass=...` now preserves constructor-thrown runtime failures instead of reporting them as missing `Applet` constructors
- Gradle auto-generates `run<SceneName>` tasks for files under `src/directions/scenes/`
- Generated `run<SceneName>` tasks now default to `fullscreen=true`, `recordVideo=true`, `holdOnFinish=true`, and `videoPath=output/<SceneName>.mp4`; use `-Dfullscreen=false` to keep a scene windowed
- `./gradlew listScenes` prints the currently discoverable scene task names

## Important Runtime Findings
- `P2D` / JOGL was not stable in this Linux environment.
- The default renderer is now `JAVA2D` unless `-Drenderer=P2D` is passed explicitly.
- Verified successful run used:
  - `./gradlew run --console=plain -Drenderer=JAVA2D -Dfullscreen=false`
- That run completed successfully and printed `Goodbye`.
- `JAVA2D` is the currently verified renderer path.
- `P2D` still needs a renderer compatibility pass before it should be trusted on Linux.
- On this macOS machine, `brew install openjdk@21` was required because the Apple `/usr/bin/java` launcher had no registered JDK.
- Homebrew `openjdk@21` is keg-only here, so future terminal sessions may need:
  - `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
  - `export PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH`
- `timeout 20s ./gradlew runTestScene --console=plain -Drenderer=JAVA2D -Dfullscreen=false` now reaches the timeout without emitting a renderer exception on the updated `DataGrid` path, but the chart motion still needs a human visual pass.

## Build / Tooling Notes
- `build.gradle` now targets Java 21 instead of Java 25.
- Processing native jars are unpacked into `build/processing-natives` for JavaExec tasks.
- JavaExec tasks propagate:
  - `renderer`
  - `fullscreen`
  - `recordVideo`
  - `ffmpegPath`
  - `videoPath`
  - `holdOnFinish`
  - `sceneClass`
- The old bundled `VideoExport.jar` is not Processing 4 compatible because it reads the removed `PApplet.frame` field; runtime recording now uses `src/core/FfmpegRecorder.java` instead of the library jar.
- Recording expects `ffmpeg` on `PATH` by default, and can be overridden with `-DffmpegPath=/absolute/path/to/ffmpeg`.
- `Main` now defaults recordings to `output/<ResolvedSceneName>.mp4`, prints the active recording path at startup, and lets `FfmpegRecorder` create parent directories automatically.
- When `holdOnFinish=true`, the director keeps re-rendering the final scene frame after completion so the ffmpeg recorder can continue capturing until the operator presses `q`.
- SVG export now creates parent directories automatically, so `temp/` can stay ignored and untracked.
- JVM crash logs are ignored via `hs_err_pid*.log`.
- The tracked `hs_err_pid79698.log` dump was removed and scrubbed from branch history because it contained local path/user data.
- Tracked `.gradle/`, `.idea/`, temp SVGs, test renders, and local MP4 outputs were removed from version control and added to `.gitignore`.

## Portability Fixes Already Made
- Several Windows-only paths were converted to portable forward-slash paths:
  - font paths in `Grid` and `Main`
  - SVG temp paths in `ImmutableTex`

## Architectural Notes
- `Grid`, `Graph`, and `ImmutableTex` now expose render/update separation, so the new engine can control orchestration cleanly.
- Fixed windowed-size defaults now live in `src/core/RenderConfig.java`; runtime viewport math should read live `Applet.width` / `Applet.height` instead of `Grid` constants.
- `Grid` and `Graph` no longer own global viewport constants; they derive bounds from the active canvas size.
- A new chart-specific `src/geom/DataGrid.java` now exists again, but it is a fresh first-quadrant renderer for data charts, not the removed legacy `DataGrid`.
- `DataGrid` uses anchored domain-space grid families (`anchor + n * step`) so lines stay phase-locked during pan/rescale, and it renders with grey gridlines, white left/bottom axes, and offset label bands similar to the older look.
- `DataGrid` now uses a coarser but still phase-aligned minor family (`2` subdivisions in `TestScene`), and its draw loops allow one extra anchored line family to appear slightly beyond the top/right plot edges instead of only lengthening existing lines.
- `DataGrid` now extends the white axes with the same top/right overscan as the grid so the chart frame and extra line families stay visually aligned.
- `DataGrid` now suppresses the lower-left corner labels at the axis intersection (e.g. `0` and `800` in the current test setup) and adds a second label pass for minor grid values using smaller, greyer, more transparent text.
- `DataGrid` label generation now follows the same top/right overscan window as the grid and extends the left/bottom label bands accordingly, so top/right labels can appear beyond the plot instead of clipping at `xMax` / `yMax`.
- `TestScene` currently spaces the `DataGrid` y-axis majors at `150` units with `2` minor subdivisions, anchors the y-grid at `800` so the bottom axis sits on a major line, and uses slightly larger minor labels for readability.
- `TestScene` now also draws a bright sine curve directly in chart space, reveals it over time, and starts horizontally following the curve once its head reaches roughly `85%` of the visible x-window.
- `DataGrid` now fades vertical grid lines against the left plot edge over about one current x-major gap, so lines exiting during follow soften out while incoming right-side lines stay crisp.
- `DataGrid` now derives a smooth left-rail collapse from `xMin` relative to `xAnchor`, auto-sizes the slim pinned y-label rail from the currently visible y-label widths, and expands the plot leftward as follow begins instead of leaving a fixed empty gutter.
- `DataGrid` now renders the y-axis as the world-space vertical line at `xAnchor`, fades that line as it exits through the collapsing left rail, and applies matching alpha to x-labels for fading vertical lines, including the returning `0` label under the moving axis.
- `Grid`'s primary spacing API is now `gridSpacing` / `getGridSpacing()` instead of the older `incrementor` naming.
- `Grid` camera bounds and axis-label anchoring now compute directly from `camera`; the unused `startingCamera` offset path was removed.
- `Grid` no longer exposes its core mutable fields directly; scene code should use getters like `getGridSpacing()`, `getSpacing()`, `getTextColor()`, and `getCamera()`.
- `Grid` no longer sets Processing text state in its constructor; it lazily creates its font and applies it during label rendering.
- The old `Grid.draw()` convenience path has been removed; scenes should drive `Grid` animation explicitly and call `render()` for drawing.
- `TaylorsScene` is now native to the new engine action flow.
- `TestScene` now exercises `DataGrid` on the verified `JAVA2D` renderer path and was confirmed to run to completion with `./gradlew runTestScene --console=plain -Drenderer=JAVA2D -Dfullscreen=false`.
- `Graph` reveal now has a clean update path separate from rendering, so the scene no longer double-renders graph segments during reveal.
- The old legacy runner, old scene tree, and old `DataGraph` / `DataGrid` classes have been removed.

## Recommended Next Steps
1. Reduce debug printing left in unrelated utilities, especially SVG code and any remaining exploratory logging.
2. Build new scenes directly under `src/directions/scenes/`.
3. Remove more legacy animation state from `Graph`, `Grid`, and related classes where it still leaks into scene usage.
4. Investigate whether `P2D` can be made reliable on Linux, or keep `JAVA2D` as a supported fallback.

## Working Commands
- Compile:
  - `./gradlew compileJava`
- Run the current scene path with verified settings:
  - `./gradlew run --console=plain -Drenderer=JAVA2D -Dfullscreen=false`
- Run a specific scene:
  - `./gradlew run --console=plain -Drenderer=JAVA2D -Dfullscreen=false -Dscene=TaylorsScene`
  - `./gradlew run --console=plain -Drenderer=JAVA2D -Dfullscreen=false -Dscene=TexScene`
- List discoverable scenes:
  - `./gradlew listScenes`
- Run a discovered scene directly:
  - `./gradlew runTestScene`
  - `./gradlew runTestScene -Dfullscreen=false`
  - `./gradlew runTestScene -DvideoPath=output/custom-name.mp4`
