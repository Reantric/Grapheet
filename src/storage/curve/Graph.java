package storage.curve;

import geom.Grid;
import storage.Color;
import storage.ColorType;
import storage.Vector;

import java.util.Arrays;
import java.util.function.Function;

import static geom.DataGrid.WIDTH;
import static processing.core.PConstants.EPSILON;

public class Graph { // 2D graph, fuck 3D
    Color color = new Color(ColorType.GREEN);
    float[] xValues;
    float[] yValues;
    Grid plane;
    double distance = 0.04;
    Vector bounds;
    Vector index = new Vector(0);

    public Graph(Grid plane){
        this.plane = plane;
        double left = plane.canvasToPlane(new Vector(-WIDTH/2f)).x;
        this.bounds = new Vector((float) left,(float) left+WIDTH*plane.getScale().x);
        System.out.println(bounds);
        System.out.println("bruh");
    }

    public Graph(Grid plane, Vector bounds){ // Graph is assumed to be explicitly defined, so bounds refers to xBounds
        this.plane = plane;
        this.bounds = bounds;
    }

    public void setValues(Function<Double,Double> f){
        int ender = (int) Math.floor((bounds.y-bounds.x)/distance - EPSILON);
        xValues = new float[ender+1];
        yValues = new float[ender+1];
        double recipScaleX = 1/plane.getScale().x, recipScaleY = 1/plane.getScale().y;
        for (int i = 0; i < ender; i++){
            double x = bounds.x + i*distance;
            xValues[i] = (float) (recipScaleX * x);
            yValues[i] = (float) (recipScaleY * f.apply(x));
        }
        xValues[ender] = (float) (recipScaleX * bounds.y);
        yValues[ender] = (float) (recipScaleY * f.apply((double) bounds.y));
        System.out.println(Arrays.toString(xValues));
        System.out.println(Arrays.toString(yValues));
    }

    public boolean draw(){
       return draw(3);
    }

    public boolean draw(float time){
        plane.p.stroke(color);
        plane.p.strokeWeight(5);
        for (int i = 0; i < index.x; i++){
            plane.p.line(xValues[i],yValues[i],xValues[i+1],yValues[i+1]);
        }
        return index.easeTo(new Vector(xValues.length-1),time);
    }


    public void setColor(Color color) {
        this.color = color;
    }
}
