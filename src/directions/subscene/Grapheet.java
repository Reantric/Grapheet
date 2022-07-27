package directions.subscene;

import core.Applet;
import directions.Scene;
import geom.DataGrid;
import storage.Color;
import storage.ColorType;
import storage.dataviz.DataGraph;
import storage.Vector;

import java.util.Arrays;

public class Grapheet extends Scene {
    public DataGrid plane;
    public DataGraph[] dataGraphs;
    public Color[] colorWheel;

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
        for (int i = 0; i < yValLen; i++){
            dataGraphs[i] = new DataGraph(plane,yValues[i], colorWheel[i % colorWheel.length],"John");
        }
        //System.out.println(Arrays.deepToString(yValues));
    }


    @Override
    public boolean executeHelper() {
        step[0] = plane.draw();
        plane.drawMainAxes();
        plane.label();
        if (step[0]) {
          //  step[1] = plane.getCamera().interpolate(new Vector(300, -415), 2.5);
           // step[1] = this.graph();
            step[1] = true;
        }
        if (step[1]) {
          //  plane.getScale().interpolate(new Vector(500, 800 * 150.0f/200));
            plane.getIncrementor().interpolate(new Vector(250,75),8);
           // plane.getSpacing().interpolate(new Vector(200,120));
        }

        return false; //step[1];
    }

    private boolean graph(){
        for (DataGraph g: dataGraphs){
            g.draw();
        }
        DataGraph.update();
        return DataGraph.index >= DataGraph.xValues.length-1;
    }
}
