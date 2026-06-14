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
- On macOS, exporting from a sandboxed shell (no WindowServer access) yields a perfectly-timed but ALL-BLACK video: the sketch and recorder run fine, the hidden AWT surface just never paints. Run exports from a normal user shell.

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
- The old bundled `VideoExport.jar` (since removed from the repo) is not Processing 4 compatible because it reads the removed `PApplet.frame` field; runtime recording now uses `src/core/FfmpegRecorder.java` instead of the library jar.
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
- Shared app fonts now live on `core.Applet`; `Main` initializes them once via `setSharedFonts(...)`, and scene-owned components can read defaults like `getLatoFont()` / `getLatoBoldFont()` from the passed applet instead of importing from `Main`.
- Fixed windowed-size defaults now live in `src/core/RenderConfig.java`; runtime viewport math should read live `Applet.width` / `Applet.height` instead of `Grid` constants.
- `Grid` and `Graph` no longer own global viewport constants; they derive bounds from the active canvas size.
- A new chart-specific `src/geom/DataGrid.java` now exists again, but it is a fresh first-quadrant renderer for data charts, not the removed legacy `DataGrid`.
- `DataGrid` uses anchored domain-space grid families (`anchor + n * step`) so lines stay phase-locked during pan/rescale, and it renders with grey gridlines, white left/bottom axes, and offset label bands similar to the older look.
- `DataGrid` draw loops allow one extra anchored line family to appear slightly beyond the top/right plot edges instead of only lengthening existing lines.
- `DataGrid` now extends the white axes with the same top/right overscan as the grid so the chart frame and extra line families stay visually aligned.
- `DataGrid` now suppresses the lower-left corner labels at the axis intersection (e.g. `0` and `800` in the current test setup), and secondary adaptive label families render smaller, greyer, and more transparent than the dominant family.
- `DataGrid` label generation now follows the same top/right overscan window as the grid and extends the left/bottom label bands accordingly, so top/right labels can appear beyond the plot instead of clipping at `xMax` / `yMax`.
- `DataGrid` now renders adaptive anchored tick families on both axes using the ABSOLUTE decimal `1, 2, 5` ladder (..., 0.05, 0.1, 0.2, 0.5, 1, 2, 5, ...) anchored at the configured base step. The old base-relative ladder (base x 1, 2, 5, 10) produced steps like 0.25 from a 0.05 base — "1.25, 1.75" axis values where charting convention expects "1.20, 1.40" — and made 0.1 -> 0.2-equivalent hand-offs non-nested. Smaller sub-base steps fade in only when the base spacing becomes genuinely sparse.
- `DataGrid` uses the same adaptive family logic for x-axis labels/gridlines, so future data-story zoom-outs can widen the x-domain without crushing labels.
- `TestScene` currently uses a `100` y-axis base step, anchors the y-grid at `800` so the bottom axis sits on a major line, and relies on `DataGrid` adaptive tick families instead of fixed minor label spacing.
- `TestScene` now also draws a bright sine curve directly in chart space, reveals it over time, and starts horizontally following the curve once its head reaches roughly `85%` of the visible x-window.
- Once the `TestScene` sine reveal reaches the end of the data, it eases the x-domain back out to show the full graph, exercising the adaptive x-axis path.
- `DataGrid` now fades vertical grid lines against the left plot edge over about one current x-major gap, so lines exiting during follow soften out while incoming right-side lines stay crisp.
- `DataGrid` now derives a smooth left-rail collapse from `xMin` relative to `xAnchor`, auto-sizes the slim pinned y-label rail from the currently visible y-label widths, and expands the plot leftward as follow begins instead of leaving a fixed empty gutter.
- `DataGrid` now renders the y-axis as the world-space vertical line at `xAnchor`, fades that line as it exits through the collapsing left rail, and applies matching alpha to x-labels for fading vertical lines, including the returning `0` label under the moving axis.
- `DataGrid` now fades the world-space y-axis and matching `0` x-label through a short handoff zone and fully removes them before they enter the pinned left label band, while preserving the left-rail collapse motion that slides the plot and labels left into place.
- `DataGrid` now renders the bottom label band before the axes but draws the left label rail after the axes, so the pinned left panel behaves like a cover mask and hides the moving world-space y-axis as that axis exits into the rail.
- `DataGrid` now defaults its major/minor label fonts from the owning applet's shared Lato font and still allows per-grid overrides through `setFonts(...)`; the old lazy internal font creation path is gone.
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
  ("Leader:  [avatar] Name (rating)" + "For N days (~Y.YY years)"), top-right
  a two-line "Current Date:" readout. The leader avatar slot is always
  rendered: the player's PNG when present, else a code-drawn HLTV-style
  anonymous placeholder (ringed disc, head-and-shoulders silhouette, "?" on
  the face). Optional avatar thumbnails: drop
  `src/data/cs2/avatars/<player>.png` (exact or lowercase name) and they
  appear in the head labels and leader header. `-DmsPerDay=N` controls
  playback speed (default 85 — sized so the 2017..mid-2026 dataset runs
  ~5 minutes; the user found 100 "kinda slow").
