package directions;

import core.Applet;
import directions.engine.Director;
import directions.engine.Scene;
import directions.scenes.TaylorsScene;
import directions.scenes.TexScene;

import java.util.Locale;

public final class SceneRegistry {
    private SceneRegistry() {
    }

    public static Director create(Applet applet) {
        return new Director(
                applet,
                createScene(applet, System.getProperty("scene", "TaylorsScene"))
        );
    }

    private static Scene createScene(Applet applet, String sceneName) {
        String normalized = normalize(sceneName);
        if (normalized.isEmpty() || normalized.equals("taylorsscene")) {
            return new TaylorsScene(applet);
        }
        if (normalized.equals("texscene")) {
            return new TexScene(applet);
        }
        throw new IllegalArgumentException(
                "Unknown scene '" + sceneName + "'. Available scenes: TaylorsScene, TexScene"
        );
    }

    private static String normalize(String sceneName) {
        return sceneName == null ? "" : sceneName
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }
}
