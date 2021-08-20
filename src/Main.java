import com.hamoid.VideoExport;
import core.Applet;
import directions.Directions;
import geom.Grid;
import processing.core.PApplet;
import processing.core.PFont;
import processing.event.MouseEvent;

import java.lang.reflect.InvocationTargetException;

import static geom.Grid.*;

public class Main extends Applet {
    public static PFont myFont, italics;
    public VideoExport videoExport;

    public void setup(){
        String commonPath = "src\\data\\";
        myFont = createFont(commonPath + "cmunbmr.ttf", 150, true);
        italics = createFont(commonPath + "cmunbmo.ttf", 150, true);
        try {
            Directions.init(this);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException noSuchMethodException) {
            noSuchMethodException.printStackTrace();
        }

        videoExport = new VideoExport(this,"test.mp4");
        videoExport.setFfmpegPath("library\\ffmpeg.exe");
        videoExport.setQuality(85,0);
        videoExport.setFrameRate(60);
        frameRate(60);
        //videoExport.startMovie();
    }


    public void settings() {
        //size(WIDTH, HEIGHT, P2D);
        fullScreen(P2D);
        smooth(8);
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
        //beginRecord(SVG, "frame-####.svg");
        init();
        if (Directions.directions()){ // if all scenes finishes, terminate!
            System.out.println("Goodbye");
            videoExport.endMovie();
            exit();
        } else
            videoExport.saveFrame();
       // saveFrame("test/line-######.png");
      //  endRecord();
    }

    private void init(){
        colorMode(HSB);
        translate(WIDTH/2f,HEIGHT/2f);
        background(0);
        shapeMode(CENTER);
        rectMode(CORNERS);
        strokeCap(ROUND);
        ellipseMode(CENTER);
    }


    public void mouseWheel(MouseEvent event) {
        e += -0.07f * event.getCount();
    }

    public void keyPressed() {
        if (key == 'q') {
            videoExport.endMovie();
            exit();
        }
    }
}
