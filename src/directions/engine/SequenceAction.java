package directions.engine;

import java.util.Arrays;

final class SequenceAction extends ActionBase {
    private final Action[] actions;
    private int index;

    SequenceAction(Action... actions) {
        this.actions = actions == null ? new Action[0] : Arrays.copyOf(actions, actions.length);
    }

    @Override
    protected boolean onUpdate(SceneContext ctx) {
        while (index < actions.length) {
            if (actions[index].tick(ctx)) {
                index++;
                continue;
            }
            return false;
        }
        return true;
    }

    @Override
    protected void onReset() {
        index = 0;
        for (Action action : actions) {
            action.reset();
        }
    }
}
