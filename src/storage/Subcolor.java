package storage;


import util.Mapper;
import util.map.Interpolatable;
import util.map.MapEase;
import util.map.MapType;

import static util.map.MapType.QUADRATIC;

public class Subcolor implements Interpolatable<Float> {
    float value, prevVal;
    long incrementor;
    boolean interpolationComplete = true;
    final double EPSILON = 0.1;

    public Subcolor(float val) {
        this.value = val;
    }

    public Subcolor() {
        this(0);
    }

    public Subcolor(Subcolor s) {
        this.value = s.getValue();
        this.prevVal = s.prevVal;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float newValue) {
        this.value = newValue;
    }

    public boolean is0() {
        return Math.abs(0 - value) < EPSILON;
    }

    public String toString() {
        return String.valueOf(value);
    }

    public boolean interpolate(Float bound, MapType interpType, MapEase easing, double time) {
        if (interpolationComplete) {
            prevVal = value;
            incrementor = 0;
        }

        value = (float) Mapper.map2(incrementor++, 0, 60 * time, prevVal, bound, interpType, easing); // this has to be fixed lol
        interpolationComplete = Math.abs(bound - value) < EPSILON;
        if (interpolationComplete || Float.isNaN(value)) {
            value = bound;
        }

        return interpolationComplete;
    }

    public boolean interpolate(int bound, MapType interpType, double time) {
        return interpolate((float) bound, interpType, MapEase.EASE_IN_OUT, time);
    }

    public boolean interpolate(float bound) {
        return this.interpolate(bound, QUADRATIC, MapEase.EASE_IN_OUT,1);
    }

    public boolean interpolate(float bound, double time) {
        return this.interpolate(bound, QUADRATIC, MapEase.EASE_IN_OUT,time);
    }

    public boolean interpolate(float bound, MapEase easing) {
        return this.interpolate(bound, QUADRATIC, easing, 1);
    }
}
