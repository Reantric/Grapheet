package directions.engine;

import core.Applet;

import java.util.ArrayList;
import java.util.List;

public abstract class Scene {
    protected final Applet p;

    private final List<Node> nodes = new ArrayList<>();
    private final List<Updater> updaters = new ArrayList<>();

    private Action root = Actions.noop();
    private boolean built;

    protected Scene(Applet p) {
        this.p = p;
    }

    protected abstract Action build();

    protected void onReset() {
    }

    protected final void addNode(Node node) {
        nodes.add(node);
    }

    protected final void addNodes(Node... moreNodes) {
        for (Node node : moreNodes) {
            nodes.add(node);
        }
    }

    protected final void addUpdater(Updater updater) {
        updaters.add(updater);
    }

    protected final void addUpdaters(Updater... moreUpdaters) {
        for (Updater updater : moreUpdaters) {
            updaters.add(updater);
        }
    }

    protected final Applet applet() {
        return p;
    }

    final boolean tick(SceneContext ctx) {
        ensureBuilt();
        for (Updater updater : updaters) {
            updater.update(ctx);
        }
        return root.tick(ctx);
    }

    final void render(SceneContext ctx) {
        for (Node node : nodes) {
            node.draw(ctx);
        }
    }

    final void reset() {
        if (built) {
            root.reset();
        }
        built = false;
        root = Actions.noop();
        nodes.clear();
        updaters.clear();
        onReset();
    }

    private void ensureBuilt() {
        if (built) {
            return;
        }
        nodes.clear();
        updaters.clear();
        Action builtRoot = build();
        root = builtRoot == null ? Actions.noop() : builtRoot;
        root.reset();
        built = true;
    }
}
