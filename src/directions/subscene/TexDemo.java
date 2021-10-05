package directions.subscene;

import core.Applet;
import directions.Scene;
import processing.core.PVector;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import text.ImmutableTex;

import static storage.Vector.*;

public class TexDemo extends Scene {
    ImmutableTex tex,copyTex;
    public TexDemo(Applet window) {
        super(window);
        tex = new ImmutableTex(window, """
                 \\[\\begin{bmatrix}
                                   1 & -1 & 0 \\\\
                                   1 & -1 & 1\\\\
                                   0 & 1 & 1
                                  \\end{bmatrix} \\begin{bmatrix}
                                   4 \\\\
                                   2 \\\\
                                   2
                                  \\end{bmatrix} \\]""",new Color(ColorType.YELLOW));


        copyTex = new ImmutableTex(window, """
                 \\[\\begin{bmatrix}
                                   1 & -1 & 0 \\\\
                                   1 & -1 & 1\\\\
                                   0 & 1 & 1
                                  \\end{bmatrix} \\begin{bmatrix}
                                   4 \\\\
                                   2 \\\\
                                   2
                                  \\end{bmatrix} \\]""",new Color(ColorType.YELLOW));
    }

    @Override
    public boolean executeHelper() {
        step[0] = tex.draw();
        copyTex.draw();
        if (step[0]) {
            copyTex.getPos().interpolate(PVector.add(UP,LEFT));
        }
            //step[1] = copyTex
        return false;
    }
}
