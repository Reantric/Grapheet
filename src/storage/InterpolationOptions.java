package storage;

import util.map.MapEase;
import util.map.MapType;

public class InterpolationOptions {
    MapType type;
    double time;
    MapEase ease;

    public InterpolationOptions(MapType type, MapEase ease,  double time){
        this.type = type;
        this.time = time;
        this.ease = ease;
    }

    public InterpolationOptions(MapType type){
        this.type = type;
        this.time = Math.sqrt(2);
        this.ease = MapEase.EASE_IN_OUT;
    }

    public InterpolationOptions(double time){
        this.type = MapType.QUADRATIC;
        this.time = time;
        this.ease = MapEase.EASE_IN_OUT;
    }

    public double getTime() {
        return time;
    }

    public MapType getType() {
        return type;
    }

    public MapEase getEase(){
        return this.ease;
    }
}
