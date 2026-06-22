# legacy/

Pre-`DataGrid` code, kept for reference and potential future porting.

**Not part of the build.** The Gradle source set is `src/` only (see `build.gradle`:
`sourceSets.main.java.srcDirs = ['src']`), so nothing in this folder is compiled.
These files may no longer compile against the current engine / `DataGrid` API — that's
expected and fine. The layout mirrors the original `src/` package paths, so porting a
file back is just moving it to the matching path under `src/` and fixing it up.

## Contents

| Path | What it was |
|------|-------------|
| `geom/Grid.java`, `geom/curve/Graph.java` | The original hard-coded-1080p chart, superseded by `src/geom/DataGrid.java`. |
| `util/Useful.java` | Helpers used only by `Grid`. |
| `util/RandomDebug.java` | Unused debug utility (zero references when retired). |
| `directions/scenes/TaylorsScene.java` | Old **default** scene (used legacy `Grid`/`Graph`). Default is now `JtohDifficultyScene`. |
| `directions/scenes/TestScene.java`, `TexScene.java` | Throwaway / demo scenes. |
