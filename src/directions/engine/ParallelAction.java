package directions.engine;

import java.util.Arrays;

final class ParallelAction extends ActionBase {
    private final Action[] actions;
    private final boolean[] finished;

    ParallelAction(Action... actions) {
        this.actions = actions == null ? new Action[0] : Arrays.copyOf(actions, actions.length);
        this.finished = new boolean[this.actions.length];
    }

    @Override
    protected boolean onUpdate(SceneContext ctx) {
        if (actions.length == 0) {
            return true;
        }

        boolean allFinished = true;
        for (int i = 0; i < actions.length; i++) {
            if (!finished[i]) {
                finished[i] = actions[i].tick(ctx);
            }
            allFinished &= finished[i];
        }
        return allFinished;
    }

    @Override
    protected void onReset() {
        Arrays.fill(finished, false);
        for (Action action : actions) {
            action.reset();
        }
    }
}
