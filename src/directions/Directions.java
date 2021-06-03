package directions;

import core.Applet;
import geom.Grid;
import storage.Color;
import storage.ColorType;
import storage.Graph;

import java.util.Arrays;
import java.util.stream.Stream;

public class Directions {
    public Grid plane;
    protected Applet window;
    public boolean[] step = new boolean[100];
    public Graph[] graphs;
    public Color[] colorWheel;

    public Directions(Applet window){
        this.window = window;
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

    public boolean execute(){
        step[0] = plane.draw();
        if (step[0])
            step[1] = this.graph(); // Combine these together, elim terminate method

        plane.drawMainAxes();
        plane.label();
        return step[1];
    }

    private boolean graph(){
        for (Graph g: graphs){
            g.draw();
        }
        Graph.update();
        return Graph.index >= 4/0.004f-0.6f;
    }
}
