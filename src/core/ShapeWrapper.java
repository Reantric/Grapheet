package core;

import processing.core.PShape;
import storage.Color;
import storage.Vector;

public class ShapeWrapper {
    PShape shape;
    Color color;
    Vector pos;

    public ShapeWrapper(PShape shape, Color color) {
        this.shape = shape;
        this.color = color;
    }

    public ShapeWrapper(PShape shape, Color color, Vector pos) {
        this.shape = shape;
        this.pos = pos;
        this.color = color;
    }


    public PShape getShape() {
        return this.shape;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Color getColor(){
        return this.color;
    }

    public void setPos(Vector pos){
        this.pos = pos;
    }

    public Vector getPos() {
        return this.pos;
    }
}
