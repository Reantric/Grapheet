package geom;

import core.Applet;
import processing.core.PFont;
import processing.core.PVector;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import util.Mapper;
import util.map.MapEase;
import util.map.MapType;

import java.text.DecimalFormat;

import static geom.DataGrid.HEIGHT;
import static geom.DataGrid.WIDTH;
import static processing.core.PConstants.*;
import static processing.core.PConstants.CENTER;
import static util.Useful.ceilAny;
import static util.Useful.floorAny;

public class Grid {
    Applet p;
    Color color;
    Vector incrementor = new Vector(200,200), startingIncrementor = new Vector(incrementor);
    Vector camera = new Vector(0,0), startingCamera = new Vector(0,0);
    Vector spacing = new Vector(0,0);
    Vector begin = new Vector(), end = new Vector();
    Vector startingBegin = new Vector((float) ceilAny(startingCamera.x - WIDTH/2f,incrementor.x),(int) floorAny(HEIGHT/2f + startingCamera.y, incrementor.y));
    PVector displacement = new Vector(0,0);
    Vector scale = new Vector(200,200); // tick marks are 200 by default


    DecimalFormat df = new DecimalFormat("####.##");
    PFont font;
    // ?

    public Grid(Applet window) {
        this.p = window;
        this.color = new Color(ColorType.WHITE);
        String commonPath = "src\\data\\";
        font = p.createFont(commonPath + "cmunbmr.ttf", 150, true);
        p.textFont(font);
    }

    public void setColor(Color color){
        this.color = color;
    }

    private void update(){
        displacement = PVector.sub(camera,startingCamera);
    }

    public void setScale(Vector scale){
        this.scale = scale;
    }

    private void generate(){
        float largeStroke = 5, smallStroke = 2.5f;

        p.noFill();
        p.stroke(color);

        begin.x = (float) ceilAny(displacement.x - WIDTH/2f,incrementor.x);
        end.x = (float) ceilAny(camera.x + WIDTH/2f,incrementor.x);

        float ender = (end.x-begin.x)/incrementor.x;
        for (int i = 0; i < ender; i++){ // draws vert lines
            float x = begin.x + i*incrementor.x;
           // p.println(x-startingBegin.x, 2*incrementor.x);
            if (Math.abs(Math.IEEEremainder(x-startingBegin.x, 2*incrementor.x)) < EPSILON) // v-startingBegin.v is meant for translation, not scaling! TODO: fix
                p.strokeWeight(largeStroke);
            else
                p.strokeWeight(smallStroke);

            p.line(x,camera.y+spacing.y,x,camera.y-spacing.y);
        }

        begin.y = (int) floorAny(HEIGHT/2f + camera.y, incrementor.y);
        end.y = (int) floorAny(-HEIGHT/2f + camera.y, incrementor.y); //This is the top of the p (as it is translated based on cameraPos)

        ender = (begin.y-end.y)/incrementor.y;
        for (int j = 0; j < ender; j++){  // draws horiz lines, processing draws y up to down cuz flipped (so invert the bounds)
            float y = begin.y - j*incrementor.y;
            if (Math.abs(Math.IEEEremainder(y-startingBegin.y, 2*incrementor.y)) < EPSILON)
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

    public void label(){
        // y value rectangle
        p.textSize(50);
        p.textAlign(RIGHT,CENTER);
        p.fill(ColorType.WHITE);
        float ender = (begin.y-end.y)/incrementor.y; // remember y's flipped!
        for (int j = 0; j < ender; j++){
            float y = begin.y - j*incrementor.y;
            if (Math.abs(Math.IEEEremainder(y-startingBegin.y, 2*incrementor.y)) < EPSILON) {
                // -600 is the original begin.y <--- dont trust anything idk
                float txt = textify(y,Y);
                float yCoord;
                yCoord = y - 40;

                p.text(df.format(Math.abs(txt)), displacement.x - 6, yCoord); // account for everything !

            }
        }

        p.textAlign(CENTER,CENTER);
        // increment end because text needs to show up before line (super efficient line)
        ender = (end.x-begin.x)/incrementor.x;
        for (int i = 0; i < ender; i++){
            float x = begin.x + i*incrementor.x;
            float txt = textify(x,X);
            if (txt == 0)
                continue;

            if (Math.abs(Math.IEEEremainder(x-startingBegin.x, 2*incrementor.x)) < EPSILON)
                // -600 is the original begin.x
                p.text(df.format(txt),x,displacement.y+30); // account for everything !
        }
    }

    private float textify(float r, int XorY){
        if (XorY == X)
            return 1/scale.x * r; // begin.x ORIGINAL
        return -1/scale.y * r; // begin.y ORIGINAL
    }


    public boolean draw(){
        p.translate(PVector.mult(camera,-1));
        update();
        generate();
        drawMainAxes();
        label();
        incrementor.easeTo(new Vector(100,100),5);
        //p.println(incrementor);
        return spacing.easeTo(new Vector(WIDTH/2f,HEIGHT/2f),1);
    }

}
