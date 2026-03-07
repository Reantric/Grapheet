package storage;
import util.map.Interpolatable;
import util.map.MapEase;
import util.map.MapType;

import java.util.Objects;

import static util.map.MapType.QUADRATIC;

public class Color implements Interpolatable<Color> {
    Subcolor hue, saturation, brightness, alpha;
    boolean interpStatus = true;

    public Color(float hue) {
        this(hue, 100, 100, 100);
    }

    public Color(float hue, float saturation) {
        this(hue, saturation, 100, 100);

    }

    public Color(float hue, float saturation, float brightness) {
        this(hue, saturation, brightness, 100);
    }

    public Color(float hue, float saturation, float brightness, float alpha) {
        this.hue = new Subcolor(hue);
        this.saturation = new Subcolor(saturation);
        this.brightness = new Subcolor(brightness);
        this.alpha = new Subcolor(alpha);
    }

    public Color(ColorType c) {
        switch (c) {
            case WHITE -> {
                hue = new Subcolor(0);
                saturation = new Subcolor(0);
                brightness = new Subcolor(100);
            }
            case RED -> {
                hue = new Subcolor(360);
                saturation = new Subcolor(100);
                brightness = new Subcolor(100);
            }
            case CYAN -> {
                hue = new Subcolor(180);
                saturation = new Subcolor(100);
                brightness = new Subcolor(100);
            }
            case BLACK -> {
                hue = new Subcolor(0);
                saturation = new Subcolor(0);
                brightness = new Subcolor(0);
            }
            case GREEN -> {
                hue = new Subcolor(150);
                saturation = new Subcolor(100);
                brightness = new Subcolor(100);
            }
            case MAGENTA -> {
                hue = new Subcolor(300);
                saturation = new Subcolor(100);
                brightness = new Subcolor(100);
            }
            case ORANGE -> {
                hue = new Subcolor(40);
                saturation = new Subcolor(100);
                brightness = new Subcolor(100);
            }
            case YELLOW -> {
                hue = new Subcolor(60);
                saturation = new Subcolor(100);
                brightness = new Subcolor(100);
            }
        }
        alpha = new Subcolor(100);
    }

    public Color() {
        this(0, 100, 100, 100);
    }

    public static Color fromCss(String css) {
        Objects.requireNonNull(css, "css");

        String hex = css.startsWith("#") ? css.substring(1) : css;
        int red;
        int green;
        int blue;
        int alpha = 255;

        switch (hex.length()) {
            case 3 -> {
                red = parseShortHexChannel(hex.charAt(0), css);
                green = parseShortHexChannel(hex.charAt(1), css);
                blue = parseShortHexChannel(hex.charAt(2), css);
            }
            case 4 -> {
                red = parseShortHexChannel(hex.charAt(0), css);
                green = parseShortHexChannel(hex.charAt(1), css);
                blue = parseShortHexChannel(hex.charAt(2), css);
                alpha = parseShortHexChannel(hex.charAt(3), css);
            }
            case 6 -> {
                red = parseHexChannel(hex, 0, css);
                green = parseHexChannel(hex, 2, css);
                blue = parseHexChannel(hex, 4, css);
            }
            case 8 -> {
                red = parseHexChannel(hex, 0, css);
                green = parseHexChannel(hex, 2, css);
                blue = parseHexChannel(hex, 4, css);
                alpha = parseHexChannel(hex, 6, css);
            }
            default -> throw new IllegalArgumentException("Unsupported CSS color: " + css);
        }

        float[] hsb = java.awt.Color.RGBtoHSB(red, green, blue, null);
        return new Color(hsb[0] * 360f, hsb[1] * 100f, hsb[2] * 100f, alpha * 100f / 255f);
    }

    public Color(Subcolor hue, Subcolor saturation, Subcolor brightness, Subcolor alpha) {
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        this.alpha = alpha; // Storing reference
    }

    public Color(Color color) {
        this.hue = new Subcolor(color.getHue());
        this.saturation = new Subcolor(color.getSaturation());
        this.brightness = new Subcolor(color.getBrightness());
        this.alpha = new Subcolor(color.getAlpha());
    }

    public Subcolor getHue() {
        return hue;
    }

    public Subcolor getSaturation() {
        return saturation;
    }

    public Subcolor getBrightness() {
        return brightness;
    }

    public Subcolor getAlpha() {
        return alpha;
    }

    public void setHue(float newHue) {
        hue.setValue(newHue);
    }

    public void setSaturation(float newSat) {
        saturation.setValue(newSat);
    }

    public void setBrightness(float newBri) {
        brightness.setValue(newBri);
    }

    public void setAlpha(float newAlpha) {
        alpha.setValue(newAlpha);
    }

    public boolean interpolate(Color color, MapType interpType, MapEase easing, double time) {
        interpStatus = this.getHue().interpolate(color.getHue().getValue(), interpType, easing, time) &
                this.getSaturation().interpolate(color.getSaturation().getValue(), interpType , easing, time) &
                this.getBrightness().interpolate(color.getBrightness().getValue(), interpType, easing,  time) &
                this.getAlpha().interpolate(color.getAlpha().getValue(), interpType, easing, time);
        return interpStatus;
    }

    public boolean interpolate(Color color) {
        return this.interpolate(color, QUADRATIC, MapEase.EASE_IN_OUT,1);
    }

    public boolean interpolate(Color color, double time) {
        return this.interpolate(color, QUADRATIC, MapEase.EASE_IN_OUT,time);
    }


    public java.awt.Color toJavaRGB() {
        return java.awt.Color.getHSBColor(hue.getValue() / 360, saturation.getValue() / 100, brightness.getValue() / 100);
    }

    public String toString() {
        return String.format("[ %s, %s, %s, %s ]", hue, saturation, brightness, alpha);
    }

    public Color invert() {
        return new Color(Math.abs((180 - hue.getValue()) % 360), saturation.getValue(), brightness.getValue(), alpha.getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Color color)) return false;
        return getHue().equals(color.getHue()) &&
                getSaturation().equals(color.getSaturation()) &&
                getBrightness().equals(color.getBrightness()) &&
                getAlpha().equals(color.getAlpha());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHue(), getSaturation(), getBrightness(), getAlpha());
    }

    public boolean getInterpolationStatus() {
        return this.interpStatus;
    }

    private static int parseShortHexChannel(char digit, String css) {
        int value = Character.digit(digit, 16);
        if (value < 0) {
            throw new IllegalArgumentException("Unsupported CSS color: " + css);
        }
        return value * 17;
    }

    private static int parseHexChannel(String hex, int start, String css) {
        try {
            return Integer.parseInt(hex.substring(start, start + 2), 16);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Unsupported CSS color: " + css, ex);
        }
    }
}
