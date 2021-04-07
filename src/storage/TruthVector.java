package storage;

public class TruthVector {
    public boolean x, y;
    public TruthVector (boolean x, boolean y){
        this.x = x;
        this.y = y;
    }

    public TruthVector(){
        this.x = false;
        this.y = true;
    }
}
