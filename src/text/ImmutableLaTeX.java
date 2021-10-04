package text;

import core.Applet;
import core.ShapeWrapper;
import processing.core.PApplet;
import processing.core.PShape;
import processing.data.XML;
import storage.BoundingBox;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import util.tex.SVGConverter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static processing.core.PConstants.*;

public class ImmutableLaTeX {
    PShape latex;
    Color color;
    SVGConverter converter;
    Applet p;
    Vector dim;
    public List<ShapeWrapper> tex;
    BoundingBox bbox = new BoundingBox();
    int endingInd;
    Vector scale = new Vector(1,1);
    static long counterID = 0;
    long id;
    public static Map<String,Long> encodedID = new HashMap<>();
    public static Path path;

    public ImmutableLaTeX(Applet p, String str, Color color) {
        this.p = p;
        String eID = encode(str);
        this.color = color;
        boolean debug = false;

        if (!encodedID.containsKey(eID)) {
            this.id = ++counterID;
            encodedID.put(eID,id);
            try {
                Files.writeString(path, eID + " " + id + "\n", StandardOpenOption.APPEND);
            } catch (IOException e){
                e.printStackTrace();
            }
            converter = new SVGConverter(color); // TODO: Modify Later
            converter.write(str, ".\\temp\\" + id + ".svg", 60);
            debug = true;
        } else
            this.id = encodedID.get(eID);

        this.latex = p.loadShape(".\\temp\\" + id + ".svg").getChild("eq");
        this.latex.disableStyle();
        dim = new Vector(latex.getWidth(),latex.getHeight());
        setupBoundingBoxCrap(debug);
        color.setAlpha(0);
    }

    public ImmutableLaTeX(Applet p, String str){ // allow bunching them up like in that manim example
        this(p,str,new Color(ColorType.CYAN));
    }

    private void setupBoundingBoxCrap(boolean debug){
        XML xml = p.loadXML(".\\temp\\" + id + ".svg").getChild("g");

        tex = new ArrayList<>();
        Vector prevPosLow = new Vector(MAX_FLOAT,MAX_FLOAT), prevPosHigh = new Vector();

        for (int i = 0; i < latex.getChildCount(); i++){ // eq has max layer 2 children, layer 1 is filler (usually), layer 2 is the path (and maybe rect)
            PShape child = latex.getChild(i);
            boolean hasMultipleChildren = child.getChildCount() > 1;
            String translate;
            XML xmlChild = xml.getChild(i);
            if (hasMultipleChildren)
                translate = xmlChild.getChild(1).getString("transform");
            else
                translate = xmlChild.getString("transform");

            String findTranslateValues = translate.substring(translate.indexOf("translate"));
            String[] values = findTranslateValues.substring(findTranslateValues.indexOf("(")+1,findTranslateValues.indexOf(")")).split(",");
            Vector pos = new Vector(Float.parseFloat(values[0]),Float.parseFloat(values[1]));


            if (hasMultipleChildren){
                float x = Float.parseFloat(xmlChild.getChild(0).getString("x"));
                float y = Float.parseFloat(xmlChild.getChild(0).getString("y"));
                tex.add(new ShapeWrapper(child.getChild(0),color,new Vector(
                        x,y),scale
                ));

                if (prevPosHigh.x < Math.max(pos.x,x))
                    prevPosHigh.x = Math.max(pos.x,x);

                if (prevPosLow.x < Math.min(pos.x,x))
                    prevPosLow.x = Math.min(pos.x,x);

                bbox.widths.add(new Vector(prevPosLow.x,prevPosHigh.x));

                if (prevPosHigh.y < Math.max(pos.y,y))
                    prevPosHigh.y = Math.max(pos.y,y);

                if (prevPosLow.y > Math.min(pos.y,y))
                    prevPosLow.y = Math.min(pos.y,y);

                bbox.heights.add(new Vector(prevPosLow.y,prevPosHigh.y));
                tex.add(new ShapeWrapper(child.getChild(1),color,pos,scale));

            } else {
                tex.add(new ShapeWrapper(child.getChild(0),color,pos,scale));

                if (prevPosHigh.x < pos.x)
                    prevPosHigh.x = pos.x;

                if (prevPosLow.x > pos.x)
                    prevPosLow.x = pos.x;

                if (prevPosHigh.y < pos.y)
                    prevPosHigh.y = pos.y;

                if (prevPosLow.y > pos.y)
                    prevPosLow.y = pos.y;
            }
            bbox.widths.add(new Vector(prevPosLow.x,prevPosHigh.x));
            bbox.heights.add(new Vector(prevPosLow.y,prevPosHigh.y)); // this is left/bottom justified, that might be a problem...
        }
        endingInd = tex.size()-1;
       // if (debug)
        //    p.println("h1gh",bbox.heights);
    }

