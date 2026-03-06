package directions.engine;

final class WaitAction extends ActionBase {
    private final float durationSeconds;
    private float elapsedSeconds;

    WaitAction(float durationSeconds) {
        this.durationSeconds = Math.max(0f, durationSeconds);
    }

    @Override
    protected boolean onUpdate(SceneContext ctx) {
        if (durationSeconds == 0f) {
            return true;
        }
        elapsedSeconds = Math.min(durationSeconds, elapsedSeconds + ctx.dt());
        return elapsedSeconds >= durationSeconds;
    }

    @Override
    protected void onReset() {
        elapsedSeconds = 0f;
    }
}
