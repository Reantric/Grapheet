package storage;

import processing.core.PVector;
import util.Mapper;
import util.map.Easable;
import util.map.MapEase;
import util.map.MapType;

public class Vector extends PVector implements Easable<PVector> { // only dealing with 2D, 3D can fuck off
    private long incrementor = 0;
    private float uneasedX, uneasedY;
    public Vector(float x, float y){
        super(x,y);
        uneasedX = this.x;
        uneasedY = this.y;
    }

    public Vector(float x) {
        this(x,0);
    }

    public Vector(Vector o) {
        this.x = o.x;
        this.y = o.y;
    }

    public Vector() {
        this.x = 0;
        this.y = 0;
    }

    public boolean easeTo(float o){
        return this.easeTo(new Vector(o),Math.sqrt(2)); // 1.4 seconds
    }

    public boolean easeTo(PVector o){
        return this.easeTo(o,Math.sqrt(2));
    }

    public boolean easeTo(PVector o, double time){ // for now, Quadratic Map, fix extra computation power for one dim easing
        long incFinal = (long) (time*60);
        if (incrementor == incFinal) {
            this.uneasedX = this.x;
            this.uneasedY = this.y;
            incrementor = 0;
            return true;
        }
        incrementor++;
        this.x = (float) Mapper.map2(incrementor,0,incFinal,this.uneasedX,o.x, MapType.QUADRATIC, MapEase.EASE_IN_OUT);
        this.y = (float) Mapper.map2(incrementor,0,incFinal,this.uneasedY,o.y, MapType.QUADRATIC, MapEase.EASE_IN_OUT);
        return false;
    }

}
