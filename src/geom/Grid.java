package geom;


import core.Applet;
import core.Graphics;
import processing.core.PApplet;
import storage.TruthVector;
import storage.Vector;
import processing.core.PVector;

import static processing.core.PConstants.*;
import static util.Useful.ceilAny;
import static util.Useful.floorAny;

public class Grid {
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    Applet p;
    Vector camera = new Vector(32400,-40),spacing = new Vector(200);
    Vector startingCamera = new Vector(camera);
    TruthVector startMoving = new TruthVector();
    float scaleFactor = 1;
    public static float e = 1;

    public Grid(Applet p){
        this.p = p;
        // Empty for now because nothing much really happens
    }

    private void init(){
        p.colorMode(HSB);
        p.translate(WIDTH/2f,HEIGHT/2f);
        p.background(0);
        p.shapeMode(CENTER);
        p.rectMode(CENTER);
        p.textAlign(CENTER);
    }

    private void updateMovementVec(){
        if (camera.x != startingCamera.x && camera.x > 168)
            startMoving.x = true;

        if (camera.y != startingCamera.y)
            startMoving.y = true;
    }

    private void generate(){
        float largeStroke = 5, smallStroke = 2;
        PVector incrementor = new PVector(200,200);

        p.stroke(0,0,95);

        float x = (int) floorAny(camera.x - WIDTH/2f + 2*incrementor.x,incrementor.x);
        for (;x < ceilAny(camera.x + WIDTH/2f,incrementor.x); x += incrementor.x){ // draws vert lines
            if (Math.abs((x + WIDTH/2f) % (2*incrementor.x)) < EPSILON)
                p.strokeWeight(smallStroke);
            else
                p.strokeWeight(largeStroke);
            p.line(x,-spacing.x,x,Math.min(400,spacing.x)); // do more experimentation
        } // issues still to be debugged

        float y = (int) floorAny(-HEIGHT/2f - camera.y + incrementor.y,incrementor.y); //This is the top of the p (as it is translated based on cameraPos)
        for (;y < (int) floorAny(HEIGHT/2f-camera.y,incrementor.y); y += incrementor.y){  // draws horiz lines, processing draws y up to down cuz flipped.
            if (Math.abs((y + HEIGHT/2f) % (2*incrementor.y)) < EPSILON)
                p.strokeWeight(smallStroke);
            else
                p.strokeWeight(largeStroke);

            if (startMoving.x)
                p.line(camera.x - spacing.x,y,camera.x + spacing.x,y);
            else
                p.line(Math.max(-spacing.x,-800),y,spacing.x,y); // math.max here sugga
        }

        p.stroke(0,0,255);
        p.strokeWeight(6);

        y += incrementor.y;
        if (startMoving.x)
            p.line(camera.x - spacing.x,y-incrementor.y,camera.x + spacing.x,y-incrementor.y); // x axis
        else {
            p.line(startingCamera.x + Math.max(-spacing.x, -800), y - incrementor.y, startingCamera.x + spacing.x, y - incrementor.y); // x axis
            p.line((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),-spacing.x,(int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),Math.min(400,spacing.x)); // y axis
        }
    }

    public void draw(){
        init();
        p.scale(e);
        updateMovementVec();
   //     camera.easeTo(new Vector(31000,-30),10);
        spacing.easeTo(new Vector(3*WIDTH/4f),1); // better to err on the side of caution
        PApplet.println(PVector.mult(camera,-1));
        p.translate(PVector.mult(camera,-1));
        generate();
       // processing.image(p,-WIDTH/2f,-HEIGHT/2f);
    }
}
