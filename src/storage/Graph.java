package storage;

import core.Applet;
import geom.Grid;
import processing.core.PShape;
import processing.core.PVector;
import util.Mapper;
import util.map.MapEase;
import util.map.MapType;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    public Function<Double,Double> function;
    private static float distance = 0.004f; // dont want to make final
    public static double index = 0;

    public Graph(Grid grid, double[] pv, Color color, String name){
        this.grid = grid;
        this.pointValues = pv;
        this.color = color;
        this.name = name;
    }

    public Graph(Grid grid, Function<Double, Double> function, Color color){
        this.grid = grid;
        this.color = color;
        this.function = function;
        initializeValues();
        System.out.println(xValues.length);
    }

    public Graph(Grid grid, double[] pv, Color color){
        this(grid,pv,color,"");
    }

    public void draw(){
        grid.getProcessingInstance().shape(createGraphShape());
   //     initializeValues();
      //  distance *= 0.994f;
        if (index < xValues.length)
            index++;
    }

    private void initializeValues(){
        xValues = DoubleStream.iterate(0,t -> t + distance).limit(1 + (long) Math.ceil(20/distance)).toArray();
        pointValues = Arrays.stream(xValues).map(function::apply).toArray();
    }

    private PShape createGraphShape(){
        Applet p = grid.getProcessingInstance();
        Vector incrementor = grid.getIncrementor();
        Vector scale = grid.getScale();
        PShape shape = p.createShape();
        shape.colorMode(HSB);
        shape.beginShape(LINES);
        shape.strokeWeight(4);
        shape.stroke(color.getHue().getValue(),color.getBrightness().getValue(),color.getSaturation().getValue(),color.getAlpha().getValue());
       // double bruh = Mapper.map2(index,0,xValues.length,0,xValues.length, MapType.QUADRATIC, MapEase.EASE_IN_OUT);
        p.stroke(ColorType.MAGENTA);

       // p.line(grid.getDisplacement().x,1000,grid.getDisplacement().x,-1000); Midliner

        double index = (620+grid.getDisplacement().x) * 1/scale.x * 1/distance;
        System.out.println(index);
        for (int i = 1; i < index; i++){
            shape.vertex(165-WIDTH/2f + scale.x * (float) xValues[i-1],400-scale.y * (float) pointValues[i-1]);
            shape.vertex(165-WIDTH/2f + scale.x * (float) xValues[i],400-scale.y * (float) pointValues[i]);
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
