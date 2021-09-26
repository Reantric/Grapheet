package directions.subscene;

import core.Applet;
import directions.Scene;
import directions.subscene.generateTree.RPath;
import directions.subscene.generateTree.RTree;
import directions.subscene.generateTree.RTreeNode;
import processing.core.PApplet;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static processing.core.PConstants.CORNER;

public class GenerateTree extends Scene {
    RTree ronald;
    List<int[]> dirs = new LinkedList<>();
    Color col = new Color(ColorType.RED);
    int depth = 6;
    RPath path;

    public GenerateTree(Applet window) {
        super(window);
        runScene = false;
        ronald = new RTree(window,3);
        ronald.init();
        //ronald.setColor(new Color(ColorType.CYAN));
        path = ronald.createPath(0,0,0);
        col.setAlpha(150);
        path.setColor(col);
        dirs.add(new int[]{0,0,0});
        dirs.add(new int[]{0,0});
    }

    @Override
    public boolean executeHelper() {
        step[0] = ronald.draw();
        PShape ailun = ronald.getShape();
        ailun.scale(1f);
        //ailun.translate(0,100);
        window.shape(ailun);


        if (step[0]) {
            col.interpolate(new Color(ColorType.CYAN),6f);
            step[1] = highlightPath();
        }

        if (step[1] && !step[2]) {
            depth -= 2;
            step[2] = true;
        }

        ailun.resetMatrix();
        return false;
    }

    private boolean highlightPath(){
        boolean b = path.draw(depth); // maybe add time later who knows
        PShape shape = path.getShape();
        shape.scale(1f);
       // shape.translate(0,100);
        window.shape(shape);
        shape.resetMatrix();
        return b;
    }

    @Override
    protected void intro(){
        window.shapeMode(CORNER);
    }

}
