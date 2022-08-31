package geom;


import core.Applet;
import processing.core.PFont;
import processing.core.PVector;
import storage.Color;
import storage.ColorType;
import storage.TruthVector;
import storage.Vector;
import util.Mapper;
import util.map.MapEase;
import util.map.MapType;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static processing.core.PConstants.*;
import static util.Useful.ceilAny;
import static util.Useful.floorAny;

public class DataGrid {
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    public int cycle = 0; // 1 -> 2 -> 5 -> 1
    Applet p;
    public boolean fade = false;
    public double fadeIncrementor = 0;
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

    Vector camera = new Vector(0,-15),spacing = new Vector(200,200);
    Vector startingCamera = new Vector(camera);
    Vector incrementor = new Vector(250,200);
    Vector startingBegin = new Vector((float) ceilAny(startingCamera.x - WIDTH/2f,250),(int) floorAny(HEIGHT/2f + startingCamera.y, 200));
    // def incrementor: 200,200 // 203.8 fails
    Vector begin = new Vector(),end = new Vector();
    Vector scale = new Vector(800,800); // possible dynamic generation of scale?
    PVector displacement = new Vector(0,0);
    TruthVector startMoving = new TruthVector();
    Color color;
    PFont font;
    DecimalFormat df = new DecimalFormat("#.");
    public static float e = 1;
    public PFont italics;
    public PFont getNameFont() {
        return nameFont;
    }

    public PFont nameFont;
    public DataGrid(Applet p){
        this.p = p;
        String commonPath = "src/data/";
        font = p.createFont(commonPath + "cmunbmr.ttf", 150, true);
        color = new Color(0,0,65);
        italics = p.createFont(commonPath + "cmunbmo.ttf", 150, true);
        nameFont = p.createFont("Lato Bold",150,true);
        // Empty for now because nothing much really happens
    }

    private void init(){
        p.colorMode(HSB);
        p.background(0);
        p.shapeMode(CENTER);
        p.rectMode(CORNERS);
        p.textFont(font);
        p.textSize(60);
        p.strokeCap(ROUND);
        p.ellipseMode(CENTER);
    }

    int startFade = 130; // endis200?
    int endFade = cycle != 2 ? 100 : 80;
    private void update(){
        if (camera.x > 80)
            startMoving.x = true;

        startMoving.y = camera.y < 170-200; // suppose camera goes up and down?

        displacement = PVector.sub(camera,startingCamera);
        fade = incrementor.y < startFade;
        endFade = cycle != 2 ? 100 : 80;
       // fadeIncrementor += fade ? 0.1 : 0;

        if (cycle != 2 && incrementor.y <= 100 + EPSILON){ // this is /2, only happen if we are at 1 or 5
            incrementor.y = 200;
            scale.y /= 2;
            fadeIncrementor = 0;
            fade = false;
            cycle <<= 1;
            cycle %= 9;
        }
        if (cycle == 2 && incrementor.y <= 80){
            incrementor.y = 200;
            scale.y /= 2.5;
            fadeIncrementor = 0;
            fade = false;
            cycle = 5;
        }
    }

