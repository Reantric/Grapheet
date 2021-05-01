package storage;

import core.Applet;
import geom.Grid;

public class DataPoint { // Must store x values separately, this only holds y values
    // for efficiency!
    public float[] pointValues;
    public Color color;
    public String name;

    public DataPoint(float[] pv){
        this.pointValues = pv;
    }

    public void draw(int index, Grid grid){
        Applet p = grid.getProcessingInstance();
    }
}
