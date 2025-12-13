package storage;

import core.Applet;
import processing.core.PApplet;
import processing.core.PVector;
import util.Mapper;
import util.map.Interpolatable;
import util.map.MapEase;
import util.map.MapType;

import static util.map.MapType.QUADRATIC;

public class PreciseVector  implements Interpolatable<PreciseVector> { // only dealing with 2D, 3D can fuck off
    private long incrementor = 0;
    private double uneasedX, uneasedY;
    public static PreciseVector UP = new PreciseVector(0,-500);
    public static PreciseVector DOWN = new PreciseVector(0,500);
    public static PreciseVector LEFT = new PreciseVector(-500,0);
    public static PreciseVector RIGHT = new PreciseVector(500,0);
    public double x,y;
    public PreciseVector(double x, double y){
        this.x = x;
        this.y = y;
        uneasedX = this.x;
        uneasedY = this.y;
    }

    public void setX(double x){ // abrupt change
        this.x = x;
        uneasedX = x;
    }

    public void setY(double y){ // abrupt change
        this.y = y;
        uneasedY = y;
    }

    public PreciseVector(double x) {
        this(x,0);
    }

    public PreciseVector(PreciseVector o) {
        this.x = o.x;
        this.y = o.y;
    }

    public PreciseVector() {
        this.x = 0;
        this.y = 0;
    }

    public boolean interpolate(double o){
        return this.interpolate(new PreciseVector(o), QUADRATIC, MapEase.EASE_IN_OUT, Math.sqrt(2)); // 1.4 seconds
    }

    public boolean interpolate(double o, MapType type, double time){
        return this.interpolate(new PreciseVector(o), type, MapEase.EASE_IN_OUT, time); // 1.4 seconds
    }

    public boolean interpolate(PreciseVector o, MapType type, MapEase ease, double time){ // for now, Quadratic Map, fix extra computation power for one dim easing
        long incFinal = (long) (time*60);
        if (this.equals(o) || incrementor == incFinal) {
            this.uneasedX = this.x;
            this.uneasedY = this.y;
            incrementor = 0;
            return true;
        }
        incrementor++;
        this.x = (float) Mapper.map2(incrementor,0,incFinal,this.uneasedX,o.x, type, ease);
        this.y = (float) Mapper.map2(incrementor,0,incFinal,this.uneasedY,o.y, type, ease);
        return false;
    }

    public PreciseVector map(PreciseVector start, PreciseVector end, PreciseVector startNew, PreciseVector endNew){
        return new PreciseVector(Applet.linMap(x,start.x,end.x,startNew.x,endNew.x),Applet.linMap(y,start.y,end.y,startNew.y,endNew.y));
    }


}