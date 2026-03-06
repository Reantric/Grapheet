package directions.engine;

import java.util.function.BooleanSupplier;

final class LegacyAction extends ActionBase {
    private final BooleanSupplier tickUntilDone;

    LegacyAction(BooleanSupplier tickUntilDone) {
        this.tickUntilDone = tickUntilDone;
    }

    @Override
    protected boolean onUpdate(SceneContext ctx) {
        return tickUntilDone.getAsBoolean();
    }
}
