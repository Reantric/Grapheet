package directions.engine;

public abstract class ActionBase implements Action {
    private boolean started;
    private boolean finished;

    @Override
    public final boolean tick(SceneContext ctx) {
        if (finished) {
            return true;
        }
        if (!started) {
            onBegin(ctx);
            started = true;
        }
        if (onUpdate(ctx)) {
            finished = true;
            onEnd(ctx);
        }
        return finished;
    }

    @Override
    public final void reset() {
        started = false;
        finished = false;
        onReset();
    }

    protected void onBegin(SceneContext ctx) {
    }

    protected abstract boolean onUpdate(SceneContext ctx);

    protected void onEnd(SceneContext ctx) {
    }

    protected void onReset() {
    }
}
