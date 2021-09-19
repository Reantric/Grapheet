package geom;

import core.Applet;
import processing.core.PFont;
import processing.core.PVector;
import storage.Color;
import storage.ColorType;
import storage.IntVector;
import storage.Vector;
import storage.curve.Graph;
import util.Mapper;
import util.map.MapEase;
import util.map.MapType;

import java.text.DecimalFormat;
import java.util.function.Function;

import static geom.DataGrid.HEIGHT;
import static geom.DataGrid.WIDTH;
import static processing.core.PConstants.*;
import static processing.core.PConstants.CENTER;
import static util.Useful.ceilAny;
import static util.Useful.floorAny;

public class Grid {
    public Applet p;
    Color color, textColor;
    public Vector incrementor = new Vector(200,200), startingIncrementor = new Vector(incrementor), baseIncrementor = new Vector(incrementor);
    public Vector camera = new Vector(0,0), startingCamera = new Vector(0,0);
    Vector spacing = new Vector(0,0);
    Vector begin = new Vector(), end = new Vector();
    PVector displacement = new Vector(0,0);
    Vector scale = new Vector(1/200f,1/200f); // tick marks are 200 by default, so scale by 1/200 to get to 1
    IntVector ender = new IntVector();
    //add InterpolationOptions

    DecimalFormat df = new DecimalFormat("####.##");
    PFont font;
    // ?

    public Grid(Applet window) {
        this.p = window;
        this.color = new Color(ColorType.WHITE);
        this.textColor = new Color(ColorType.WHITE);
        this.textColor.setAlpha(0);
        String commonPath = "src\\data\\";
        font = p.createFont(commonPath + "cmunbmr.ttf", 150, true);
        p.textFont(font);
        update();
    }

    public void setColor(Color color){
        this.color = color;
    }

    private void update(){
        displacement = PVector.sub(camera,startingCamera);
        scale = new Vector(baseIncrementor.x/(startingIncrementor.x * incrementor.x),baseIncrementor.y/(startingIncrementor.y * incrementor.y)); // wtf

        // once fadingLines occur, update baseIncrementor using rules from 2DGP
        begin.x = (float) ceilAny(displacement.x - WIDTH/2f,incrementor.x);
        end.x = (float) ceilAny(camera.x + WIDTH/2f,incrementor.x);
        begin.y = (float) floorAny(HEIGHT/2f + camera.y, incrementor.y);
        end.y = (float) floorAny(-HEIGHT/2f + camera.y, incrementor.y); //This is the top of the p (as it is translated based on cameraPos)
        ender.x = ceilToNearestOdd((end.x-begin.x)/incrementor.x);
        ender.y =  ceilToNearestOdd((begin.y-end.y)/incrementor.y);
    }

    public void setScale(Vector scale){
        this.scale = scale;
    }

    public Vector getBegin(){
        return this.begin;
    }

    public Vector getEnd(){
        return this.end;
    }

    private int ceilToNearestOdd(float a){
        return (Math.round(a)/2)*2 + 1;
    }

    private void generate(){
        float largeStroke = 5, smallStroke = 2.5f;

        p.noFill();
        p.stroke(color);

        for (int i = 0; i < ender.x; i++){ // draws vert lines
            float x = begin.x + i*incrementor.x;
           // p.println(x-startingBegin.x, 2*incrementor.x);
            if ((ender.x-1)/2 % 2 == (i % 2))
                p.strokeWeight(largeStroke);
            else
                p.strokeWeight(smallStroke);

            p.line(x,camera.y+spacing.y,x,camera.y-spacing.y);
        }


        for (int j = 0; j < ender.y; j++){  // draws horiz lines, processing draws y up to down cuz flipped (so invert the bounds)
            float y = begin.y - j*incrementor.y;
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

        p.line(startingCamera.x-spacing.x,startingCamera.y,startingCamera.x+spacing.x,startingCamera.y);
        p.line(startingCamera.x,startingCamera.y-spacing.y,startingCamera.x,startingCamera.y+spacing.y);
    }

    Color darkGrey = new Color(0,0,0,60);
    public void label(){
        p.textSize(50);
        p.textAlign(RIGHT,CENTER);
        p.fill(textColor);
        for (int j = 0; j < ender.y; j++){
            float y = begin.y - j*incrementor.y;
            if ((ender.y-1)/2 % 2 == (j % 2)) {
                // -600 is the original begin.y <--- dont trust anything idk
                float txt = textify(y,Y);
               // p.println(txt);
                float yCoord;
                yCoord = y - 40;

                p.text(df.format(txt), displacement.x - 6, yCoord); // account for everything !

            }
        }

        p.textAlign(CENTER,CENTER);
        p.noStroke();
        // increment end because text needs to show up before line (super efficient line)
        for (int i = 0; i < ender.x; i++){
            float x = begin.x + i*incrementor.x;
            float txt = textify(x,X);
            // p.println(txt);
            if (txt == 0)
                continue;

            if ((ender.x-1)/2 % 2 == (i % 2)) {
                 // -600 is the original begin.x
                 String formattedNumber = df.format(txt);
                 float tWidth = p.textWidth(formattedNumber);
                 p.fill(darkGrey);
                 p.rect(x - tWidth / 2, displacement.y + 10, x + tWidth / 2, displacement.y + 64);
                 p.fill(textColor);
                 p.text(formattedNumber, x, displacement.y + 30); // account for everything !
            }
        }
    }

    private float textify(float r, int XorY){
        if (XorY == X)
            return scale.x * r; // begin.x ORIGINAL
        return -scale.y * r; // begin.y ORIGINAL
    }


    public boolean draw(){
        p.translate(PVector.mult(camera,-1));
        update();
        generate();
        drawMainAxes();
        label();
        //p.println(incrementor);
        return spacing.easeTo(new Vector(WIDTH/2f,HEIGHT/2f),1) & textColor.getAlpha().easeTo(100);
    }

    public Graph graph(Function<Double,Double> f){
        Graph graph = new Graph(this);
        graph.setValues(f.andThen(t -> -t));
        return graph;
    }

    public Vector getScale() {
        return this.scale;
    }

    public Vector canvasToPlane(Vector canvPoint){
        return new Vector(canvPoint.x * scale.x,-canvPoint.y * scale.y);
    }

    public Vector planeToCanvas(Vector planePoint){
        return new Vector(planePoint.x / scale.x, -planePoint.y / scale.y);
    }
}
