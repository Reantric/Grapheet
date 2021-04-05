package core;

import processing.core.PVector;
import util.Mapper;
import util.map.MapEase;
import util.map.MapType;

public class Vector extends PVector { // only dealing with 2D, 3D can fuck off
    private long incrementor = 0;
    private float uneasedX, uneasedY;
    public Vector(float x, float y){
        super(x,y);
        uneasedX = this.x;
        uneasedY = this.y;
    }

    public boolean easeTo(PVector o){ // for now, Quadratic Map
        long incFinal = 100;
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
