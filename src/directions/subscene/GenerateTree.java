package directions.subscene;

import core.Applet;
import core.Shape;
import directions.Scene;
import directions.subscene.generateTree.AilunNode;
import directions.subscene.generateTree.AilunTree;
import processing.core.PShape;
import storage.Vector;

import java.util.List;

import static processing.core.PConstants.*;

public class GenerateTree extends Scene {
    AilunTree a;
    public GenerateTree(Applet window) {
        super(window);
        this.a = new AilunTree(window,4);
    }

    @Override
    public boolean execute() {
        init();
        PShape ailun = a.draw(4);
        ailun.scale(1-window.frameCount/1000f);
        window.shape(ailun,0,0);
        return false;
    }

    private void init(){
        window.shapeMode(CORNER);
    }

}
