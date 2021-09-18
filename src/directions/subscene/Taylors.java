package directions.subscene;

import core.Applet;
import directions.Scene;
import geom.DataGrid;
import geom.Grid;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import storage.curve.Graph;

public class Taylors extends Scene {
    Grid plane;
    Graph sin, lin, quad;

    public Taylors(Applet window) {
        super(window);
        plane = new Grid(window);
        plane.setColor(new Color(207,97,99));
    }

    @Override
    public boolean executeHelper() {
        step[0] = plane.draw() & plane.incrementor.easeTo(new Vector(150,150),3.6);

        if (step[0] && !step[1]) {
            sin = plane.graph(Math::sin);
            step[1] = true;
        }
        if (step[1])
            step[2] = sin.draw();

        if (step[2] && !step[3]){
            lin = plane.graph(t -> t);
            lin.setColor(new Color(ColorType.MAGENTA));
            step[3] = true;
        }

        if (step[3])
            step[4] = lin.draw();

        if (step[4] && !step[5]){
            quad = plane.graph(t -> t-Math.pow(t,3)/6);
            quad.setColor(new Color(ColorType.ORANGE));
            step[5] = true;
        }

        if (step[5])
            step[6] = quad.draw();

        return false;
    }
}
