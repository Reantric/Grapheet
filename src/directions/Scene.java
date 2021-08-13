package directions;

import core.Applet;

public abstract class Scene {

    protected Applet window;
    protected boolean[] step = new boolean[100];
    protected boolean runScene = true;
    long clock = 0;

    public Scene(Applet window) {
        this.window = window;
    }

    public abstract boolean execute();

    public boolean runScene(){
        return runScene;
    }

    protected boolean wait(float time){
        if (clock > time*60) {
            clock = 0;
            return true;
        }
        clock++;
        return false;
    }

}