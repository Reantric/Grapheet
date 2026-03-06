package directions.engine;

import java.util.Objects;

public final class Nodes {
    private Nodes() {
    }

    public static Node of(Runnable draw) {
        Objects.requireNonNull(draw, "draw");
        return ctx -> draw.run();
    }
}
