package geom.curve;

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
    Function<Double,Double> f;

    public Graph(Grid plane){
        this.plane = plane;
        double left = plane.canvasToPlane(new Vector(-WIDTH/2f)).x;
        this.bounds = new Vector((float) left,(float) left+WIDTH*plane.getScale().x);
        System.out.println(bounds);
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
        this.f = f;
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

    private Vector incrementor = new Vector(0);
    public boolean interpolate(Function<Double,Double> g){ //add interpolate(Graph g) later!
        g = g.andThen(t -> -t);
        if (f.equals(g)){
            incrementor.x = 0;
            return true;
        }
        float recipScaleY = 1/plane.getScale().y;
        float recipScaleX = 1/plane.getScale().x;
        for (int i = 0; i < yValues.length; i++){
            yValues[i] = (1-incrementor.x) * ((float) (recipScaleY * f.apply((double) (xValues[i]/recipScaleX)))) + incrementor.x * ((float) (recipScaleY * g.apply((double) (xValues[i]/recipScaleX))));
        }
        if (incrementor.easeTo(1)) {
            this.f = g;
            return true;
        }
        return false;
    }


    public void setColor(Color color) {
        this.color = color;
    }
}
