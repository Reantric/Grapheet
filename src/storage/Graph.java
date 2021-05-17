package storage;

import core.Applet;
import geom.Grid;
import processing.core.PShape;
import processing.core.PVector;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static geom.Grid.HEIGHT;
import static geom.Grid.WIDTH;
import static processing.core.PConstants.HSB;
import static processing.core.PConstants.LINES;

public class Graph { // Must store x values separately, this only holds y values
    // for efficiency!
    public static double[] xValues; // Set them here, but Graph methods do not touch this!
    public double[] pointValues;
    public Color color;
    public String name;
    public Grid grid;
    private static float distance = 1; // dont want to make final

    public Graph(Grid grid, double[] pv, Color color, String name){
        this.grid = grid
        this.pointValues = pv;
        this.color = color;
        this.name = name;
    }

    public Graph(Grid grid, Function<Double, Double> function, Color color){
       // this.p = grid.getProcessingInstance();
        //this.pointValues = IntStream.range(0, (int) (8/distance)).asDoubleStream().map(t -> function.apply(t*distance)).toArray();
        this(grid,IntStream.range(0, (int) (8/distance)).asDoubleStream().map(t -> function.apply(t*distance)).toArray(),color);
        xValues = IntStream.range(0,(int) (8/distance)).asDoubleStream().toArray();
        System.out.println(Arrays.toString(pointValues));
    }

    public Graph(Grid grid, double[] pv, Color color){
        this(grid,pv,color,"");
    }

    public void draw(){
        p.stroke(color);
    }

    private PShape createGraphShape(){
        Applet p = grid.getProcessingInstance();
        PVector displacement = grid.getDisplacement();
        PShape shape = p.createShape();
        shape.colorMode(HSB);
        shape.stroke(color.getHue().getValue(),color.getBrightness().getValue(),color.getSaturation().getValue(),color.getAlpha().getValue());
        shape.beginShape(LINES);
        for (int i = 1; i < xValues.length; i++){
            shape.vertex(displacement.x + (float) xValues[i-1],(float) pointValues[i-1]);
            shape.vertex(displacement.y + (float) xValues[i],(float) pointValues[i]);
        }
        shape.endShape();
        return shape;
    }

    public void setPointValues(double[] pv){
        this.pointValues = pv;
    }

    public void setColor(Color color){
        this.color = color;
    }

    public void setName(String name){
        this.name = name;
    }
}
