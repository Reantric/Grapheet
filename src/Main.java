import com.hamoid.VideoExport;
import core.Applet;
import directions.Directions;
import directions.engine.Director;
import directions.modern.ModernScenes;
import processing.core.PApplet;
import processing.core.PFont;
import processing.event.MouseEvent;

import java.lang.reflect.InvocationTargetException;

import static geom.DataGrid.*;

public class Main extends Applet {
    public static PFont myFont, italics;
    public VideoExport videoExport;
    private boolean recordVideo;
    private boolean useLegacyDirections;
    private Director director;
    private boolean useP2DRenderer;
    private boolean useFullscreen;

    public void setup(){
        String commonPath = "src/data/";
        myFont = createFont(commonPath + "cmunbmr.ttf", 150, true);
        italics = createFont(commonPath + "cmunbmo.ttf", 150, true);
        useLegacyDirections = Boolean.getBoolean("legacyDirections");
        if (useLegacyDirections) {
            try {
                Directions.init(this);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException noSuchMethodException) {
                noSuchMethodException.printStackTrace();
            }
        } else {
            director = ModernScenes.create(this);
        }

        recordVideo = Boolean.getBoolean("recordVideo");
        if (recordVideo) {
            videoExport = new VideoExport(this, "taylors.mp4");
            videoExport.setQuality(85, 0);
            videoExport.setFrameRate(60);
            videoExport.startMovie();
        }

        frameRate(60);
        //surface.setVisible(false);
    }


    public void settings() {
        useP2DRenderer = !"JAVA2D".equalsIgnoreCase(System.getProperty("renderer", "P2D"));
        useFullscreen = Boolean.parseBoolean(System.getProperty("fullscreen", "true"));

        if (useP2DRenderer) {
            if (useFullscreen) {
                fullScreen(P2D);
            } else {
                size(WIDTH, HEIGHT, P2D);
            }
        } else {
            if (useFullscreen) {
                fullScreen();
            } else {
                size(WIDTH, HEIGHT);
            }
        }
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
        scale(e);
        init();
        boolean finished = useLegacyDirections ? Directions.directions() : director.drawFrame();
        if (finished){ // if all scenes finishes, terminate!
            System.out.println("Goodbye");
            if (recordVideo) videoExport.endMovie();
            exit();
        } else if (recordVideo) videoExport.saveFrame();
        //saveFrame("test/line-" + frameCount + ".png");
      //  endRecord();
    }

    private void init(){
        colorMode(HSB, 360, 100, 100,100);
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
            if (recordVideo) videoExport.endMovie();
            exit();
        }
    }
}
