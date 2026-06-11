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
- `DataGrid` draw loops allow one extra anchored line family to appear slightly beyond the top/right plot edges instead of only lengthening existing lines.
- `DataGrid` now extends the white axes with the same top/right overscan as the grid so the chart frame and extra line families stay visually aligned.
- `DataGrid` now suppresses the lower-left corner labels at the axis intersection (e.g. `0` and `800` in the current test setup), and secondary adaptive label families render smaller, greyer, and more transparent than the dominant family.
- `DataGrid` label generation now follows the same top/right overscan window as the grid and extends the left/bottom label bands accordingly, so top/right labels can appear beyond the plot instead of clipping at `xMax` / `yMax`.
- `DataGrid` now renders adaptive anchored tick families on both axes using the `1, 2, 5, 10` scale ladder. Labels and gridlines fade according to screen-space spacing, so a base `100` y-step naturally transitions through `200`, `500`, `1000`, etc. when zooming out, and smaller sub-base steps fade in only when the base spacing becomes genuinely sparse.
- `DataGrid` uses the same adaptive family logic for x-axis labels/gridlines, so future data-story zoom-outs can widen the x-domain without crushing labels.
- `TestScene` currently uses a `100` y-axis base step, anchors the y-grid at `800` so the bottom axis sits on a major line, and relies on `DataGrid` adaptive tick families instead of fixed minor label spacing.
- `TestScene` now also draws a bright sine curve directly in chart space, reveals it over time, and starts horizontally following the curve once its head reaches roughly `85%` of the visible x-window.
- Once the `TestScene` sine reveal reaches the end of the data, it eases the x-domain back out to show the full graph, exercising the adaptive x-axis path.
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

## CS2 Top-Players Race (feature/cs2-top5-race)
- `DataGrid`'s tick system was rewritten. The old per-family rendering treated
  the `1, 2, 5` ladder as nested, but 2x and 5x steps are not mutually nested
  (500 is not a multiple of 200), so two full label bands could render at once
  and the "dominant family" styling snapped discretely. Now all families merge
  into a single tick list: each tick's alpha is the max over the families
  containing it (continuous bump curve over log2 of px spacing), and size /
  brightness / stroke derive continuously from that alpha. Crossfades are
  smooth in both zoom directions; sub-base steps still fade in only when the
  base family becomes sparse.
- `DataGrid.setXCalendarAxis(LocalDate dayZero)` switches x to calendar mode:
  x values are days since `dayZero`; ticks sit on month / quarter (Jan 1,
  Apr 1, Jul 1, Oct 1) / year / 2-5-10-year boundaries and crossfade with the
  same machinery. Labels render as full reference-style dates ("Dec 1, 2013").
  Quarter labels dominate at a ~1-year window, years take over on zoom-out.
- `util/Pchip.java`: monotone (Fritsch-Carlson / MATLAB-style) piecewise cubic
  Hermite interpolation, used to evaluate rating curves smoothly between
  weekly rolling-average knots. Clamped outside the knot range.
- `directions/scenes/Cs2TopPlayersScene.java`: animated race of top CS2
  players by 3-month rolling HLTV rating. Camera follows the head through a
  365-day window, then eases out to the full range. Per-line "Name (rating)"
  head labels sit to the right of the moving dots with collision-resolved
  smooth flips on overtakes. Top-left shows a reference-style header
  ("Leader:  Name (rating)" + "For N days (~Y.YY years)"), top-right a
  two-line "Current Date:" readout. Optional avatar thumbnails: drop
  `src/data/cs2/avatars/<player>.png` (exact or lowercase name) and they
  appear in the head labels and leader header. `-DmsPerDay=N` controls
  playback speed (default 100).
- HUD/axis font matches the reference (Lato): the scene picks the first
  installed face from Lato -> Helvetica Neue -> Arial -> DejaVu Sans ->
  Verdana (bold variants for the HUD, regular for axis labels via
  `DataGrid.setLabelFont`), falling back to logical SansSerif. No font file
  is bundled; install Lato for the exact reference look. Head dots are plain
  white per the reference; ratings display 2dp like HLTV.
- HUD blocks (leader header, Current Date) sit on translucent 2DGP-style
  backing panels sized to their text.
- The bottom x axis is the world-space "ground" line at the y anchor
  (rating 1.0): the scene starts with its y-window grounded so the axis is
  visible, then it falls off the bottom and dissolves as the camera lifts.
  The left-rail collapse is a RATCHET (`DataGrid.setRailCollapseRatchet`):
  once the camera follows past the world y-axis it never reappears, even
  during the final zoom-out. TestScene does not enable the ratchet.
- The final zoom-out pins the race head to its screen position: xMin eases
  back to day zero while the span solves for a constant head fraction, so
  dots and labels stay put and only the history compresses behind them.
- Head labels: one shared column anchored at the midpoint of the race-front
  head dots; only tracks with data within FRONT_TOLERANCE_DAYS (10) of "now"
  join the column/queue — retired lines keep their fading label at their own
  line end (the wider 45-day RANK_GRACE_DAYS is for ranking only; using it
  for the column once dragged all labels back onto the dots). Vertical
  placement is least-displacement min-gap spreading via
  pool-adjacent-violators isotonic regression, applied to targets AND to the
  eased displayed positions every frame: isolated labels sit exactly at
  their dot, conflicting groups centre on their dots' mean, and labels can
  never render intersecting — overtakes squeeze to the gap and slide past.
- Grid density: quarter majors with soft month minors (calendar grid ideal
  340px, plateau 0.5, support 1.85), years take over after the zoom-out;
  y sub-base minors stay hidden until genuinely zoomed in (ramp 110-190px),
  matching the pre-rework branch's single dominant rhythm. Minor stroke
  floor lifted (brightness 34, alpha 28) for visibility on ordinary
  displays. There are intentionally no sub-0.05 horizontal minors at the
  default zoom — original Grapheet behaved the same way.
- Data pipeline: `src/data/cs2/top_players_rolling.csv` (player,team,color,
  date,rating weekly knots). `tools/generate_cs2_mock_data.py` produces
  realistic mock data (match-level noise -> 90-day rolling average; career
  arcs hand-tuned to reality through mid-2025). Regeneration is
  deterministic and byte-identical to the committed CSV (LF endings).
  `tools/scrape_hltv.py` is an unverified best-effort real scraper (HLTV is
  Cloudflare-protected; run it from a residential IP) that emits the same
  CSV schema.
- Verified in a headless Linux sandbox: full compile (ECJ, release 21) and
  recorded runs of both `Cs2TopPlayersScene` and `TestScene` under
  Xvfb/JAVA2D with frame-by-frame visual inspection. Known cosmetic niceties
  left open: head-label stack can crowd when 6 lines converge, and faint
  next-family ticks are visible mid-crossfade by design.

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
- CS2 race scene:
  - `python3 tools/generate_cs2_mock_data.py` (regenerate data; only needed after editing the generator)
  - `./gradlew runCs2TopPlayersScene`
  - `./gradlew runCs2TopPlayersScene -Dfullscreen=false -DmsPerDay=40` (fast preview)
