package storage;

import core.Applet;
import geom.Grid;

import java.util.function.Function;

public class Graph { // Must store x values separately, this only holds y values
    // for efficiency!
    public float[] pointValues;
    public Color color;
    public String name;

    public Graph(float[] pv){
        this.pointValues = pv;
    }

    public Graph(Function<Double, Double> function){

    }

    public void draw(int index, Grid grid){
        Applet p = grid.getProcessingInstance();
    }

    public void setPointValues(float[] pv){
        this.pointValues = pv;
    }

    public void setColor(Color color){
        this.color = color;
    }

    public void setName(String name){
        this.name = name;
    }
}
