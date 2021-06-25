package directions.subscene;

import core.Applet;
import directions.Scene;
import geom.Grid;
import storage.Color;
import storage.ColorType;
import storage.Graph;
import storage.Vector;

import java.util.Arrays;

public class Grapheet extends Scene {
    public Grid plane;
    public Graph[] graphs;
    public Color[] colorWheel;

    public Grapheet(Applet window) {
        super(window);
        runScene = false;
       // init();
    }

    public void init(){
        plane = new Grid(window);
        String[] strings = window.loadStrings("src\\data\\datas.csv");
        ColorType[] colorTypes = ColorType.values();
        colorWheel = new Color[colorTypes.length];
        for (int u = 0; u < colorTypes.length; u++)
            colorWheel[u] = new Color(colorTypes[u]);

        System.out.println(Arrays.toString(colorTypes));
        String[] header = strings[0].split(",");
        int dataLen = strings.length-1;
        int yValLen = header.length-1;
        graphs = new Graph[yValLen];
        double[] xValues = new double[dataLen];
        double[][] yValues = new double[yValLen][dataLen];
        for (int c = 1; c <= dataLen; c++){
            String[] data = strings[c].split(",");
            xValues[c-1] = Double.parseDouble(data[0]);
            for (int r = 1; r <= yValLen; r++){
                yValues[r-1][c-1] = Double.parseDouble(data[r]);
            }
        }

        Graph.setXValues(xValues);
        for (int i = 0; i < yValLen; i++){
            graphs[i] = new Graph(plane,yValues[i], colorWheel[i % colorWheel.length],"John");
        }
        //System.out.println(Arrays.deepToString(yValues));
    }


    @Override
    public boolean execute() {
        step[0] = plane.draw();
        if (step[0])
            plane.getCamera().easeTo(new Vector(200,-200),6);
        //    step[1] = this.graph();

        plane.drawMainAxes();
        plane.label();
        //  plane.getIncrementor().easeTo(new Vector(300,300),9);
        return false; //step[1];
    }

    private boolean graph(){
        for (Graph g: graphs){
            g.draw();
        }
        Graph.update();
        return Graph.index >= Graph.xValues.length-1;
    }
}
