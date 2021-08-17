package directions.subscene;

import core.Applet;
import directions.Scene;
import directions.subscene.generateTree.RPath;
import directions.subscene.generateTree.RTree;
import directions.subscene.generateTree.RTreeNode;
import directions.subscene.generateTree.TreePath;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import text.ImmutableLaTeX;

import java.util.ArrayList;
import java.util.Collections;

import static processing.core.PConstants.*;

public class GenerateTree extends Scene {
    RTree ronald;
    RPath path;

    public GenerateTree(Applet window) {
        super(window);
        ronald = new RTree(window,3);
        ronald.init();
        ronald.setColor(new Color(ColorType.CYAN));
        path = new RPath(window,ronald.getRoot(), 0,1);
        path.setColor(new Color(ColorType.MAGENTA));
    }

    @Override
    public boolean executeHelper() {
        init();
        step[0] = ronald.draw(3);
        PShape ailun = ronald.getShape();
        ailun.scale(1);
        window.shape(ailun);
        if (step[0]) {
            step[1] = path.draw();
            path.getShape().scale(1);
            window.shape(path.getShape());
            path.getShape().resetMatrix();
        }



      //  System.out.println(window.frameCount);
        ailun.resetMatrix();
        return false;
    }

    @Override
    protected void init(){
        window.shapeMode(CORNER);
    }

}
