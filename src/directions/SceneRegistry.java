package directions;

import core.Applet;
import directions.engine.Director;
import directions.engine.Scene;

import java.lang.reflect.InvocationTargetException;

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
        String sceneName = System.getProperty("scene", "JtohDifficultyScene").trim();
        if (sceneName.isEmpty()) {
            sceneName = "JtohDifficultyScene";
        }
        return createSceneClass(applet, sceneName);
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
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Scene class '" + resolvedClassName + "' must expose a constructor that accepts core.Applet",
                    e
            );
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "Scene class '" + resolvedClassName + "' constructor threw an exception",
                    cause
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Scene class '" + resolvedClassName + "' could not be instantiated",
                    e
            );
        }
    }
}