- Team logos: `src/data/cs2/team_logos/<team>.png` (14 committed, fetched
  from Liquipedia commons darkmode/allmode variants — all verified legible
  on black). The CSV carries a team PER KNOT, so transfers show the
  era-correct logo: the scene resolves `Track.teamAt(day)` and draws the
  logo in the head label (between dot and name, uniform slot width for
  column alignment) and after the leader-header title. Missing logo files
  are skipped quietly (label text shifts are avoided by reserving the slot
  whenever ANY visible track has a logo).
- Head labels draw bottom-rank-first, so on an overtake the riser (which
  takes the higher queue slot at the hysteresis swap) always renders in
  FRONT of the label it passes. Label text 34px, min gap 40px, dot-to-label
  gap 34px.
- The generator aligns every still-active career's final knot exactly on
  the dataset end date (rolling_knots(matches, end=...)), so all live lines
  end at the same x — mismatched weekly grids used to scatter line ends and
  let labels collide with neighbours' line tails on the final frames.
- Dataset spans 2017..mid-2026: original cast extended back with arcs
  anchored to real HLTV top-20 yearly ratings (researched June 2026), plus
  era players coldzera (SK/MIBR/FaZe, fades out 2021), dev1ce
  (Astralis/NIP/Astralis), electronic (NAVI/VP) so the early years are a
  real race. s1mple and coldzera test mid-video retirement.
- HUD/axis font matches the reference (Lato): `Main` loads the bundled
  `src/data/Lato-{Regular,Bold}.ttf` into `Applet`'s shared fonts and the
  scene uses them (bold for the HUD, regular for axis labels via
  `DataGrid.setLabelFont`), so the exact reference look needs no installed
  fonts. Without `Main`'s setup the scene falls back to scanning installed
  faces (Lato -> Helvetica Neue -> Arial -> DejaVu Sans -> Verdana, else
  logical SansSerif). Head dots are plain white per the reference; ratings
  display 2dp like HLTV.
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
- Head labels: one shared column anchored to the live race head
  (`domainToCanvasX(min(tDay, xMax)) + LABEL_DOT_GAP_PX`); only tracks with
  data within FRONT_TOLERANCE_DAYS (10) of "now" join the column, so a
  just-retired label can fade in the column briefly, but frozen line ends do
  not pull the anchor left. Once a retiree leaves the front, its label starts
  fading at its own line end and still participates in the vertical gap solve
  while it fades. The wider 45-day RANK_GRACE_DAYS is for leader ranking only;
  using it for the column once dragged all labels back onto the dots. Vertical
  placement: pool-adjacent-violators isotonic min-gap spreading applied to
  the TARGETS only; displayed positions ease toward them (rate 6), so
  overtakes slide visibly past each other. Do NOT re-run the gap solve on
  the eased displayed positions in rank order — that teleported swapping
  pairs into the new arrangement in a single frame. The queue order is
  persistent with hysteresis (RANK_SWAP_HYSTERESIS 0.006) PLUS a per-track
  swap cooldown (1.2s, bypassed when the lead exceeds RANK_SWAP_FORCE
  0.025): re-sorting on raw ratings flapped near-tied pairs every few
  frames and both labels converged superimposed on the crossing point. The
  margin alone is not enough — early-era noise swings past any reasonable
  margin within a fraction of a second of video, so the cooldown is what
  actually pins ties; the force threshold keeps genuine fast overtakes
  (donk 2024) from being delayed.
