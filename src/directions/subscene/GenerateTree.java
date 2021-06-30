package directions.subscene;

import core.Applet;
import directions.Scene;
import directions.subscene.generateTree.AilunTree;
import processing.core.PShape;
import text.ImmutableLaTeX;

import static processing.core.PConstants.CORNER;

public class GenerateTree extends Scene {
    AilunTree a;
    ImmutableLaTeX tex;
    public GenerateTree(Applet window) {
        super(window);
        this.a = new AilunTree(window,4);
        tex = new ImmutableLaTeX(window,"$\\int_{1}^{2} f(x)dx$");
    }

    @Override
    public boolean execute() {
        init();
        step[0] = a.draw(4);
        PShape ailun = a.getSkeleton();
        if (step[0]) {
            System.out.println(window.frameCount);
            ailun.scale(1 - window.frameCount / 1000f);
        }
        window.shape(ailun, 0, 0);
        ailun.scale(1 / (1 - window.frameCount / 1000f)); // in the future replace with resetMatrix
        return false;
    }

    private void init(){
        window.shapeMode(CORNER);
    }

}
