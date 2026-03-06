# AGENTS.md

## Start Here
- At the beginning of every task, read [`notes.md`](./notes.md) before making changes.
- Treat `notes.md` as the session handoff file for current architecture, runtime caveats, and active plans.

## Working Rules
- Preserve user changes already in the worktree unless the user explicitly asks to revert them.
- Prefer extending the scene engine under `src/directions/engine/` and `src/directions/scenes/` instead of adding new legacy `step[]`-driven scenes.
- If `notes.md` is stale after meaningful work, update it before finishing the task.
