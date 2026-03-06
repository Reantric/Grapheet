# Notes

## Purpose
This file is the handoff context for future Codex sessions. Read it before starting work.

## Current Direction
- The project is being modernized away from the legacy `directions.Scene` + `step[]` flow.
- The new path lives under `src/directions/engine/` and `src/directions/modern/`.
- A real legacy scene has been ported to the new engine: `src/directions/modern/scenes/ModernTaylorsScene.java`.
- `ModernTaylorsScene` no longer uses the old `Actions.legacy(...)` bridge.

## What Exists Now
- New engine primitives:
  - `Director`
  - `Scene`
  - `SceneContext`
  - `Action` and composable actions in `Actions`
  - typed tween adapters in `Values`
- Modern scene registry:
  - `src/directions/modern/ModernScenes.java`
- `Main` can run either:
  - modern path by default
  - legacy path with `-DlegacyDirections=true`
- Modern scene selection can be overridden with `-Dscene=...`

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
  - `legacyDirections`
  - `recordVideo`

## Portability Fixes Already Made
- Several Windows-only paths were converted to portable forward-slash paths:
  - font paths in `Grid`, `DataGrid`, and `Main`
  - SVG temp paths in `ImmutableTex`
  - CSV path in legacy `Grapheet`

## Architectural Notes
- `Grid`, `Graph`, and `ImmutableTex` now expose render/update separation, so the new engine can control orchestration cleanly.
- `ModernTaylorsScene` is now native to the new engine action flow.
- `Graph` reveal now has a clean update path separate from rendering, so the modern scene no longer double-renders graph segments during reveal.
- The old legacy scene runner still exists, but the modern Taylor path no longer depends on it.

## Recommended Next Steps
1. Reduce debug printing left in unrelated legacy classes, especially `DataGraph`, SVG utilities, and older scene code.
2. Continue porting meaningful scenes from legacy `directions/subscene/...` into `src/directions/modern/scenes/`.
3. Remove the old `directions.Directions` / `directions.Scene` runner once enough scenes are ported.
4. Remove more legacy animation state from `Graph`, `Grid`, and related classes where it still leaks into modern usage.
5. Investigate whether `P2D` can be made reliable on Linux, or keep `JAVA2D` as a supported fallback.

## Working Commands
- Compile:
  - `./gradlew compileJava`
- Run modern path with verified settings:
  - `./gradlew run --console=plain -Drenderer=JAVA2D -Dfullscreen=false`
- Run a specific modern scene:
  - `./gradlew run --console=plain -Drenderer=JAVA2D -Dfullscreen=false -Dscene=ModernTaylorsScene`
  - `./gradlew run --console=plain -Drenderer=JAVA2D -Dfullscreen=false -Dscene=ModernTexScene`
- Run legacy path:
  - `./gradlew run --console=plain -DlegacyDirections=true -Drenderer=JAVA2D -Dfullscreen=false`

## Worktree Cautions
- There are unrelated tracked `.gradle/7.1/*` deletions in the worktree. Do not revert them unless the user asks.
- There is an untracked `.vscode/` directory in the worktree. Leave it alone unless asked.
