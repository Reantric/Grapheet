package core;

import processing.core.PShape;
import storage.Color;

public class ShapeWrapper {
    PShape shape;
    Color color;
    public ShapeWrapper(PShape shape, Color color) {
        this.shape = shape;
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
}
