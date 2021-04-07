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
    Graphics canvas;
    Applet processing;
    Vector camera = new Vector(120,-40),spacing = new Vector(200);
    TruthVector startMoving = new TruthVector();
    float scaleFactor = 1;
    public Grid(Applet p){
        this.processing = p;
        // Empty for now because nothing much really happens
        canvas = processing.createGraphics(WIDTH,HEIGHT);
    }

    private void init(){
        canvas.beginDraw();
        canvas.colorMode(HSB);
        canvas.translate(WIDTH/2f,HEIGHT/2f);
        canvas.background(0);
        canvas.shapeMode(CENTER);
        canvas.rectMode(CENTER);
        canvas.textAlign(CENTER);
    }

    private void generate(){
        float largeStroke = 5, smallStroke = 2;
        PVector incrementor = new PVector(200,200);

        canvas.stroke(0,0,95);
        for (float x = (int) floorAny(camera.x -WIDTH/2f + 2*incrementor.x,incrementor.x); x < ceilAny(camera.x + WIDTH/2f,incrementor.x); x += incrementor.x){ // draws vert lines
            if (Math.abs((x + WIDTH/2f) % (2*incrementor.x)) < EPSILON)
                canvas.strokeWeight(smallStroke);
            else
                canvas.strokeWeight(largeStroke);
            canvas.line(x,-spacing.x,x,Math.min(400,spacing.x)); // do more experimentation
        }

        float y = (int) floorAny(-HEIGHT/2f - camera.y + incrementor.y,incrementor.y); //This is the top of the canvas (as it is translated based on cameraPos)
        for (;y < (int) ceilAny(HEIGHT/2f-camera.y,incrementor.y); y += incrementor.y){  // draws horiz lines, processing draws y up to down cuz flipped.
            if (Math.abs((y + HEIGHT/2f) % (2*incrementor.y)) < EPSILON)
                canvas.strokeWeight(smallStroke);
            else
                canvas.strokeWeight(largeStroke);
            canvas.line(Math.max(-spacing.x,-760),y,spacing.x,y); // math.max here sugga
        }

        canvas.stroke(0,0,255);
        canvas.strokeWeight(6);
        canvas.line(-WIDTH/2f + incrementor.x,-spacing.x,-WIDTH/2f + incrementor.x,Math.min(400,spacing.x));
        canvas.line(Math.max(-spacing.x,-760),y-incrementor.y,spacing.x,y-incrementor.y);
    }

    public void draw(){
        init();
        camera.easeTo(new Vector(1200,-30),8);
        spacing.easeTo(new Vector(5000),3);
        canvas.translate(PVector.mult(camera,-1));
        generate();
        canvas.endDraw();
        processing.image(canvas,-WIDTH/2f,-HEIGHT/2f);
    }
}
