package directions;

import core.Applet;

public abstract class Scene {

    protected Applet window;
    protected boolean[] step = new boolean[100];
    protected boolean runScene = true;
    long[] clock = new long[100];
    int clockInd = 0;
    protected final static float fps = 1/60f;

    public Scene(Applet window) {
        this.window = window;
    }

    public abstract boolean executeHelper();

    public boolean execute(){
        init();
        boolean b = executeHelper();
        endit();
        return b;
    }

    protected void init(){
    }

    protected void endit(){ // when overriding, call super!
        clockInd = 0;
    }

    public boolean runScene(){
        return runScene;
    }

    protected boolean wait(float time){
        if (window.frameCount > clock[clockInd] + time*60 && clock[clockInd] != 0)
            return true;
        if (clock[clockInd] == 0)
            clock[clockInd] = window.frameCount;
        else if ((window.frameCount-clock[clockInd])*fps > time) {
            clock[clockInd] = 0;
            clockInd++;
            return true;
        }
        clockInd++;
        return false;
    }

}