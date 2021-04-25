package core;

import processing.core.PApplet;
import processing.core.PVector;
import storage.Color;
import storage.ColorType;


public class Applet extends PApplet {

    public void stroke(Color color){
        this.stroke(color.getHue().getValue(), color.getSaturation().getValue(), color.getBrightness().getValue(), color.getAlpha().getValue());
    }

    public void fill(Color color) {
        this.fill(color.getHue().getValue(), color.getSaturation().getValue(), color.getBrightness().getValue(), color.getAlpha().getValue());
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
}
