# Notes

## Purpose
This file is the handoff context for future Codex sessions. Read it before starting work.

## Current Direction
- The project is being modernized away from the legacy `directions.Scene` + `step[]` flow.
- The new path lives under `src/directions/engine/` and `src/directions/scenes/`.
- A real legacy scene has been ported to the new engine: `src/directions/scenes/TaylorsScene.java`.
- `TaylorsScene` no longer uses the old `Actions.legacy(...)` bridge.

## What Exists Now
- New engine primitives:
  - `Director`
  - `Scene`
  - `SceneContext`
  - `Action` and composable actions in `Actions`
  - typed tween adapters in `Values`
- Scene registry:
  - `src/directions/SceneRegistry.java`
- Scene selection can be overridden with `-Dscene=...`
- Supported scene names are the canonical class names only: `TaylorsScene`, `TexScene`

## Important Runtime Findings
- `P2D` / JOGL was not stable in this Linux environment.
- The default renderer is now `JAVA2D` unless `-Drenderer=P2D` is passed explicitly.
- Verified successful run used:
  - `./gradlew run --console=plain -Drenderer=JAVA2D -Dfullscreen=false`
- That run completed successfully and printed `Goodbye`.
- `JAVA2D` is the currently verified renderer path.
- `P2D` still needs a renderer compatibility pass before it should be trusted on Linux.

## Build / Tooling Notes
- `build.gradle` now targets Java 21 instead of Java 25.
- Processing native jars are unpacked into `build/processing-natives` for JavaExec tasks.
- JavaExec tasks propagate:
  - `renderer`
  - `fullscreen`
  - `recordVideo`
- JVM crash logs are ignored via `hs_err_pid*.log`.
- The tracked `hs_err_pid79698.log` dump was removed and scrubbed from branch history because it contained local path/user data.

## Portability Fixes Already Made
- Several Windows-only paths were converted to portable forward-slash paths:
  - font paths in `Grid` and `Main`
  - SVG temp paths in `ImmutableTex`

## Architectural Notes
- `Grid`, `Graph`, and `ImmutableTex` now expose render/update separation, so the new engine can control orchestration cleanly.
- `TaylorsScene` is now native to the new engine action flow.
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

## Worktree Cautions
- There are unrelated tracked `.gradle/7.1/*` deletions in the worktree. Do not revert them unless the user asks.
- There is an untracked `.vscode/` directory in the worktree. Leave it alone unless asked.