- Grid density: quarter majors with soft month minors (calendar grid ideal
  340px, plateau 0.5, support 1.85), years take over after the zoom-out;
  y sub-base minor GRIDLINES stay hidden until genuinely zoomed in (ramp
  110-190px) and sub-base LABELS need far sparser spacing still (ramp
  260-380px — at the race's tightest y-window the base gap reaches ~250px,
  and half-step labels collide with 2dp formatting: "1.13, 1.18" between
  "1.10, 1.15"). Minor stroke floor lifted (brightness 34, alpha 28) for
  visibility on ordinary displays. There are intentionally no sub-0.05
  horizontal minor labels at any zoom the race reaches.
- All DataGrid px-spacing density constants are tuned for a 1920px-wide
  canvas and scaled by `width/1920` at use (`densityScale()`), so 1280px
  rough cuts and 3840px direct-canvas finals pick the same tick families as
  the 1920px design at the same domain window.
- DataGrid edge fades: the left-edge exit fade only activates once the rail
  collapse has begun (a static framing must not dim ticks that merely sit
  near the edge — "Apr 1 already half-faded on the opening frame"); x labels
  exit through a 0.45-base-gap smoothstep window (tighter than gridlines) so
  the leftmost date doesn't linger half-faded; the moving world y-axis
  dissolves within a <=48px zone at the plot edge and the left label band is
  drawn AFTER the axes as a cover mask, so the axis never slices through the
  pinned y numbers; label crossfade alpha is pow(t, 2.0) with numeric label
  support 0.95 for crisper family handoffs.
- The bump curve is asymmetric (`SPARSE_SUPPORT_LOG2` 2.2): only the cramped
  side fades at the per-curve support. An axis' base family has no coarser
  stand-in, so dimming it for mere sparseness left the whole y rail grey at
  the race's tight opening window; sparse ticks are clean, cramped ticks are
  the actual problem.
- Sub-base families gate per-depth: each finer family fades in only while
  its PARENT (one ladder step coarser) is sparse, gates multiplying with
  depth — one family of soft minors at a time. A single gate keyed on the
  base family once admitted EVERY finer family at a tight window (0.025 AND
  0.01 under a 0.05 base), rendering as a gridline mesh on the race's
  opening frames. Grid ramp 150-300px, label ramp 260-380px (of 1920).
- Axis LABELS: exactly ONE family per axis is the live "label band"
  (selectLabelBand): the strongest raw bump alpha wins, but a sitting
  incumbent keeps the band unless the challenger beats it by
  LABEL_BAND_STICKINESS (0.12). One band at a time is what prevents two
  strong NON-NESTED families (5-year vs 2-year ticks; 500 vs 200) from
  overprinting label text — a coarser-than-dominant suppression rule was
  tried first and broke as soon as the coarse family became the strongest
  ("Jan 1, 2025" garbled into 2024/2026 on the final hold).
- GRIDLINES follow the same band (the old position-based grid bump curves
  are gone): the band family's lines are the majors, exactly one family
  finer renders as soft minors (MINOR_GRID_STRENGTH x the SUB_BASE_GRID
  ramp evaluated on the BAND's spacing — this is also what fades finer
  structure back in when the band goes sparse), and every other family
  fades out — lines whose labels died must die with them. Grid fade states
  share the nested snap/linger transitions.
- Band crossfades are TIME-based (fadedAlpha): each family's rendered
  alpha eases toward its target (in 4.5/s; out 7/s on x where dying labels
  keep compressing during zooms, a gentler 4.2/s on y). Driving rendered
  alpha straight off pixel spacing left labels stuck at mid-grey for tens
  of seconds when the camera drifted slowly through a crossfade boundary.
  Scenes on the fixed-timestep export clock must pass dt via
  grid.setLabelFadeTimeStep(dt) each frame.