    public void setBoundingBoxIndex(int index){this.endingInd = index;}

    public void setBoundingBoxIndex(){
        setBoundingBoxIndex(tex.size()-1);
    }

    public PShape getShape(){
        return latex;
    }

    public Vector getScale(){
        return this.scale;
    }

    public void setColor(Color color){
        this.setColor(color,false);
    }

    public void setColor(Color color, boolean init){ // maybe this isnt the best idea
        this.color = color;
        for (ShapeWrapper s: tex){
            s.setColor(color);
        }
        if (init)
            color.setAlpha(0);
    }

    public boolean draw(Vector pos){
        return this.draw(pos.x,pos.y);
    }

    Color darkGrey = new Color(0,0,0,60);
    private void drawBoundingBox(float x, float y){
        darkGrey.setAlpha(0.6f * color.getAlpha().getValue());

        p.fill(ColorType.CYAN);
        p.noStroke();


        float width = PApplet.map(tex.get(endingInd).getPos().x,bbox.getBoundingWidth(0).y,bbox.getBoundingWidth(tex.size()-1).y,0,latex.getWidth());
        float heightDown  = PApplet.map(tex.get(endingInd).getPos().y,0,bbox.getBoundingHeight(tex.size()-1).y,0,latex.getHeight()); // idk why i gotta do this but TODO fix
        float heightUp  = PApplet.map(tex.get(endingInd).getPos().x,0,bbox.getBoundingHeight(tex.size()-1).y,0,latex.getHeight());
        //p.println(latex.getWidth(),latex.getHeight(), width,height);
        p.rect(x,y+heightUp,x+width*scale.x + 25,y+heightDown*scale.y + 25); // leftmost corner, ??
        //p.rect(x - dim.x/2, y+height + 5,x + width + 20, y+dim.y/2 + 15); // justified left bottom, fix later if need be (god this is pain)
    }

    public boolean draw(float x, float y) {
        drawBoundingBox(x, y);

        p.shapeMode(CORNER); // necessary evil
        for (ShapeWrapper s: tex) {
            Vector scale = s.getScale();
            p.fill(s.getColor());
            p.shape(s.getShape(),x,y,s.getShape().getWidth()*scale.x,s.getShape().getHeight()*scale.y);
        }
        return color.getAlpha().interpolate(100);
    }

    public void setPos(float x, float y){
        latex.translate(x,y);
    }

    public static String encode(String s){ // TODO: possibly use Huffman coding? LOL
        String b = s.replaceAll("\\s","");
        // System.out.println(b);
        StringBuilder id = new StringBuilder();
        for (char c: b.toCharArray()){
            id.append(String.format("%03d", (int) c));
        }
        return id.toString();
    }

    public static String decode(String n){
        StringBuilder og = new StringBuilder();
        for (int i = 0; i < n.length(); i+=3){
            og.append((char) Integer.parseInt(n.substring(i,i+3)));
        }
        return og.toString();
    }

    public List<ShapeWrapper> getSubtex(int i, int j){
        return this.tex.subList(i,j);
    }

    public List<ShapeWrapper> getSubtex(int i){
        return this.getSubtex(i,tex.size());
    }

    public Color getColor() {
        return this.color;
    }

    public void setScale(Vector scale) {
        this.scale = scale;
        // i dont like having to do this...
        for (ShapeWrapper s: tex){
            s.setScale(scale);
        }
    }
}