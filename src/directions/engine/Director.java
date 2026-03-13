package directions.engine;

import core.Applet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Director {
    private final SceneContext context;
    private final List<Scene> scenes = new ArrayList<>();

    private int sceneIndex;
    private boolean finished;

    public Director(Applet applet) {
        this.context = new SceneContext(applet);
        this.finished = true;
    }

    public Director(Applet applet, Scene... initialScenes) {
        this(applet);
        Collections.addAll(scenes, initialScenes);
        this.finished = scenes.isEmpty();
    }

    public void add(Scene scene) {
        scenes.add(scene);
        if (scenes.size() == 1 && sceneIndex == 0) {
            finished = false;
        }
    }

    public boolean drawFrame() {
        if (finished) {
            renderLastScene();
            return true;
        }

        context.advanceFrame();
        Scene activeScene = scenes.get(sceneIndex);
        boolean sceneFinished = activeScene.tick(context);
        activeScene.render(context);

        if (sceneFinished) {
            sceneIndex++;
            if (sceneIndex >= scenes.size()) {
                finished = true;
            }
        }

        return finished;
    }

    private void renderLastScene() {
        if (scenes.isEmpty()) {
            return;
        }

        int lastSceneIndex = Math.min(sceneIndex, scenes.size() - 1);
        scenes.get(lastSceneIndex).render(context);
    }

    public void reset() {
        for (Scene scene : scenes) {
            scene.reset();
        }
        sceneIndex = 0;
        finished = scenes.isEmpty();
        context.resetClock();
    }

    public SceneContext context() {
        return context;
    }

    public boolean isFinished() {
        return finished;
    }

    public int currentSceneIndex() {
        return sceneIndex;
    }

    public List<Scene> scenes() {
        return List.copyOf(scenes);
    }
}