- X gridlines share the exact left-edge exit window with their labels
  (0.45 base gap) — with separate windows the line dimmed first and its
  date hung label-only for seconds.
- Horizontal gridlines and y labels overshoot the plot bottom by 30px and
  dissolve (BOTTOM_GRID_OVERSCAN_PX) instead of popping out at the edge;
  the y label on the ground axis (1.00) is NOT suppressed anymore — the
  old corner-label rule hid a value the viewer wants.
- updateYFit samples splines on an ABSOLUTE 5-day grid plus exact window
  endpoints: a grid anchored to the moving window edge changed the sample
  set every frame and the min/max wobble made the chart and labels jitter
  during the final zoom-out.
- Y-fit occupancy 0.65 with 0.18 top headroom (0.12 let the leader collide
  with the Current Date panel). drawSeries clamps lines/dots at plot
  bottom + 30px (soft floor): lines may dip below the grounded 1.0 axis
  instead of flattening against it. The TOP clamp sits 400px above the
  plot: age-discounted spikes exit through the top of the frame and must
  not flatten into a plateau at the plot edge.
- The y-fit is AGE-WEIGHTED: samples within Y_FIT_RECENT_DAYS (45) of
  "now" get full framing weight; older extremes decay toward the recent
  range (tau Y_FIT_AGE_DECAY_DAYS 75). Without this the camera held the
  window open for a months-old spike until it scrolled off the left edge
  (~31s of video). The discount fades off in sync with the final zoom-out,
  which must frame the full history again (stress-tested with a synthetic
  2.5-peak/0.8-trough player).
- Numeric label ideal spacing is 175px (of 1920): smaller ideals let the
  0.05 y band hold on to ~92-107px spacing, which read as overcrowded
  before the hand-off fired.
- The election bump curve is SYMMETRIC: rendered alphas are band-binary,
  so the curve only drives the election — a sparse-side leniency there
  meant a sparse coarse incumbent's raw never decayed and refinements
  (fading finer labels back in when the window contracts) could never
  unseat it. Refinement challengers also get a reduced stickiness
  (LABEL_BAND_REFINE_STICKINESS 0.04 vs 0.12 for coarsening) — no flap
  risk because the reverse trip still faces the full coarsening margin.
- 1080p sizing pass: line stroke 5.2px (+1.8 leader boost), head dots
  15px, player label text 38px, min gap 46px, logo box 36px; follow
  camera capped at 0.83 with 56px extra label margin so dots/lines end
  further left of the bigger labels. Fade-outs softened to 5.5/s (x) and
  3.2/s (y).
- NESTED band hand-offs are seamless (applyBandTransition): shared ticks
  must never blink out and refade. Coarsening (1->2, 5->10, quarter->year):
  the incoming band snaps to the outgoing band's alpha and only the dying
  minors fade out. Refinement (2->1, year->quarter): the OLD band lingers
  at full until the finer band finishes fading in, then drops invisibly
  (xLingeringBandKey / yLingeringBandKey). Only non-nested switches (2<->5,
  two-year<->five-year) get a true simultaneous crossfade.
- Calendar label alphas carry a physical collision factor: the family's
  spacing is compared against the measured label INK width (0.9x the
  textWidth advance — using the raw advance dented the final two-year band
  enough to flip the closing election to sparse five-year labels). The
  bump curve alone happily kept full-alpha quarter labels while they
  compressed to zero gap during the final zoom-out. Below raw 0.5 the
  incumbent band also loses its election stickiness (LABEL_BAND_CRAMP_FLOOR),
  so the hand-off fires right at first text contact.
- X labels fade against the viewport's right edge (1.2x half-width ramp)
  instead of rendering half-clipped in the overscan ("Jan 1, 20|").
- While the world y-axis line is visible, vertical gridlines within
  axisStroke+2px of it are suppressed — a Jan 1 tick two days from the
  Dec 30 day-zero anchor otherwise peeks out from under the axis stroke as
  a grey sliver.
