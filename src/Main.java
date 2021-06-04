import com.hamoid.VideoExport;
import core.Applet;
import directions.Directions;
import geom.Grid;
import processing.core.PApplet;
import processing.core.PFont;
import processing.event.MouseEvent;

import static geom.Grid.*;

public class Main extends Applet {
    public static PFont myFont, italics;
    public VideoExport videoExport;
    public Directions directions;

    public void setup(){
        String commonPath = "src\\data\\";
        myFont = createFont(commonPath + "cmunbmr.ttf", 150, true);
        italics = createFont(commonPath + "cmunbmo.ttf", 150, true);
        directions = new Directions(this);
        directions.init();
       // plane = new Grid(this);

        videoExport = new VideoExport(this,"test.mp4");
        videoExport.setFfmpegPath("library\\ffmpeg.exe");
        videoExport.setQuality(85,0);
        videoExport.setFrameRate(60);
        frameRate(60);
        //videoExport.startMovie();
    }


    public void settings() {
        // size(WIDTH, HEIGHT, P2D);
        fullScreen();
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
        if (directions.execute()){ // if scene finishes, terminate!
            System.out.println("Goodbye");
            videoExport.endMovie();
            //     exit();
        }

        videoExport.saveFrame();
       // saveFrame("test/line-######.png");
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
