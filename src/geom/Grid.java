package geom;

import core.Applet;
import processing.core.PFont;
import processing.core.PVector;
import storage.Color;
import storage.ColorType;
import storage.IntVector;
import storage.Vector;
import geom.curve.Graph;

import java.text.DecimalFormat;
import java.util.function.Function;

import static processing.core.PConstants.*;
import static util.Useful.ceilAny;
import static util.Useful.floorAny;

public class Grid {
    public static final int X = 3;
    public static final int Y = 5;

    private final Applet p;
    private Color color;
    private final Color textColor;
    private final Vector gridSpacing = new Vector(200,200);
    private final Vector startingGridSpacing = new Vector(gridSpacing);
    private final Vector baseGridSpacing = new Vector(gridSpacing);
    private final Vector camera = new Vector(0,0);
    private final Vector spacing = new Vector(0,0);
    private final Vector begin = new Vector();
    private final Vector end = new Vector();
    private final Vector scale = new Vector(1/200f,1/200f); // tick marks are 200 by default, so scale by 1/200 to get to 1
    private final IntVector ender = new IntVector();
    //add InterpolationOptions

    private final DecimalFormat df = new DecimalFormat("####.##");
    private PFont font;
    // ?

    public Grid(Applet window) {
        this.p = window;
        this.color = new Color(ColorType.WHITE);
        this.textColor = new Color(ColorType.WHITE);
        this.textColor.setAlpha(0);
        update();
    }

    public void setColor(Color color){
        this.color = color;
    }

    private void update(){
        float viewportWidth = getViewportWidth();
        float viewportHeight = getViewportHeight();
        float halfViewportWidth = viewportWidth / 2f;
        float halfViewportHeight = viewportHeight / 2f;
        scale.x = baseGridSpacing.x/(startingGridSpacing.x * gridSpacing.x);
        scale.y = baseGridSpacing.y/(startingGridSpacing.y * gridSpacing.y);
        // perhaps scale ought to decide gridSpacing?


        // once fadingLines occur, update baseGridSpacing using rules from 2DGP
        begin.x = (float) ceilAny(camera.x - halfViewportWidth, gridSpacing.x);
        end.x = (float) ceilAny(camera.x + halfViewportWidth, gridSpacing.x);
        begin.y = (float) floorAny(camera.y + halfViewportHeight, gridSpacing.y);
        end.y = (float) floorAny(camera.y - halfViewportHeight, gridSpacing.y); //This is the top of the p (as it is translated based on cameraPos)
        ender.x = ceilToOdd((end.x-begin.x)/gridSpacing.x);
        ender.y =  ceilToOdd((begin.y-end.y)/gridSpacing.y);
    }

    public Vector getSpacing() {
        return spacing;
    }

    public Color getTextColor() {
        return textColor;
    }

    public Vector getGridSpacing() {
        return gridSpacing;
    }

    public Vector getBegin(){
        return this.begin;
    }

    public Vector getEnd(){
        return this.end;
    }

    private int ceilToOdd(float value){
        int ceil = Math.max(1, (int) Math.ceil(value - 1e-6f));
        return (ceil & 1) == 0 ? ceil + 1 : ceil;
    }

    private void generate(){
        float largeStroke = 5, smallStroke = 2.5f;

        p.noFill();
        p.stroke(color);

        for (int i = 0; i < ender.x; i++){ // draws vert lines
            float x = begin.x + i*gridSpacing.x;
           // p.println(x-startingBegin.x, 2*gridSpacing.x);
            if ((ender.x-1)/2 % 2 == (i % 2))
                p.strokeWeight(largeStroke);
            else
                p.strokeWeight(smallStroke);

            p.line(x,camera.y+spacing.y,x,camera.y-spacing.y);
        }


        for (int j = 0; j < ender.y; j++){  // draws horiz lines, processing draws y up to down cuz flipped (so invert the bounds)
            float y = begin.y - j*gridSpacing.y;
            if ((ender.y-1)/2 % 2  == (j % 2))
                p.strokeWeight(largeStroke);
            else
                p.strokeWeight(smallStroke);

            p.line(camera.x - spacing.x,y,camera.x + spacing.x,y);
        }
    }

    private void drawMainAxes(){
        p.stroke(new Color(ColorType.WHITE)); // optimize in the future
        p.strokeWeight(6);

        p.line(-spacing.x,0,spacing.x,0);
        p.line(0,-spacing.y,0,spacing.y);
    }

    private final Color darkGrey = new Color(0,0,0,60);
    public void label(){
        if (font == null) {
            String commonPath = "src/data/";
            font = p.createFont(commonPath + "cmunbmr.ttf", 150, true);
        }
        p.textFont(font);
        p.textSize(50);
        p.textAlign(RIGHT,CENTER);
        p.fill(textColor);
        for (int j = 0; j < ender.y; j++){
            float y = begin.y - j*gridSpacing.y;
            if ((ender.y-1)/2 % 2 == (j % 2)) {
                // -600 is the original begin.y <--- dont trust anything idk
                float txt = textify(y,Y);
               // p.println(txt);
                float yCoord;
                yCoord = y - 40;

                p.text(df.format(txt), camera.x - 6, yCoord); // account for everything !

            }
        }

        p.textAlign(CENTER,CENTER);
        p.noStroke();
        // increment end because text needs to show up before line (super efficient line)
        for (int i = 0; i < ender.x; i++){
            float x = begin.x + i*gridSpacing.x;
            float txt = textify(x,X);
            // p.println(txt);
            if (txt == 0)
                continue;

            if ((ender.x-1)/2 % 2 == (i % 2)) {
                 // -600 is the original begin.x
                 String formattedNumber = df.format(txt);
                 float tWidth = p.textWidth(formattedNumber);
                 p.fill(darkGrey);
                 p.rect(x - tWidth / 2, camera.y + 10, x + tWidth / 2, camera.y + 64);
                 p.fill(textColor);
                 p.text(formattedNumber, x, camera.y + 30); // account for everything !
            }
        }
    }

    private float textify(float r, int XorY){
        if (XorY == X)
            return scale.x * r; // begin.x ORIGINAL
        if (r == 0)
            return 0;
        return -scale.y * r; // begin.y ORIGINAL
    }


    public void render(){
        p.translate(PVector.mult(camera,-1));
        update();
        generate();
        drawMainAxes();
        label();
    }

    public Graph graph(Function<Double,Double> f){
        Graph graph = new Graph(this);
        graph.setValues(f.andThen(t -> -t));
        return graph;
    }

    public Applet applet() {
        return p;
    }

    public float getViewportWidth() {
        return p.width;
    }

    public float getViewportHeight() {
        return p.height;
    }

    public Vector getScale() {
        return this.scale;
    }

    public Vector getCamera() {
        return camera;
    }

    public Vector canvasToPlane(Vector canvPoint){
        return new Vector(canvPoint.x * scale.x,-canvPoint.y * scale.y);
    }

    public Vector planeToCanvas(Vector planePoint){
        return new Vector(planePoint.x / scale.x, -planePoint.y / scale.y);
    }
}
