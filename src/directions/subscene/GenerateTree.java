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
    List<PShape> acceptedPaths = new LinkedList<>();
    Color col = new Color(ColorType.RED);

    public GenerateTree(Applet window) {
        super(window);
        ronald = new RTree(window,3);
        ronald.init();
        //ronald.setColor(new Color(ColorType.CYAN));
        RPath path = ronald.createPath(0,0,0);
        col.setAlpha(150);
        path.setColor(col);
        paths.add(path);
    }

    @Override
    public boolean executeHelper() {
        init();
        step[0] = ronald.draw();
        PShape ailun = ronald.getShape();
        ailun.scale(1.3f);
        ailun.translate(0,100);
        window.shape(ailun);
        if (step[0]) {
            col.easeTo(new Color(ColorType.CYAN),6f);
            if (step[1])
            {
                step[2] = paths.get(0).draw(4); // hmmm this is odd
                PShape shape = paths.get(0).getShape();
                shape.scale(1.3f);
                shape.translate(0,100);
                window.shape(shape);
                shape.resetMatrix();
            }
            else {
                step[1] = highlightPaths() && wait(2f);
            }
        }
        if (step[2])
            window.println("eijdsa");

        drawPaths();
        ailun.resetMatrix();
        return false;
    }

    private boolean highlightPaths(){
        for (RPath path: paths){
            boolean b = path.draw(); // maybe add time later who knows
            PShape shape = path.getShape();
            shape.scale(1.3f);
            shape.translate(0,100);
            acceptedPaths.add(shape);
            if (!b)
                return false;
        }
        return true;
    }

    private void drawPaths(){
        for (PShape shape: acceptedPaths) {
            window.shape(shape);
            shape.resetMatrix();
        }
        acceptedPaths.clear();
    }

    @Override
    protected void init(){
        window.shapeMode(CORNER);
    }

}
