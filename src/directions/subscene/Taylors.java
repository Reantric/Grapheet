package directions.subscene;

import core.Applet;
import directions.Scene;
import geom.Grid;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import geom.curve.Graph;
import text.ImmutableLaTeX;

public class Taylors extends Scene {
    Grid plane;
    Graph sin, taylor;
    ImmutableLaTeX sinText;
    ImmutableLaTeX taylorText;

    public Taylors(Applet window) {
        super(window);
        plane = new Grid(window);
        plane.setColor(new Color(207,97,99));
        sinText = new ImmutableLaTeX(window,"y = \\sin(x)");
        sinText.setColor(new Color(ColorType.GREEN));
        taylorText = new ImmutableLaTeX(window,"y = x - \\frac{x^3}{3!} + \\frac{x^5}{5!}");
        taylorText.setColor(new Color(ColorType.MAGENTA));
    }

    @Override
    public boolean executeHelper() {
        step[0] = plane.draw() & plane.incrementor.easeTo(new Vector(150,150),3.6);

        if (step[0] && !step[1]) {
            sin = plane.graph(Math::sin);
            step[1] = true;
        }
        if (step[1]) {
            step[2] = sin.draw();
            sinText.draw(plane.planeToCanvas(new Vector(-1,1)));
        }

        if (step[2] && !step[3]){
            taylor = plane.graph(t -> t);
            taylor.setColor(new Color(ColorType.MAGENTA));
            sinText.tex.forEach(shapeWrapper -> shapeWrapper.setColor(new Color(ColorType.RED)));
            step[3] = true;
        }

        if (step[3]) {
            step[4] = taylor.draw();
            taylorText.draw(plane.planeToCanvas(new Vector(-1,-1)));
        }

        if (step[4])
            step[5] = taylor.interpolate(t -> t-Math.pow(t,3)/6);

        return false;
    }
}
