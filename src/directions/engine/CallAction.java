package directions.engine;

final class CallAction extends ActionBase {
    private final Runnable callback;

    CallAction(Runnable callback) {
        this.callback = callback;
    }

    @Override
    protected void onBegin(SceneContext ctx) {
        callback.run();
    }

    @Override
    protected boolean onUpdate(SceneContext ctx) {
        return true;
    }
}
