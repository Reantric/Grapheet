package util;

import util.map.MapEase;
import util.map.MapType;

import static util.map.MapEase.*;

public class Mapper {
    /* The map2() function supports the following eaMath.sing types */

    /*
     * A map() replacement that allows for specifying eaMath.sing curves
     * with arbitrary exponents.
     *
     * value :   The value to map
     * start1:   The lower limit of the input range
     * stop1 :   The upper limit of the input range
     * start2:   The lower limit of the output range
     * stop2 :   The upper limit of the output range
     * type  :   The type of eaMath.sing (see above)
     * when  :   One of EASE_IN, EASE_OUT, or EASE_IN_OUT
     */
    public static double map2(double value, double start1, double stop1, double start2, double stop2, MapType type, MapEase when) {
        double c = stop2 - start2;
        double t = value - start1;
        double d = stop1 - start1;
        double p = 0.5f;
        switch (type) {
            case LINEAR:
                return c * t / d + start2;
            case SQRT:
                if (when == EASE_IN) {
                    t /= d;
                    return c * Math.pow(t, p) + start2;
                } else if (when == EASE_OUT) {
                    t /= d;
                    return c * (1 - Math.pow(1 - t, p)) + start2;
                } else if (when == EASE_IN_OUT) {
                    t /= d / 2;
                    if (t < 1) return c / 2 * Math.pow(t, p) + start2;
                    return c / 2 * (2 - Math.pow(2 - t, p)) + start2;
                }
                break;
            case QUADRATIC:
                if (when == EASE_IN) {
                    t /= d;
                    return c * t * t + start2;
                } else if (when == EASE_OUT) {
                    t /= d;
                    return -c * t * (t - 2) + start2;
                } else if (when == EASE_IN_OUT) {
                    t /= d / 2;
                    if (t < 1) return c / 2 * t * t + start2;
                    t--;
                    return -c / 2 * (t * (t - 2) - 1) + start2;
                }
                break;
            case CUBIC:
                if (when == EASE_IN) {
                    t /= d;
                    return c * t * t * t + start2;
                } else if (when == EASE_OUT) {
                    t /= d;
                    t--;
                    return c * (t * t * t + 1) + start2;
                } else if (when == EASE_IN_OUT) {
                    t /= d / 2;
                    if (t < 1) return c / 2 * t * t * t + start2;
                    t -= 2;
                    return c / 2 * (t * t * t + 2) + start2;
                }
                break;
            case QUARTIC:
                if (when == EASE_IN) {
                    t /= d;
                    return c * t * t * t * t + start2;
                } else if (when == EASE_OUT) {
                    t /= d;
                    t--;
                    return -c * (t * t * t * t - 1) + start2;
                } else if (when == EASE_IN_OUT) {
                    t /= d / 2;
                    if (t < 1) return c / 2 * t * t * t * t + start2;
                    t -= 2;
                    return -c / 2 * (t * t * t * t - 2) + start2;
                }
                break;
            case QUINTIC:
                if (when == EASE_IN) {
                    t /= d;
                    return c * t * t * t * t * t + start2;
                } else if (when == EASE_OUT) {
                    t /= d;
                    t--;
                    return c * (t * t * t * t * t + 1) + start2;
                } else if (when == EASE_IN_OUT) {
                    t /= d / 2;
                    if (t < 1) return c / 2 * t * t * t * t * t + start2;
                    t -= 2;
                    return c / 2 * (t * t * t * t * t + 2) + start2;
                }
                break;
            case SINUSOIDAL:
                if (when == EASE_IN) {
                    return -c * Math.cos(t / d * (Math.PI / 2)) + c + start2;
                } else if (when == EASE_OUT) {
                    return c * Math.sin(t / d * (Math.PI / 2)) + start2;
                } else if (when == EASE_IN_OUT) {
                    return -c / 2 * (Math.cos(Math.PI * t / d) - 1) + start2;
                }
                break;
            case EXPONENTIAL:
                if (when == EASE_IN) {
                    return c * Math.pow(2, 10 * (t / d - 1)) + start2;
                } else if (when == EASE_OUT) {
                    return c * (-Math.pow(2, -10 * t / d) + 1) + start2;
                } else if (when == EASE_IN_OUT) {
                    t /= d / 2;
                    if (t < 1) return c / 2 * Math.pow(2, 10 * (t - 1)) + start2;
                    t--;
                    return c / 2 * (-Math.pow(2, -10 * t) + 2) + start2;
                }
                break;
            case CIRCULAR:
                if (when == EASE_IN) {
                    t /= d;
                    return -c * (Math.sqrt(1 - t * t) - 1) + start2;
                } else if (when == EASE_OUT) {
                    t /= d;
                    t--;
                    return c * Math.sqrt(1 - t * t) + start2;
                } else if (when == EASE_IN_OUT) {
                    t /= d / 2;
                    if (t < 1) return -c / 2 * (Math.sqrt(1 - t * t) - 1) + start2;
                    t -= 2;
                    return c / 2 * (Math.sqrt(1 - t * t) + 1) + start2;
                }
                break;
        }
        return 0;
    }

    /*
     * A map() replacement that allows for specifying easing curves
     * with arbitrary exponents.
     *
     * value :   The value to map
     * start1:   The lower limit of the input range
     * stop1 :   The upper limit of the input range
     * start2:   The lower limit of the output range
     * stop2 :   The upper limit of the output range
     * v     :   The exponent value (e.g., 0.5, 0.1, 0.3)
     * when  :   One of EASE_IN, EASE_OUT, or EASE_IN_OUT
     */
    public static double map3(double value, double start1, double stop1, double start2, double stop2, double v, MapEase when) {
        double c = stop2 - start2;
        double t = value - start1;
        double d = stop1 - start1;
        double out = 0;
        if (when == EASE_IN) {
            t /= d;
            out = c * Math.pow(t, v) + start2;
        } else if (when == EASE_OUT) {
            t /= d;
            out = c * (1 - Math.pow(1 - t, v)) + start2;
        } else if (when == EASE_IN_OUT) {
            t /= d / 2;
            if (t < 1) return c / 2 * Math.pow(t, v) + start2;
            out = c / 2 * (2 - Math.pow(2 - t, v)) + start2;
        }
        return out;
    }
}
