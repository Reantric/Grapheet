package core;

import processing.core.PApplet;
import processing.core.PGraphics;
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

    public void translate(Vector mult) {
        this.translate((float) mult.x,(float) mult.y);
    }

    public void fill(ColorType c) {
        this.fill(new Color(c));
    }

    public void stroke(ColorType c) {
        this.stroke(new Color(c));
    }

    public void shape(PShape latex, Vector pos) {
        this.shape(latex, (float) pos.x, (float) pos.y);
    }

    public void rect(Vector pos, Vector pos1) {
        this.rect((float) pos.x, (float) pos.y, (float) pos1.x, (float) pos1.y);
    }

    public void scale(Vector scale){
        this.scale((float) scale.x, (float) scale.y);
    }

    public static final double linMap(double value, double start1, double stop1, double start2, double stop2) {
        double outgoing = start2 + (stop2 - start2) * ((value - start1) / (stop1 - start1));
        String badness = null;
        if (outgoing != outgoing) {
            badness = "NaN (not a number)";
        } else if (outgoing == Float.NEGATIVE_INFINITY || outgoing == Float.POSITIVE_INFINITY) {
            badness = "infinity";
        }

        if (badness != null) {
            PGraphics.showWarning("fuc");
        }

        return outgoing;
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
