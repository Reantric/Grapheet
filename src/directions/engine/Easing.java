package directions.engine;

@FunctionalInterface
public interface Easing {
    float apply(float progress);
}
