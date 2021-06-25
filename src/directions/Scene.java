package directions;

import core.Applet;

public abstract class Scene {

    protected Applet window;
    protected boolean[] step = new boolean[100];
    protected boolean runScene = true;

    public Scene(Applet window) {
        this.window = window;
    }

    public abstract boolean execute();

    public boolean runScene(){
        return runScene;
    }

}