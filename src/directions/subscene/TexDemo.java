package directions.subscene;

import core.Applet;
import directions.Scene;
import storage.Color;
import storage.ColorType;
import text.ImmutableLaTeX;

public class TexDemo extends Scene {
    ImmutableLaTeX tex;
    public TexDemo(Applet window) {
        super(window);
        tex = new ImmutableLaTeX(window, """
                 \\[\\begin{bmatrix}
                                   1 & -1 & 0 \\\\
                                   1 & -1 & 1\\\\
                                   0 & 1 & 1
                                  \\end{bmatrix} \\begin{bmatrix}
                                   4 \\\\
                                   2 \\\\
                                   2
                                  \\end{bmatrix} \\]
                """,new Color(ColorType.WHITE));
    }

    @Override
    public boolean executeHelper() {
        System.out.println("hello");
        return false;
    }
}
