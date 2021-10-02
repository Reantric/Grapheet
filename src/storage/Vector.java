package storage;

import processing.core.PApplet;
import processing.core.PVector;
import util.Mapper;
import util.map.Interpolatable;
import util.map.MapEase;
import util.map.MapType;

import static util.map.MapType.QUADRATIC;

public class Vector extends PVector implements Interpolatable<PVector> { // only dealing with 2D, 3D can fuck off
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

    public boolean interpolate(float o){
        return this.interpolate(new Vector(o), QUADRATIC,MapEase.EASE_IN_OUT, Math.sqrt(2)); // 1.4 seconds
    }

    public boolean interpolate(float o, MapType type, double time){
        return this.interpolate(new Vector(o), type, MapEase.EASE_IN_OUT, time); // 1.4 seconds
    }

    public boolean interpolate(PVector o, MapType type, MapEase ease, double time){ // for now, Quadratic Map, fix extra computation power for one dim easing
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

    public Vector map(Vector start, Vector end, Vector startNew, Vector endNew){
        return new Vector(PApplet.map(x,start.x,end.x,startNew.x,endNew.x),PApplet.map(y,start.y,end.y,startNew.y,endNew.y));
    }


}