    private void generate(){
        float largeStroke = 5, smallStroke = 2.5f;

        p.noFill();

        begin.x = (float) ceilAny(displacement.x - WIDTH/2f,250);
        end.x = (float) ceilAny(camera.x + WIDTH/2f + 250,250);

        float ender = (end.x-begin.x)/incrementor.x;
        for (int i = 0; i < ender; i++){ // draws vert lines
            float x = begin.x + i*incrementor.x;
            if (x-displacement.x < 333-WIDTH/2f && startMoving.x)
                color.setAlpha((float) Mapper.map2(x-displacement.x,90-WIDTH/2f, 333-WIDTH/2f,0,100, MapType.QUADRATIC, MapEase.EASE_IN_OUT));
            else
                color.setAlpha(100);

            p.stroke(color);

            if (Math.abs(Math.IEEEremainder(x-startingBegin.x, 2*incrementor.x)) < EPSILON)
                p.strokeWeight(largeStroke);
            else
                p.strokeWeight(smallStroke);

            if (startMoving.y){
                p.line(x,camera.y+spacing.y,x,camera.y-spacing.y);
            } else {
                p.line(x,-spacing.y,x,Math.min(400,spacing.y));
            }
        }

        begin.y = (float) floorAny(HEIGHT/2f - 15, 200); // this isn't good but it works for now
        end.y = (float) floorAny(-HEIGHT/2f + camera.y, incrementor.y); //This is the top of the p (as it is translated based on cameraPos)

        ender = (begin.y-end.y)/incrementor.y;
        for (int j = 0; j <= ender; j++){  // draws horiz lines, processing draws y up to down cuz flipped (so invert the bounds)
            float y = begin.y - j*incrementor.y;
            if (Math.abs(Math.IEEEremainder(y-startingBegin.y, 2*incrementor.y)) < EPSILON) {
                p.strokeWeight(largeStroke);
                color.setAlpha(100);
            }
            else {
                p.strokeWeight(smallStroke);
                if (fade)
                    color.setAlpha((float) Mapper.map2(incrementor.y-fadeIncrementor,startFade,endFade,100,0,MapType.QUADRATIC,MapEase.EASE_IN_OUT));
            }


            p.stroke(color);
            if (startMoving.x) {
                p.line(camera.x - spacing.x,y,camera.x + spacing.x,y);
            }
            else {
                p.line(Math.max(-spacing.x, -800),y,spacing.x,y);
            }

        }
    }

    public void drawMainAxes(){
        p.stroke(new Color(ColorType.WHITE));
        p.strokeWeight(6);

        if (!startMoving.x && !startMoving.y){
            p.line(startingCamera.x + Math.max(-spacing.x, -800), begin.y,startingCamera.x + spacing.x, begin.y);
            p.line(begin.x,-spacing.y,begin.x,Math.min(400,spacing.y));
        } else if (startMoving.x && !startMoving.y) {
            p.line(displacement.x - spacing.x, begin.y,displacement.x + spacing.x, begin.y);
        } else if (!startMoving.x){
            p.line(begin.x,camera.y + spacing.y,begin.x,camera.y - spacing.y);
        }
    }

