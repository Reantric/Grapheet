package directions.subscene;

import core.Applet;
import core.ShapeWrapper;
import directions.Scene;
import geom.Grid;
import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction;
import org.apache.commons.math3.analysis.function.*;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import geom.curve.Graph;
import text.ImmutableLaTeX;

import java.util.ArrayList;
import java.util.List;

public class Taylors extends Scene {
    Grid plane;
    UnivariateDifferentiableFunction function;
    Graph sin, taylor;
    ImmutableLaTeX sinText;
    ImmutableLaTeX taylorText;
    List<DerivativeStructure> taylorValue = new ArrayList<>();

    public Taylors(Applet window) {
        super(window);
        plane = new Grid(window);
        plane.setColor(new Color(207,97,99));
        sinText = new ImmutableLaTeX(window,"y = \\sin(x)");
        sinText.setColor(new Color(ColorType.GREEN),true);
        taylorText = new ImmutableLaTeX(window,"y = x - \\frac{x^3}{3!} + \\frac{x^5}{5!}");
        taylorText.setColor(new Color(ColorType.MAGENTA),true);
        taylorText.getSubtex(4).forEach(s -> {
            Color c = new Color(s.getColor());
            c.setAlpha(0);
            s.setColor(c);
        });

        function = new Tanh();
        taylorValue.add(function.value(new DerivativeStructure(1,1,0,0)));
        taylorValue.add(function.value(new DerivativeStructure(1,3,0,0)));
        taylorValue.add(function.value(new DerivativeStructure(1,5,0,0)));
        taylorValue.add(function.value(new DerivativeStructure(1,7,0,0)));
    }

    @Override
    public boolean executeHelper() {
        step[0] = plane.draw() & plane.incrementor.easeTo(new Vector(150,150),3.6);

        if (step[0] && !step[1]) {
            sin = plane.graph(function::value);
            step[1] = true;
        }
        if (step[1]) {
            step[2] = sin.draw();
            sinText.draw(plane.planeToCanvas(new Vector(-5,-1.4f)));
        }

        if (step[2] && !step[3]){
            taylor = plane.graph(taylorValue.get(0)::taylor);
            taylor.setColor(new Color(ColorType.MAGENTA));
            step[3] = true;
        }

        if (step[3]) {
            step[4] = taylor.draw();
            taylorText.draw(plane.planeToCanvas(new Vector(3,2)));
        }

        if (step[4] && !step[5]) {
            step[5] = taylor.interpolate(taylorValue.get(1)::taylor) && wait(2f);
        }

        if (step[5] && !step[6]) {
            step[6] = taylor.interpolate(taylorValue.get(2)::taylor);
        }

        if (step[6]) {
            step[7] = taylor.interpolate(taylorValue.get(3)::taylor);
        }
        return false;
    }
}
