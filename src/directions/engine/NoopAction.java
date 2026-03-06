package directions.engine;

final class NoopAction extends ActionBase {
    @Override
    protected boolean onUpdate(SceneContext ctx) {
        return true;
    }
}