- The race scene's y formatter is precision-adaptive (2dp, 3dp for
  half-step values like 1.125) and the sub-base label ramp is 210-320px —
  with the 0.55 fade-on threshold finer y labels stay off at the race's
  windows but engage on genuinely sparse spacing (deep zoom-ins).
- The follow camera derives its head fraction per frame from the measured
  widest label block (dot gap + logo slot + text + 24px), smoothed at
  rate 2, capped at MAX_FOLLOW_RATIO 0.86: a fixed fraction clamped the
  label column onto the line tails whenever a long name ("electronic
  (1.23)") was in the race.
- "Current Date:" readout: the date line is centred under the heading
  (heading stays right-aligned at the panel's right edge).
- Logo performance: PImage.tint() in JAVA2D re-blits the source every draw
  even at full-white tint — drawing the raw ~3000px Liquipedia PNGs tinted
  into a 32px box dropped the export from ~76 to ~14fps. Logos are resized
  to max 200px at load and tint is only applied while a label is actually
  fading (alpha < 0.995).
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
  - `./gradlew runCs2TopPlayersScene` (realtime, watchable)
  - `./gradlew runCs2TopPlayersScene -Dfullscreen=false -DmsPerDay=40` (fast preview)
  - `./gradlew exportCs2TopPlayersScene` (fast preview export, no watching)
  - `./gradlew exportCs2TopPlayersScene -DpixelDensity=2 -DffmpegPreset=medium` (final quality)
- Export a scene in a hidden window, decoupled from wall time:
  - `./gradlew exportTestScene` (preview), add `-DpixelDensity=2
    -DffmpegPreset=medium` for the final 3840x2160 render.
  - `-DexportFps=<n>` (min 10) switches the scene clock from wall time to a
    fixed `1/n`s step per frame, the recorder stamps frames at `n` fps, the
    draw loop is uncapped, the JAVA2D surface is hidden (P2D stays visible —
    JOGL's animator stops driving hidden NEWT windows), and the app exits on
    finish. The video timeline is identical to a realtime run regardless of
    render speed; wall-clock speed varies with per-frame scene complexity.
  - Export tasks pin the canvas to 1920x1080 (`fullscreen=false`) so output
    resolution does not depend on the attached display.
  - Recording captures at `pixelDensity(1)` by default (retina density 2
    quadruples the pixel work and used to make realtime recordings play
    ~2.5x fast because the sketch fell below the stamped 60fps); with the
    fixed-timestep export, `-DpixelDensity=2` is safe and is the
    high-quality path.
  - Recorder is an async pipe: the sketch thread snapshots pixels, a writer
    thread packs RGB and feeds ffmpeg; a stalled/dead ffmpeg fails loudly
    (process killed, exception surfaced, JVM exits nonzero) instead of
    hanging or silently truncating the file.
  - `exportFps`, `msPerDay`, `hideSurface`, `pixelDensity`, `ffmpegPreset`,
    `maxFrames`, `renderWidth`, and `renderHeight` are forwarded by the
    run/export Gradle tasks when passed as `-D` flags (previously
    `-DmsPerDay` was documented but silently not forwarded; its consumer is
    the CS2 race scene branch).
  - `renderWidth`/`renderHeight` size the windowed canvas directly: 4K
    finals on non-retina machines (`-DrenderWidth=3840 -DrenderHeight=2160`)
    or fast rough cuts (`-DrenderWidth=1280 -DrenderHeight=720
    -DexportFps=30 -DffmpegPreset=ultrafast`). Layout scales with the
    canvas, so rough cuts are for pacing checks, not final framing.
  - macOS P2D requires Processing core 4.5.x + JOGL 2.6.0 (JogAmp Bug 1528:
    older JOGL SIGTRAPs in NSWindow teardown on macOS 26). Natives unpack
    from the resolved Maven artifacts (the vendored library/ jars were
    removed). P2D
    measured SLOWER than JAVA2D for export on M1 Pro (sync glReadPixels +
    60fps animator pin); use JAVA2D here, test P2D on a discrete-GPU box.
