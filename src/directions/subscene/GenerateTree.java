package directions.subscene;

import core.Applet;
import directions.Scene;
import directions.subscene.generateTree.AilunTree;
import directions.subscene.generateTree.RTree;
import directions.subscene.generateTree.RTreeNode;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import text.ImmutableLaTeX;

import java.util.function.Function;

import static processing.core.PConstants.CORNER;
import static processing.core.PConstants.PI;

public class GenerateTree extends Scene {
    AilunTree a;
    RTree ronald;
    ImmutableLaTeX tex;
    Vector i = new Vector();
    Vector ang = new Vector(0);
    float[] dest = {PI,0};
    RTreeNode[] n;

    public GenerateTree(Applet window) {
        super(window);
        this.a = new AilunTree(window,3);
        System.out.println("big r");
        this.ronald = new RTree(window,3);
        tex = new ImmutableLaTeX(window,"$\\int_{1}^{2} f(x)dx$");
        n = new RTreeNode[]{ronald.getRoot(), ronald.getRoot().getChildren().get(0), ronald.getRoot().getChildren().get(0).getChildren().get(0)};
    }

    @Override
    public boolean executeHelper() {
        init();
        step[0] = ronald.draw(3);
        PShape ailun = ronald.getShape();
        ailun.scale(1); // well this is slightly awkward isnt it

        window.shape(ailun);
        if (step[0]){ //TODO: Make Path, Left corresponds to 2, Right to 3, Text to 1, use enums maybe?
            ailun.disableStyle();
            for (RTreeNode ailu: n){
                Color color = new Color(ColorType.MAGENTA);
                window.stroke(color);
                PShape bruh = ailu.getNodeShape();
                window.shape(bruh.getChild(0));
                window.shape(bruh.getChild(2));
            }
            ailun.enableStyle();
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
