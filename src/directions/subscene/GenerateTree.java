package directions.subscene;

import core.Applet;
import directions.Scene;
import directions.subscene.generateTree.AilunTree;
import directions.subscene.generateTree.RTree;
import processing.core.PShape;
import storage.Vector;
import text.ImmutableLaTeX;

import java.util.Stack;

import static processing.core.PConstants.*;

public class GenerateTree extends Scene {
    AilunTree a;
    RTree ronald;
    ImmutableLaTeX tex;
    Vector i = new Vector();
    Vector ang = new Vector(0);

    public GenerateTree(Applet window) {
        super(window);
        this.a = new AilunTree(window,3);
        System.out.println("big r");
        this.ronald = new RTree(window,3);
        tex = new ImmutableLaTeX(window,"$\\int_{1}^{2} f(x)dx$");
    }

    @Override
    public boolean execute() {
        init();
        step[0] = ronald.draw(3);
       // i.easeTo(new Vector(ronald.getDegree()),5);
        PShape ailun = ronald.getShape();


        if (step[0] && wait(2f)) { // possibly abuse short circuiting? (step[0] && wait(2)) ?
            ang.easeTo(new Vector(PI),5);
            ailun.rotate(ang.x);
        }

        window.shape(ailun);
        System.out.println(window.frameCount);

  //      ailun.resetMatrix();
        return false;
    }

    private void init(){
        window.shapeMode(CORNER);
    }

}
