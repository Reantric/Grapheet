package directions.engine;

public interface Action {
    boolean tick(SceneContext ctx);

    default void reset() {
    }
}
