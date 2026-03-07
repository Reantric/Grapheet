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
                createSelectedScene(applet)
        );
    }

    private static Scene createSelectedScene(Applet applet) {
        String sceneClassName = System.getProperty("sceneClass", "").trim();
        if (!sceneClassName.isEmpty()) {
            return createSceneClass(applet, sceneClassName);
        }
        return createScene(applet, System.getProperty("scene", "TaylorsScene"));
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

    private static Scene createSceneClass(Applet applet, String sceneClassName) {
        String resolvedClassName = sceneClassName.contains(".")
                ? sceneClassName
                : "directions.scenes." + sceneClassName;

        Class<? extends Scene> sceneClass;
        try {
            sceneClass = Class.forName(resolvedClassName).asSubclass(Scene.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown scene class '" + sceneClassName + "'", e);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                    "Scene class '" + resolvedClassName + "' does not extend directions.engine.Scene",
                    e
            );
        }

        try {
            return sceneClass.getDeclaredConstructor(Applet.class).newInstance(applet);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Scene class '" + resolvedClassName + "' must expose a constructor that accepts core.Applet",
                    e
            );
        }
    }

    private static String normalize(String sceneName) {
        return sceneName == null ? "" : sceneName
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }
}
