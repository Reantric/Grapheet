package storage;

public class TruthVector {
    public boolean x, y;
    public TruthVector (boolean x, boolean y){
        this.x = x;
        this.y = y;
    }

    public TruthVector(){
        this.x = false;
        this.y = false;
    }

    @Override
    public String toString(){
        return String.format("[%s,%s]",x,y);
    }
}
