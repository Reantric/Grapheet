package directions.subscene;

import core.Applet;
import directions.Scene;
import directions.subscene.generateTree.RPath;
import directions.subscene.generateTree.RTree;
import processing.core.PApplet;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static processing.core.PConstants.CORNER;

public class GenerateTree extends Scene {
    RTree ronald;
    List<RPath> paths = new LinkedList<>();

    public GenerateTree(Applet window) {
        super(window);
        ronald = new RTree(window,3);
        ronald.init();
        //ronald.setColor(new Color(ColorType.CYAN));
        RPath path = new RPath(window,ronald.getRoot(), 0,1);
        path.setColor(new Color(ColorType.RED));
        paths.add(path);

        path = new RPath(window,ronald.getRoot(), 0,0,1);
        path.setColor(new Color(ColorType.GREEN));
        paths.add(path);

        path = new RPath(window,ronald.getRoot(), 0,0,0);
        path.setColor(new Color(ColorType.MAGENTA));
        paths.add(path);
    }

    @Override
    public boolean executeHelper() {
        init();
        step[0] = ronald.draw(3);
        PShape ailun = ronald.getShape();
        ailun.scale(1.3f);
        ailun.translate(0,100);
        ailun.rotate(PApplet.sin(window.frameCount/40f)/10);
        window.shape(ailun);
        if (step[0]) {
            step[1] = highlightPaths();
        }

        ailun.resetMatrix();
        return false;
    }

    private boolean highlightPaths(){
        for (RPath path: paths){
            boolean b = path.draw(); // maybe add time later who knows
            PShape shape = path.getShape();
            shape.scale(1.3f);
            shape.translate(0,100);
            shape.rotate(PApplet.sin(window.frameCount/40f)/10);
            window.shape(shape);
            shape.resetMatrix();
            if (!b)
                return false;
        }
        return true;
    }

    @Override
    protected void init(){
        window.shapeMode(CORNER);
    }

}
