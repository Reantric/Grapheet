package geom;


import core.Applet;
import core.Graphics;
import core.Vector;
import processing.core.PApplet;
import processing.core.PVector;

import static processing.core.PConstants.*;

public class Grid {
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    Graphics canvas;
    Applet processing;
    Vector cameraPos = new Vector(600,0);
    Vector spacing = new Vector(200);
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
        for (float x = -WIDTH/2f + 2*incrementor.x; x < WIDTH/2f; x += incrementor.x){
            if (Math.abs((x + WIDTH/2f) % (2*incrementor.x)) < EPSILON)
                canvas.strokeWeight(smallStroke);
            else
                canvas.strokeWeight(largeStroke);
            canvas.line(x,-spacing.x,x,Math.min(460,spacing.x)); // do more experimentation
        }

        float y = -HEIGHT/2f + incrementor.y;
        for (;y < HEIGHT/2f; y += incrementor.y){
            if (Math.abs((y + HEIGHT/2f) % (2*incrementor.y)) < EPSILON)
                canvas.strokeWeight(smallStroke);
            else
                canvas.strokeWeight(largeStroke);
            canvas.line(Math.max(-spacing.x,-760),y,spacing.x,y); // math.max here sugga
        }

        canvas.stroke(0,0,255);
        canvas.strokeWeight(6);
        canvas.line(-WIDTH/2f + incrementor.x,-spacing.x,-WIDTH/2f + incrementor.x,Math.min(460,spacing.x));
        canvas.line(Math.max(-spacing.x,-760),y-incrementor.y,spacing.x,y-incrementor.y);
    }

    public void draw(){
        init();
        cameraPos.easeTo(new Vector(0,-900),7);
        spacing.easeTo(new Vector(1260),3);
       // PApplet.println(cameraPos);
        canvas.translate(cameraPos);
        generate();
        canvas.endDraw();
        processing.image(canvas,-WIDTH/2f,-HEIGHT/2f);
    }
}
