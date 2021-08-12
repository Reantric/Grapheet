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
    Vector treePos = new Vector(0,600); // 400 kinda weird number go fix that asap!
    Vector i = new Vector();
    Vector ang = new Vector(0);

    public GenerateTree(Applet window) {
        super(window);
        this.a = new AilunTree(window,5);
        System.out.println("big r");
        this.ronald = new RTree(window,5);
        tex = new ImmutableLaTeX(window,"$\\int_{1}^{2} f(x)dx$");
    }

    @Override
    public boolean execute() {
        init();
        step[0] = a.draw((int)i.x);
        i.easeTo(new Vector(a.getDegree()),7);
        PShape ailun = a.getSkeleton();
        Vector scale = new Vector(2/(1+0.425f*i.x),2/(1+0.425f*i.x));
        treePos = scale.map(new Vector(2,2),new Vector(0,0),new Vector(0,600),new Vector(0,-400));
        if (step[0]) { // possibly abuse short circuiting? (step[0] && wait(2)) ?
            ang.easeTo(new Vector(PI),3);
            ailun.rotate(ang.x);
        }
        ailun.scale(scale.x,scale.y);
        window.shape(ailun, treePos.x, treePos.y);
        System.out.println(window.frameCount);

        /* if (step[1]){
            PShape copy = window.createShape(GROUP);
            for (int i = 0; i < ailun.getChildCount(); i+=2){
                copy.addChild(ailun.getChild(i));
            }
            copy.scale(scale.x,scale.y);
            window.shape(copy,0,0);
        } */

        ailun.resetMatrix();
        return false;
    }

    private void init(){
        window.shapeMode(CORNER);
    }

}
