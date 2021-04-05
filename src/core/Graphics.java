package core;

import processing.core.PVector;
import processing.opengl.PGraphics2D;

public class Graphics extends PGraphics2D {

    public void translate(PVector p){
        this.translate(p.x,p.y);
    }
}
