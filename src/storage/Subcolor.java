package storage;


public class Subcolor {
    float value, prevVal;
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

    public boolean is255() {
        return Math.abs(255 - value) < EPSILON;
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
}