    Color whiteText = new Color(ColorType.WHITE);
    public void label(){
        // y value rectangle
        p.fill(ColorType.BLACK);
        p.noStroke();
        p.rect(displacement.x-WIDTH/2f,displacement.y-HEIGHT/2f - 100,displacement.x + 153-WIDTH/2f,displacement.y + HEIGHT/2f); // buffer


        p.textAlign(RIGHT,CENTER);

        p.stroke(ColorType.RED);
        float ender = (begin.y-end.y)/incrementor.y; // remember y's flipped!
        for (int j = 0; j <= ender; j++){
            float y = begin.y - j*incrementor.y;
             if (Math.abs(Math.IEEEremainder(y-startingBegin.y, 2*incrementor.y)) < EPSILON) {
                // -600 is the original begin.y <--- dont trust anything idk
                double num = textify(y);
                float yCoord;
                if (y == begin.y) // hmm?
                    yCoord = y - 20;
                else
                    yCoord = y - 2;

                if (j % 4 == 2 && fade)
                    whiteText.setAlpha((float) Mapper.map2(incrementor.y-fadeIncrementor,startFade,endFade,100,0,MapType.QUADRATIC,MapEase.EASE_IN_OUT));
                else
                    whiteText.setAlpha(100);
                p.fill(whiteText);

                var txt = showText(num);
                int sze = 60;
                switch (txt.length()){
                    case 1-> sze = 70;
                    case 2-> sze = 62;
                    case 3-> sze = 56;
                    case 4-> sze = 48;
                }
                p.textSize(sze);
                p.text(txt, displacement.x + 130 - WIDTH / 2f, yCoord); // account for everything !

            }
             p.textSize(60);
        }

        // x value rectangle
        p.fill(ColorType.BLACK);
        p.noStroke();
        p.rect(displacement.x -WIDTH/2f,displacement.y + HEIGHT/2f+100,displacement.x + WIDTH/2f,displacement.y + HEIGHT/2f-130); // buffer
        p.fill(ColorType.WHITE);
        p.textAlign(CENTER,CENTER);
        // Fade out first line(s) if it gets too close
        p.stroke(ColorType.RED);
        // increment end because text needs to show up before line (super efficient line)
        ender = (end.x-begin.x)/incrementor.x;

        for (int i = 0; i < ender; i++){
            float x = begin.x + i*incrementor.x;
           // double txt = showTextEpoch(x);
            //if (txt == 0) // this might still be aesthetically pleasing
              //  continue;

            if (x-displacement.x < 333-WIDTH/2f && startMoving.x) // cant hurt now can it?
                if (x-displacement.x < 90-WIDTH/2f)
                    p.fill(0,0,0,0);
                else
                    p.fill(0,0,255,(float) Mapper.map2(x-displacement.x,90-WIDTH/2f, 333-WIDTH/2f,0,255, MapType.QUADRATIC, MapEase.EASE_IN_OUT));
            else
                p.fill(ColorType.WHITE);

            if (Math.abs(Math.IEEEremainder(x-startingBegin.x, 2*incrementor.x)) < EPSILON)
                // -600 is the original begin.x
                // 161-WIDTH/2f + scale.x * (float) (xValues[i] - xOffset) = x, solve for xValues[i]
                p.text(showTextEpoch((x - 161 + WIDTH/2f)/scale.x + xOffset),x,displacement.y + HEIGHT/2f - 95); // account for everything !
        }
    }

    public void setColor(Color color){
        this.color = color;
    }

    public Color getColor(){
        return this.color;
    }

    public double textify(float r){ // textifY
            //return 1/scale.x * (r-startingBegin.x); // begin.x ORIGINAL
        return -1/scale.y * 200/incrementor.y * (r-startingBegin.y); // begin.y ORIGINAL
    }

    Calendar cal = Calendar.getInstance();
    String showTextEpoch(double a){
        cal.setTime(new Timestamp((long) a));
        LocalDate date2 = Instant.ofEpochMilli((long) a * 1000).atZone(ZoneId.systemDefault()).toLocalDate();
        String strMonth2 = date2.getMonth().toString().substring(0,3);
        return "" + strMonth2.charAt(0) + strMonth2.substring(1,3).toLowerCase() + " " + date2.getDayOfMonth() + ", " + date2.getYear();
    }

    String showText(double a){
        if (Math.round(a) >= 1000000){
            String s = df.format(a/1000000);
            return !s.contains(".") ? s : s.replaceAll("0*$", "").replaceAll("\\.$", "") + "M";
        }

        if (Math.round(a) >= 1000){
            String s = df.format(a/1000);
            return !s.contains(".") ? s : s.replaceAll("0*$", "").replaceAll("\\.$", "") + "K";
        }
        String s = Long.toString(Math.round(a));
        return !s.contains(".") ? s : s.replaceAll("0*$", "").replaceAll("\\.$", "");
    }


    public boolean draw(){
        init();
        p.scale(e);
        update();
        p.translate(PVector.mult(camera,-1));
        generate();
        boolean b = spacing.interpolate(new Vector(2*WIDTH/3f,2*HEIGHT/3f),1); // better to err on the side of caution
       // camera.easeTo(new Vector(1200,-300),QUADRATIC,15);
       // PApplet.println(begin,end);
   //     incrementor.add(new Vector(0.1f));
       // processing.image(p,-WIDTH/2f,-HEIGHT/2f);
        drawMainAxes();
        return b;
    }

    double xOffset = 0;
    public void setXOffset(double offset) {
        xOffset = offset;
    }

    public double getXOffset() {
        return xOffset;
    }
}
