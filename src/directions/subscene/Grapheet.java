package directions.subscene;

import core.Applet;
import directions.Scene;
import geom.DataGrid;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import storage.dataviz.DataGraph;

import java.util.Arrays;
import static util.Useful.ceilAny;
import static util.Useful.floorAny;

import static geom.DataGrid.HEIGHT;
import static geom.DataGrid.WIDTH;

public class Grapheet extends Scene {
    public DataGrid plane;
    public DataGraph[] dataGraphs;
    public Color[] colorWheel;
    public Vector minMaxGraph = new Vector(Float.MAX_VALUE,Float.MIN_VALUE);
    public double midline;

    public Grapheet(Applet window) {
        super(window);
        runScene = true;
        init();
    }

    private void init(){
        plane = new DataGrid(window);
        String[] strings = window.loadStrings("src/data/datas.csv");
        ColorType[] colorTypes = ColorType.values();
        colorWheel = new Color[colorTypes.length];
        for (int u = 0; u < colorTypes.length; u++)
            colorWheel[u] = new Color(colorTypes[u]);
        System.out.println(Arrays.toString(colorTypes));
        String[] header = strings[0].split(",");
        int dataLen = strings.length-1;
        int yValLen = header.length-1;
        dataGraphs = new DataGraph[yValLen];
        double[] xValues = new double[dataLen];
        double[][] yValues = new double[yValLen][dataLen];
        for (int c = 1; c <= dataLen; c++){
            String[] data = strings[c].split(",");
            xValues[c-1] = Double.parseDouble(data[0]);
            for (int r = 1; r <= yValLen; r++){
                yValues[r-1][c-1] = Double.parseDouble(data[r]);
            }
        }

        DataGraph.setXValues(xValues);
        double maxY = 0;
        for (int i = 0; i < yValLen; i++){
            dataGraphs[i] = new DataGraph(plane,yValues[i], colorWheel[i % colorWheel.length],"John");
            maxY = Math.max(maxY,yValues[i][0]);
        }

        plane.setXOffset(DataGraph.xValues[0]);
        plane.getScale().x = (float) (dataLen/(DataGraph.xValues[dataLen-1] - DataGraph.xValues[0]));
        plane.getScale().y = (float) Math.pow(10,Math.floor(Math.log10(800.0/(0.1 + maxY)))); //(float) floorAny(800.0/maxY,100);
        //System.out.println(Arrays.deepToString(yValues));
    }


    @Override
    public boolean executeHelper() {
        step[0] = plane.draw();
        plane.drawMainAxes();
        plane.label();
        if (step[0]) {
           // step[1] = plane.getCamera().interpolate(new Vector(300, -415), 2.5);
            step[1] = this.graph();
           // step[1] = true;
        }
        if (step[1]) {
            //plane.getIncrementor().interpolate(new Vector(250,75),8);
        }
        if (DataGraph.xDistanceFromOrigin > WIDTH/2-300+plane.getDisplacement().x)
            plane.getCamera().x += DataGraph.moveX;

        if (minMaxGraph.y < plane.getDisplacement().y - HEIGHT/2){ // make sure this works!
            plane.getCamera().y -= 400;
        }

        if (minMaxGraph.x < plane.getDisplacement().y - HEIGHT/2 + 40){ // cuz neg vals are how high u are
            plane.getIncrementor().y = 200*(400 - plane.getDisplacement().y + HEIGHT/2 - 40)/(200*(400-minMaxGraph.x)/plane.getIncrementor().y);
            // ^ --> 400 - scale.y * inc.y / 200 * pointVal[index]
        }

        return false; //step[1];
    }

    private boolean graph(){
        minMaxGraph = new Vector(Float.MAX_VALUE,Float.MIN_VALUE);
        for (DataGraph g: dataGraphs){
            g.draw();
            var eval = g.evaluate();
            minMaxGraph.y = (float) Math.max(minMaxGraph.y,eval);
            minMaxGraph.x = (float) Math.min(minMaxGraph.x,eval);
        }
        midline = (minMaxGraph.x + minMaxGraph.y)/2;
        DataGraph.update();
        return DataGraph.index >= DataGraph.xValues.length-1;
    }
}
