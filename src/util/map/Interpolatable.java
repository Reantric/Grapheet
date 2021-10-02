package util.map;

import storage.InterpolationOptions;

public interface Interpolatable<T> {
    // time in seconds
    boolean interpolate(T other, MapType type, MapEase ease, double time);

    default boolean interpolate(T other, InterpolationOptions options){
        return interpolate(other,options.getType(),options.getEase(),options.getTime());
    }

    default boolean interpolate(T other){
        return interpolate(other,MapType.QUADRATIC,MapEase.EASE_IN_OUT,Math.sqrt(2));
    }

    default boolean interpolate(T other, double time){
        return interpolate(other,MapType.QUADRATIC,MapEase.EASE_IN_OUT,time);
    }
}
