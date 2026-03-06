package directions.engine;

import java.util.function.BooleanSupplier;

final class UpdateAction extends ActionBase {
    private final BooleanSupplier tickUntilDone;

    UpdateAction(BooleanSupplier tickUntilDone) {
        this.tickUntilDone = tickUntilDone;
    }

    @Override
    protected boolean onUpdate(SceneContext ctx) {
        return tickUntilDone.getAsBoolean();
    }
}
