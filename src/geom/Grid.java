package geom;


import core.Applet;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PShape;
import processing.core.PVector;
import storage.*;
import java.text.DecimalFormat;

import static processing.core.PConstants.*;
import static util.Useful.ceilAny;
import static util.Useful.floorAny;

public class Grid {
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    Applet p;

    public Applet getProcessingInstance() {
        return p;
    }

    public Vector getCamera() {
        return camera;
    }

    public Vector getSpacing() {
        return spacing;
    }

    public Vector getBegin() {
        return begin;
    }

    public Vector getEnd() {
        return end;
    }

    public Vector getScale() {
        return scale;
    }

    public TruthVector getMoving() {
        return startMoving;
    }

    Vector camera = new Vector(0,0),spacing = new Vector(200,200);
    Vector startingCamera = new Vector(camera);
    Vector incrementor = new Vector(200,200);
    Vector begin,end;
    Vector scale = new Vector(1,1);
    TruthVector startMoving = new TruthVector();
    PFont font;
    DecimalFormat df = new DecimalFormat("#.00");
    public static float e = 1;

    public Grid(Applet p){
        this.p = p;
        String commonPath = "src\\data\\";
        font = p.createFont(commonPath + "cmunbmr.ttf", 150, true);
        // Empty for now because nothing much really happens
    }

    private void init(){
        p.colorMode(HSB);
        p.translate(WIDTH/2f,HEIGHT/2f);
        p.background(0);
        //p.shapeMode(CENTER);
        p.rectMode(CORNERS);
        p.textFont(font);
        p.textSize(60);
    }

    private void updateMovementVec(){
        if (camera.x > 20)
            startMoving.x = true;

        if (camera.y < 170-incrementor.y)
            startMoving.y = true;
    }

    private void generate(){

        float largeStroke = 5, smallStroke = 2.5f;

        p.stroke(0,0,95);
        p.noFill();

        p.beginShape(LINES);

        begin = new Vector(); end = new Vector();


        begin.x = (int) floorAny(startingCamera.x - WIDTH/2f + 2*incrementor.x,incrementor.x);
        end.x = (float) ceilAny(camera.x + WIDTH/2f,incrementor.x);

        for (float x = begin.x; x < end.x; x += incrementor.x){ // draws vert lines
            if (Math.abs(x % (2*incrementor.x)) < EPSILON)
                p.strokeWeight(largeStroke);
            else
                p.strokeWeight(smallStroke);

            if (startMoving.y){
                p.vertex(x,camera.y+spacing.y);
                p.vertex(x,camera.y-spacing.y);
            } else {
                p.vertex(x,-spacing.y);
                p.vertex(x,Math.min(400,spacing.y));
            }

        //    p.text(PApplet.round(x),x,HEIGHT/2f - 95); // account for everything !
        }

        begin.y = (int) floorAny(-HEIGHT/2f + camera.y,incrementor.y); //This is the top of the p (as it is translated based on cameraPos)
        end.y = (int) floorAny(HEIGHT/2f + camera.y,incrementor.y);

        for (float y = begin.y; y < end.y; y += incrementor.y){  // draws horiz lines, processing draws y up to down cuz flipped.

            if (Math.abs(y % (2*incrementor.y)) < EPSILON)
                p.strokeWeight(largeStroke);
            else
                p.strokeWeight(smallStroke);

            if (startMoving.x) {
                p.vertex(camera.x - spacing.x,y);
                p.vertex(camera.x + spacing.x,y);
            }
            else {
                p.vertex(Math.max(-spacing.x, -800),y); // math.max here sugga
                p.vertex(spacing.x,y);
            }

        //    p.text(PApplet.round(y),130-WIDTH/2f,y-2); // account for everything !
        }
        p.stroke(0,0,255);
        p.strokeWeight(6);
      //  PApplet.println(begin);

        if (!startMoving.x && !startMoving.y){
            p.vertex(startingCamera.x + Math.max(-spacing.x, -800), end.y); // x axis
            p.vertex(startingCamera.x + spacing.x, end.y);
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),-spacing.y); // y axis
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),Math.min(400,spacing.y));
        } else if (startMoving.x && !startMoving.y) {
            p.vertex(camera.x - spacing.x, end.y); // x axis
            p.vertex(camera.x + spacing.x, end.y);
        } else if (!startMoving.x){
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),camera.y + spacing.y); // y axis
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),camera.y - spacing.y);
        }
        p.endShape();
        label();
    }

    public void label(){
        PVector displacement = PVector.sub(camera,startingCamera);
        p.textAlign(CENTER,CENTER);
        // x value rectangle
        p.fill(ColorType.BLACK);
        p.noStroke();
        p.rect(displacement.x -WIDTH/2f,displacement.y + HEIGHT/2f+100,displacement.x + WIDTH/2f,displacement.y + HEIGHT/2f-130); // buffer
        p.fill(ColorType.WHITE);

        // y value rectangle
        p.fill(ColorType.BLACK);
        p.noStroke();
        p.rect(-WIDTH/2f,displacement.y-HEIGHT/2f,displacement.x + 153-WIDTH/2f,displacement.y + HEIGHT/2f); // buffer
        p.fill(ColorType.WHITE);

        for (float x = begin.x; x < end.x+incrementor.x; x += incrementor.x){
            if (Math.abs(x % (2*incrementor.x)) < EPSILON)
                // -600 is the original begin.x
                p.text(PApplet.round(scale.x*(x-(-600-incrementor.x))),x,displacement.y + HEIGHT/2f - 95); // account for everything !
        }
        p.textAlign(RIGHT,CENTER);

        for (float y = begin.y; y < end.y; y+= incrementor.y){
            if (Math.abs(y % (2*incrementor.y)) < EPSILON) {
                // -600 is the original begin.y
                p.text(PApplet.round(scale.y * -y - (-600 + incrementor.y)), displacement.x + 130 - WIDTH / 2f, y - 2); // account for everything !
            }
        }
    }

    public void draw(){
        init();
        p.scale(e);
        updateMovementVec();
        camera.easeTo(new Vector(1350,-200),20);
        spacing.easeTo(new Vector(2*WIDTH/3f,2*HEIGHT/3f),1); // better to err on the side of caution
        p.translate(PVector.mult(camera,-1));
        generate();
   //     incrementor.add(new Vector(0.1f));
       // processing.image(p,-WIDTH/2f,-HEIGHT/2f);
    }
}
