package directions.subscene;

import core.Applet;
import directions.Scene;
import directions.subscene.generateTree.AilunTree;

public class GenerateTree extends Scene {
    AilunTree a;

    public GenerateTree(Applet window) {
        super(window);
        this.a = new AilunTree(window,4);
    }

    @Override
    public boolean execute() {
        a.draw(4);
        return false;
    }
}
