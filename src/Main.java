import core.Applet;
import geom.Grid;
import processing.core.PApplet;
import processing.core.PFont;
import processing.event.MouseEvent;

import static geom.Grid.*;

public class Main extends Applet {
    public static PFont myFont, italics;
    public Grid plane;

    public void setup(){
        String commonPath = "src\\data\\";
        myFont = createFont(commonPath + "cmunbmr.ttf", 150, true);
        italics = createFont(commonPath + "cmunbmo.ttf", 150, true);
        plane = new Grid(this);
    }


    public void settings() {
        size(WIDTH, HEIGHT);
        smooth(8);
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

    public void draw(){
        plane.draw();
        //saveFrame("test/line-######.png");
    }

    public void mouseWheel(MouseEvent event) {
        e += -0.07f * event.getCount();
    }
}
