package geom.curve;

import geom.Grid;
import processing.core.PConstants;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import util.map.Interpolatable;
import util.map.MapEase;
import util.map.MapType;

import java.util.function.Function;

import static geom.DataGrid.WIDTH;
import static processing.core.PConstants.EPSILON;

public class Graph implements Interpolatable<Function<Double,Double>> { // 2D graph, fuck 3D, maybe change Interp<Func> to Interp<Graph>?
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
        configureDistance();
        double left = plane.canvasToPlane(new Vector(-WIDTH/2f)).x;
        this.bounds = new Vector((float) left,(float) left+WIDTH*plane.getScale().x);
        //System.out.println(bounds);
    }

    public Graph(Grid plane, Vector bounds){ // Graph is assumed to be explicitly defined, so bounds refers to xBounds
        this.plane = plane;
        configureDistance();
        this.bounds = bounds;
    }

    private void configureDistance() {
        // Sample at roughly 2 screen pixels per segment to avoid visibly faceted curves.
        distance = Math.max(0.002, plane.getScale().x * 2.0);
    }

    public void setValues(Function<Double,Double> f){ // TODO: add bounds check for NaN numbers
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
        this.f = f;
    }

    public void render() {
        plane.p.stroke(color);
        plane.p.strokeWeight(5);
        plane.p.noFill();

        int lastIndex = Math.min(xValues.length - 1, Math.max(0, (int) index.x));
        boolean drawingSegment = false;

        for (int i = 0; i < lastIndex; i++){
            boolean currentVisible = isVisible(yValues[i]);
            boolean nextVisible = isVisible(yValues[i + 1]);

            if (currentVisible && nextVisible) {
                if (!drawingSegment) {
                    plane.p.beginShape();
                    plane.p.vertex(xValues[i], yValues[i]);
                    drawingSegment = true;
                }
                plane.p.vertex(xValues[i + 1], yValues[i + 1]);
            } else if (drawingSegment) {
                plane.p.endShape();
                drawingSegment = false;
            }
        }

        if (drawingSegment) {
            plane.p.endShape(PConstants.OPEN);
        }
    }

    private boolean isVisible(float yValue) {
        return yValue <= 1.33f * plane.getBegin().y && yValue >= 1.33f * plane.getEnd().y;
    }

    public boolean advanceReveal() {
        return advanceReveal(3);
    }

    public boolean advanceReveal(float time) {
        return index.interpolate(new Vector(xValues.length-1),time);
    }

    public boolean reveal(){
       return reveal(3);
    }

    public boolean reveal(float time){
        render();
        return advanceReveal(time);
    }

    public boolean draw(){
       return draw(3);
    }

    public boolean draw(float time){
        return reveal(time);
    }

    private Vector incrementor = new Vector(0);
    public boolean interpolate(Function<Double,Double> g, MapType type, MapEase ease, double time){ //add interpolate(Graph g) later!
        g = g.andThen(t -> -t);
        if (f.equals(g)){
            return true;
        }
        float recipScaleY = 1/plane.getScale().y;
        float recipScaleX = 1/plane.getScale().x;
        for (int i = 0; i < yValues.length; i++){
            yValues[i] = (1-incrementor.x) * ((float) (recipScaleY * f.apply((double) (xValues[i]/recipScaleX)))) + incrementor.x * ((float) (recipScaleY * g.apply((double) (xValues[i]/recipScaleX))));
        }
        if (incrementor.interpolate(new Vector(1), type,ease,time)) {
            this.f = g;
            incrementor = new Vector(0);
            return true;
        }
        return false;
    }


    public void setColor(Color color) {
        this.color = color;
    }
}
