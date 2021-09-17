package directions.subscene;

import core.Applet;
import directions.Scene;
import geom.DataGrid;
import geom.Grid;
import storage.Color;

public class Taylors extends Scene {
    Grid plane;

    public Taylors(Applet window) {
        super(window);
        plane = new Grid(window);
        plane.setColor(new Color(207,97,99));
    }

    @Override
    public boolean executeHelper() {
        step[0] = plane.draw();
        return false;
    }
}
