package directions.modern;

import core.Applet;
import directions.engine.Director;
import directions.engine.Scene;
import directions.modern.scenes.ModernTexScene;
import directions.modern.scenes.ModernTaylorsScene;

import java.util.Locale;

public final class ModernScenes {
    private ModernScenes() {
    }

    public static Director create(Applet applet) {
        return new Director(
                applet,
                createScene(applet, System.getProperty("scene", "ModernTaylorsScene"))
        );
    }

    private static Scene createScene(Applet applet, String sceneName) {
        String normalized = normalize(sceneName);
        if (normalized.isEmpty()
                || normalized.equals("taylors")
                || normalized.equals("moderntaylors")
                || normalized.equals("moderntaylorsscene")) {
            return new ModernTaylorsScene(applet);
        }
        if (normalized.equals("tex")
                || normalized.equals("moderntex")
                || normalized.equals("moderntexscene")) {
            return new ModernTexScene(applet);
        }
        throw new IllegalArgumentException(
                "Unknown scene '" + sceneName + "'. Available scenes: ModernTaylorsScene, ModernTexScene"
        );
    }

    private static String normalize(String sceneName) {
        return sceneName == null ? "" : sceneName
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }
}
