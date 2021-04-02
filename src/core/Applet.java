package core;

import processing.core.PApplet;
import storage.Color;


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
}
