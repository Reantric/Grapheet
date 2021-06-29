package core;

import org.apache.commons.beanutils.BeanUtils;
import processing.core.PGraphics;
import processing.core.PShape;
import storage.Color;

import java.lang.reflect.InvocationTargetException;

public class Shape extends PShape {

    public Shape(PGraphics g, int type) {
        super(g,type);
    }

    public Shape(PGraphics g, int kind, float[] p) {
        super(g,kind,p);
    }

    public Shape(PShape shape) throws InvocationTargetException, IllegalAccessException {
        BeanUtils.copyProperties(this,shape);
    }

    public void setStroke(Color color) {
        super.setStroke(this.color(color.getHue().getValue(),color.getSaturation().getValue(),color.getBrightness().getValue(),color.getAlpha().getValue()));
    }

    public void setFill(Color color){
        super.setFill(this.color(color.getHue().getValue(),color.getSaturation().getValue(),color.getBrightness().getValue(),color.getAlpha().getValue()));
    }

    public final int color(float v1, float v2, float v3, float alpha) {
            if (alpha > 255.0F) {
                alpha = 255.0F;
            } else if (alpha < 0.0F) {
                alpha = 0.0F;
            }

            if (v1 > 255.0F) {
                v1 = 255.0F;
            } else if (v1 < 0.0F) {
                v1 = 0.0F;
            }

            if (v2 > 255.0F) {
                v2 = 255.0F;
            } else if (v2 < 0.0F) {
                v2 = 0.0F;
            }

            if (v3 > 255.0F) {
                v3 = 255.0F;
            } else if (v3 < 0.0F) {
                v3 = 0.0F;
            }

            return (int)alpha << 24 | (int)v1 << 16 | (int)v2 << 8 | (int)v3;
    }

}
