package storage.dataviz;

import core.Applet;
import geom.DataGrid;
import processing.core.PFont;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;
import storage.TruthVector;
import storage.Vector;
import util.Mapper;
import util.map.MapEase;
import util.map.MapType;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.DoubleStream;

import static geom.DataGrid.WIDTH;
import static processing.core.PConstants.HSB;
import static processing.core.PConstants.LINES;

public class DataGraph {
    public static double[] xValues; // Set them here, but Graph methods do not touch this!
    public static int activeGraphCount = 0;
    public double[] pointValues;
    public Color color;
    public String name;
    public DataGrid dataGrid;
    public Function<Double,Double> function;
    private static double distance = 0.004; // dont want to make final, distance between x
    public static double incrementor = 0;
    public static double xDistanceFromOrigin = 161-WIDTH/2f;
    public int firstInd = 1;
    public static double index = 0;
    public static PFont nameFont;


    public DataGraph(DataGrid dataGrid, double[] pv, Color color, String name){
        this.dataGrid = dataGrid;
        this.pointValues = pv;
        this.color = color;
        this.name = name;
        distance = xValues[1]-xValues[0]; // let's just assume for the sake of niceness that it is the same
        activeGraphCount++; // now this will also include inactive, fix later!
        // everywhere
        //System.out.println(xValues.length);
        //System.out.println(distance);
    }

    public DataGraph(DataGrid dataGrid, Function<Double, Double> function, Color color){
        this.dataGrid = dataGrid;
        this.color = color;
        this.function = function;
        initializeValues();
        System.out.println(xValues.length);
        moveX = xValues.length/2000f;
    }

    public DataGraph(DataGrid dataGrid, double[] pv, Color color){
        this(dataGrid,pv,color,"");
    }

    public static void setXValues(double[] xVals) {
        DataGraph.xValues = xVals;
        moveX = xValues.length/2000f;
    }

    public void draw(){
      //  grid.getProcessingInstance().shape(createGraphShape());
        drawLineShape();
   //     initializeValues();
   //     distance *= 0.994f;
       // if (incrementor < xValues.length)
         //   incrementor++;
    }

    public static double moveX = 1.0/100;
    public static void update(){
        incrementor += moveX;
    }

    private void initializeValues(){
        xValues = DoubleStream.iterate(0,t -> t + distance).limit(1 + (long) Math.ceil(4/distance)).toArray();
        pointValues = Arrays.stream(xValues).map(function::apply).toArray();
    }

    private PShape createGraphShape(){
        Applet p = dataGrid.getProcessingInstance();
        Vector incrementor = dataGrid.getIncrementor();
        Vector scale = dataGrid.getScale();
        PShape shape = p.createShape();
        shape.colorMode(HSB);
        shape.beginShape(LINES);
        shape.strokeWeight(4);
        shape.stroke(color.getHue().getValue(),color.getBrightness().getValue(),color.getSaturation().getValue(),color.getAlpha().getValue());
       // double bruh = Mapper.map2(index,0,xValues.length,0,xValues.length, MapType.QUADRATIC, MapEase.EASE_IN_OUT);
        p.stroke(ColorType.MAGENTA);

       // p.line(grid.getDisplacement().x,1000,grid.getDisplacement().x,-1000); Midliner

        double index = (620+ dataGrid.getDisplacement().x) * 1/scale.x * 1/distance;
        System.out.println(index);
        for (int i = 1; i < index; i++){
            shape.vertex(165-WIDTH/2f + scale.x * (float) xValues[i-1],400-scale.y * (float) pointValues[i-1]);
            shape.vertex(165-WIDTH/2f + scale.x * (float) xValues[i],400-scale.y * (float) pointValues[i]);
        }
        shape.endShape();
        return shape;
    }

    /**
     * To be used with P2D and P2D only (not sure about the benefits really besides that)
     */
    private void drawLineShape(){
        Applet p = dataGrid.getProcessingInstance();
        p.strokeWeight(5.5f);
        p.stroke(color);
        Vector scale = dataGrid.getScale();
        Vector inc = dataGrid.getIncrementor();
        TruthVector moving = dataGrid.getMoving();
        if (incrementor < xValues.length) // start moving once this is no longer true?
            index = Mapper.map2(incrementor,0,xValues.length,0,xValues.length, MapType.LINEAR, MapEase.EASE_IN_OUT); // og QUADRATIC
        else
            index = xValues.length;
        xDistanceFromOrigin = 161-WIDTH/2f + scale.x * (float) xValues[Math.max(0,(int) index-1)];
        while (firstInd < index && xValues[firstInd] * scale.x  < dataGrid.getDisplacement().x) // - WIDTH/4 if you want line to persist
            firstInd++;

        double offset = dataGrid.getXOffset();
        for (int i = firstInd; i < index; i++){
            p.line(161-WIDTH/2f + scale.x * (float) (xValues[i-1] - offset),400-scale.y * inc.y / 200 * (float) pointValues[i-1],161-WIDTH/2f + scale.x * (float) (xValues[i] - offset),400-scale.y * inc.y/200 * (float) pointValues[i]);
        }
        p.fill(ColorType.WHITE);
        p.noStroke();
        p.circle(161-WIDTH/2f + scale.x * (float) xValues[Math.max(0,(int) index-1)],400-scale.y * inc.y/200 * (float) pointValues[Math.max(0,(int) index-1)],20);
        info();
    }

    public void info(){
        // ok lets go
        Applet p = dataGrid.getProcessingInstance();
        Vector scale = dataGrid.getScale();
        Vector inc = dataGrid.getIncrementor();
        p.fill(color);
        p.textFont(dataGrid.getNameFont());
        p.textSize(50);
        p.text(name,90+161-WIDTH/2f + scale.x * (float) xValues[Math.max(0,(int) index-1)],-20 + 400-scale.y * inc.y/200 * (float) pointValues[Math.max(0,(int) index-1)]);
    }


    public double evaluate(){
        return 400-dataGrid.getScale().y * dataGrid.getIncrementor().y / 200 * (float) pointValues[Math.max(0,(int) index-1)];
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
