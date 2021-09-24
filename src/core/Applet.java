package core;

import processing.core.PApplet;
import processing.core.PShape;
import processing.core.PVector;
import storage.Color;
import storage.ColorType;
import storage.Vector;


public class Applet extends PApplet {

    public void stroke(Color color){
        this.stroke(color.getHue().getValue(), color.getSaturation().getValue(), color.getBrightness().getValue(), color.getAlpha().getValue());
    }

    public void fill(Color color) {
        this.fill(color.getHue().getValue(), color.getSaturation().getValue(), color.getBrightness().getValue(), color.getAlpha().getValue());
    }

    public int color(Color color) {
        return this.color(color.getHue().getValue(), color.getSaturation().getValue(), color.getBrightness().getValue(), color.getAlpha().getValue());
    }

    public void tint(Color color) {
        this.tint(color.getHue().getValue(), color.getSaturation().getValue(), color.getBrightness().getValue(), color.getAlpha().getValue());
    }

    public void scale(double d){
        this.scale((float) d);
    }

    public void translate(PVector mult) {
        this.translate(mult.x,mult.y);
    }

    public void fill(ColorType c) {
        this.fill(new Color(c));
    }

    public void stroke(ColorType c) {
        this.stroke(new Color(c));
    }

    public void shape(PShape latex, Vector pos) {
        this.shape(latex,pos.x,pos.y);
    }

    public void rect(Vector pos, Vector pos1) {
        this.rect(pos.x,pos.y,pos1.x,pos1.y);
    }

    /*public Shape createShape() {
        try {
            return new Shape(g.createShape());
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        } return null;
    }


    public Shape createShape(int type) {
        try {
            return new Shape(g.createShape(type));
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        } return null;
    }


    /**
     * @param kind either POINT, LINE, TRIANGLE, QUAD, RECT, ELLIPSE, ARC, BOX, SPHERE
     * @param p parameters that match the kind of shape
     *
    public Shape createShape(int kind, float... p) {
        try {
            return new Shape(g.createShape(kind,p));
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        } return null;
    } */
}
