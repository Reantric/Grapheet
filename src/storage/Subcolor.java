package storage;


import util.Mapper;
import util.map.MapEase;
import util.map.MapType;

import static util.map.MapType.QUADRATIC;

public class Subcolor { // TODO: define some Easable interface
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

    public boolean easeTo(Float bound, MapType interpType, float time, MapEase easing) {
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

    public boolean easeTo(int bound, MapType interpType, float time) {
        return easeTo((float) bound, interpType, time, MapEase.EASE_IN_OUT);
    }

    public boolean easeTo(float bound) {
        return this.easeTo(bound, QUADRATIC, 1, MapEase.EASE_IN_OUT);
    }

    public boolean easeTo(float bound, float time) {
        return this.easeTo(bound, QUADRATIC, time, MapEase.EASE_IN_OUT);
    }

    public boolean easeTo(float bound, MapEase easing) {
        return this.easeTo(bound, QUADRATIC, 1, easing);
    }
}
