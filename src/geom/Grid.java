package geom;


import core.Applet;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PVector;
import storage.*;
import util.Mapper;
import util.map.MapEase;
import util.map.MapType;

import java.text.DecimalFormat;

import static processing.core.PConstants.*;
import static util.Useful.ceilAny;
import static util.Useful.floorAny;
import static util.map.MapType.LINEAR;

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

    public Vector getIncrementor(){
        return incrementor;
    }

    public PVector getDisplacement() {
        return displacement;
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
    Vector scale = new Vector(100,100);
    PVector displacement = new Vector(0,0);
    TruthVector startMoving = new TruthVector();
    PFont font;
    DecimalFormat df = new DecimalFormat("#.00");
    public static float e = 1;

    Graph graph = new Graph(this,t -> 2*Math.sin(t),new Color(ColorType.GREEN)); // Test

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

    private void update(){
        if (camera.x > 20)
            startMoving.x = true;

        startMoving.y = camera.y < 170-incrementor.y; // suppose camera goes up and down?
        System.out.println(startMoving);
        displacement = PVector.sub(camera,startingCamera);
    }

    private void generate(){

        float largeStroke = 5, smallStroke = 2.5f;

        p.noFill();

        p.beginShape(LINES);

        begin = new Vector(); end = new Vector();


        begin.x = (float) ceilAny(displacement.x - WIDTH/2f,incrementor.x);
        end.x = (float) ceilAny(camera.x + WIDTH/2f,incrementor.x);

        for (float x = begin.x; x < end.x; x += incrementor.x){ // draws vert lines
            if (x-displacement.x < 333-WIDTH/2f && startMoving.x)
                p.stroke(0,0,95,(float) Mapper.map2(x-displacement.x,90-WIDTH/2f, 333-WIDTH/2f,0,255, MapType.QUADRATIC, MapEase.EASE_IN_OUT));
            else
                p.stroke(0,0,95);

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
        end.y = (int) ceilAny(HEIGHT/2f + camera.y,incrementor.y);

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
            p.vertex(displacement.x - spacing.x, end.y); // x axis
            p.vertex(displacement.x + spacing.x, end.y);
        } else if (!startMoving.x){
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),camera.y + spacing.y); // y axis
            p.vertex((int) floorAny(startingCamera.x - WIDTH/2f + incrementor.x,incrementor.x),camera.y - spacing.y);
        }
        p.endShape();

        graph();

        label();
    }

    public void label(){
        // y value rectangle
        p.fill(ColorType.BLACK);
        p.noStroke();
        p.rect(displacement.x-WIDTH/2f,displacement.y-HEIGHT/2f,displacement.x + 153-WIDTH/2f,displacement.y + HEIGHT/2f); // buffer
        p.fill(ColorType.WHITE);

        p.textAlign(RIGHT,CENTER);

        p.stroke(ColorType.RED);
        //p.line(-1000,HEIGHT/2f-150+displacement.y,1000,HEIGHT/2f-150+displacement.y); // Check y line remover IRT
        for (float y = begin.y; y < end.y; y+= incrementor.y){
            if (Math.abs(y % (2*incrementor.y)) < EPSILON && y-displacement.y < HEIGHT/2f - 155) {
                // -600 is the original begin.y
                p.text(PApplet.round(1/scale.y * (-y - (-600 + incrementor.y))), displacement.x + 130 - WIDTH / 2f, y - 2); // account for everything !
            }
        }

        // x value rectangle
        p.fill(ColorType.BLACK);
        p.noStroke();
        p.rect(displacement.x -WIDTH/2f,displacement.y + HEIGHT/2f+100,displacement.x + WIDTH/2f,displacement.y + HEIGHT/2f-130); // buffer
        p.fill(ColorType.WHITE);
        p.textAlign(CENTER,CENTER);
        // Fade out first line(s) if it gets too close
        p.stroke(ColorType.RED);
        //p.line(333-WIDTH/2f + displacement.x,-1000,333-WIDTH/2f + displacement.x,1000);

        for (float x = begin.x; x < end.x+incrementor.x; x += incrementor.x){
            if (x-displacement.x < 333-WIDTH/2f && startMoving.x)
                if (x-displacement.x < 90-WIDTH/2f)
                    p.fill(0,0,0,0);
                else
                    p.fill(0,0,255,(float) Mapper.map2(x-displacement.x,90-WIDTH/2f, 333-WIDTH/2f,0,255, MapType.QUADRATIC, MapEase.EASE_IN_OUT));
            else
                p.fill(ColorType.WHITE);

            if (Math.abs(x % (2*incrementor.x)) < EPSILON)
                // -600 is the original begin.x
                p.text(PApplet.round(1/scale.x*(x-(-600-incrementor.x))),x,displacement.y + HEIGHT/2f - 95); // account for everything !
        }
    }

    public void graph(){
        graph.draw();
    }

    public void draw(){
        init();
        p.scale(e);
        update();
        p.translate(PVector.mult(camera,-1));
        generate();
       // camera.easeTo(new Vector(320,-320),LINEAR,5);
        spacing.easeTo(new Vector(2*WIDTH/3f,2*HEIGHT/3f),1); // better to err on the side of caution
        if (p.frameCount > 300)
            camera.easeTo(new Vector(400,0),LINEAR,2);
        else
            camera.easeTo(new Vector(320,-20),LINEAR,2);
       // PApplet.println(begin,end);
   //     incrementor.add(new Vector(0.1f));
       // processing.image(p,-WIDTH/2f,-HEIGHT/2f);
    }
}
