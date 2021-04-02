import core.Applet;
import processing.core.*;
import storage.Color;
import storage.ColorType;

import static processing.core.PConstants.P2D;

public class Main extends Applet {
    public static final int WIDTH = 1000;
    public static final int HEIGHT = 1000;
    PGraphics p;

    public void settings() {
        size(WIDTH, HEIGHT, P2D);
        smooth(8);
        p = new PGraphics();
        //fullScreen();
    }

    public static void main(String[] passedArgs) {
        String[] appletArgs = new String[]{Main.class.getCanonicalName()};
        if (passedArgs != null) {
            PApplet.main(concat(appletArgs, passedArgs));
        } else {
            PApplet.main(appletArgs);
        }
    }

    public void init(){
        colorMode(HSB);
        p.beginDraw();
        p.fill(255);
        p.circle(0,0,100);
        p.endDraw();
        translate(width/2f,height/2f);
        background(0);
        shapeMode(CENTER);
        rectMode(CENTER);
        //textAlign(CENTER);
    }

    public void draw(){
        init();
        fill(new Color(ColorType.CYAN));
      //  board.draw();
        image(p,0,0);
    }
}
