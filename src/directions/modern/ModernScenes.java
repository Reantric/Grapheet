package directions.modern;

import core.Applet;
import directions.engine.Director;
import directions.modern.scenes.ModernTaylorsScene;

public final class ModernScenes {
    private ModernScenes() {
    }

    public static Director create(Applet applet) {
        return new Director(
                applet,
                new ModernTaylorsScene(applet)
        );
    }
}
