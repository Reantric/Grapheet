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

    public GenerateTree(Applet window) {
        super(window);
        ronald = new RTree(window,3);
        ronald.init();
        //ronald.setColor(new Color(ColorType.CYAN));
        RPath path = new RPath(window,ronald.getRoot(), 0,0,1);
        path.setColor(new Color(ColorType.RED));
        paths.add(path);

    }

    @Override
    public boolean executeHelper() {
        init();
        step[0] = ronald.draw();
        PShape ailun = ronald.getShape();
        ailun.scale(1.3f);
        ailun.translate(0,100);
        ailun.rotate(PApplet.sin(window.frameCount/40f)/10);
        window.shape(ailun);
        if (step[0]) {
            if (step[1])
            {
                paths.get(0).draw(2);
                PShape shape = paths.get(0).getShape();
                shape.scale(1.3f);
                shape.translate(0,100);
                shape.rotate(PApplet.sin(window.frameCount/40f)/10);
                window.shape(shape);
                shape.resetMatrix();
            }
            else
                step[1] = highlightPaths();
        }

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
            shape.rotate(PApplet.sin(window.frameCount/40f)/10);
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
