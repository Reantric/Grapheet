package geom;


import core.Applet;
import processing.core.PApplet;
import processing.core.PShape;
import processing.core.PVector;
import storage.TruthVector;
import storage.Vector;

import static processing.core.PConstants.*;
import static util.Useful.ceilAny;
import static util.Useful.floorAny;

public class Grid {
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    Applet p;
    Vector camera = new Vector(0,-40),spacing = new Vector(200,200);
    Vector startingCamera = new Vector(camera);
    Vector incrementor = new Vector(200,200);
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
        //p.shapeMode(CENTER);
        p.rectMode(CENTER);
        p.textAlign(CENTER);
    }

    private void updateMovementVec(){
        if (camera.x > incrementor.x)
            startMoving.x = true;

        if (camera.y < 70-incrementor.y)
            startMoving.y = true;

    }

    private void generate(){
        float largeStroke = 5, smallStroke = 2.5f;

        p.stroke(0,0,95);
        p.noFill();

        p.beginShape(LINES);
        float x;
        if (startMoving.x)
            x = -incrementor.x + (int) floorAny(camera.x - WIDTH/2f + incrementor.x,incrementor.x);
        else
            x = (int) floorAny(startingCamera.x - WIDTH/2f + 2*incrementor.x,incrementor.x);

        for (;x < ceilAny(camera.x + WIDTH/2f,incrementor.x); x += incrementor.x){ // draws vert lines
            if (Math.abs(x % (2*incrementor.x)) < EPSILON)
                p.strokeWeight(smallStroke);
            else
                p.strokeWeight(largeStroke);

            if (startMoving.y){
                p.vertex(x,camera.y+spacing.y);
                p.vertex(x,camera.y-spacing.y);
            } else {
                p.vertex(x,-spacing.y);
                p.vertex(x,Math.min(400,spacing.y));
            }

        }

        float y = (int) floorAny(-HEIGHT/2f + camera.y,incrementor.y); //This is the top of the p (as it is translated based on cameraPos)
        for (;y < (startMoving.y ? ((int) ceilAny(HEIGHT/2f + camera.y,incrementor.y)): ((int) floorAny(HEIGHT/2f + camera.y,incrementor.y))); y += incrementor.y){  // draws horiz lines, processing draws y up to down cuz flipped.
            // abusing ternary operator
            if (Math.abs(y % (2*incrementor.y)) < EPSILON)
                p.strokeWeight(smallStroke);
            else
                p.strokeWeight(largeStroke);

            if (startMoving.x) {
                p.vertex(camera.x - spacing.x,y);
                p.vertex(camera.x + spacing.x,y);
            }
            else {
                p.vertex(Math.max(-spacing.x, -800),y); // math.max here sugga
                p.vertex(spacing.x,y);
            }
        }
        p.stroke(0,0,255);
        p.strokeWeight(6);
        y += incrementor.y;
        PApplet.println(startMoving);
        if (!startMoving.x && !startMoving.y){
            p.vertex(startingCamera.x + Math.max(-spacing.x, -800), y - incrementor.y); // x axis
            p.vertex(startingCamera.x + spacing.x, y - incrementor.y);
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),-spacing.y); // y axis
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),Math.min(400,spacing.y));
        } else if (startMoving.x && !startMoving.y) {
            p.vertex(camera.x - spacing.x, y - incrementor.y); // x axis
            p.vertex(camera.x + spacing.x, y - incrementor.y);
        } else {
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),camera.y + spacing.y); // y axis
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),camera.y - spacing.y);
        }

     /*   if (startMoving.x) { // no need to display y axis if its moving!
            p.vertex(camera.x - spacing.x, y - incrementor.y); // x axis
            p.vertex(camera.x + spacing.x, y - incrementor.y);
        }
        else {
            p.vertex(startingCamera.x + Math.max(-spacing.x, -800), y - incrementor.y); // x axis
            p.vertex(startingCamera.x + spacing.x, y - incrementor.y);
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),-spacing.y); // y axis
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),Math.min(400,spacing.y));
        } */
        p.endShape();
    }

    public void draw(){
        init();
        p.scale(e);
        updateMovementVec();
        camera.easeTo(new Vector(4000,-3000),20);
        spacing.easeTo(new Vector(2*WIDTH/3f,2*HEIGHT/3f),1); // better to err on the side of caution
        p.translate(PVector.mult(camera,-1));
        generate();
       // processing.image(p,-WIDTH/2f,-HEIGHT/2f);
    }
}
