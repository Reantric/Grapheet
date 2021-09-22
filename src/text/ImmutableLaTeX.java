package text;

import core.Applet;
import core.ShapeWrapper;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import util.tex.SVGConverter;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static processing.core.PConstants.*;

public class ImmutableLaTeX {
    PShape latex;
    Color color;
    SVGConverter converter;
    Applet p;
    public List<ShapeWrapper> tex;
    public ImmutableLaTeX(Applet p, String str) {
        this.p = p;
        String id = encode(str);
        this.color = new Color(ColorType.CYAN);
        if (!new File(".\\temp\\" + id + ".svg").exists()) {
            converter = new SVGConverter(color); // TODO: Modify Later
            converter.write(str, ".\\temp\\" + id + ".svg", 60);
        }
        this.latex = p.loadShape(".\\temp\\" + id + ".svg").getChild("eq");
        this.latex.disableStyle();
        tex = new ArrayList<>();
        for (int i = 0; i < latex.getChildCount(); i++){ // Since eq only has layer 1 children, no need to recurse!
            tex.add(new ShapeWrapper(latex.getChild(i),color));
        }
        color.setAlpha(0);
    }

    public PShape getShape(){
        return latex;
    }

    public void setColor(Color color){
        this.color = color;
        for (ShapeWrapper s: tex){
            s.setColor(color);
        }
        color.setAlpha(0);
    }

    public boolean draw(Vector pos){
        return this.draw(pos.x,pos.y);
    }

    Color darkGrey = new Color(0,0,0,60);
    public boolean draw(float x, float y) {
        darkGrey.setAlpha(0.6f * color.getAlpha().getValue());

        //p.shapeMode(CORNER);
        PShape zoo = p.createShape(GROUP);
        zoo.disableStyle();
        for (ShapeWrapper s: tex.subList(0,p.frameCount/50)){
           // p.fill(s.getColor());
           // p.shape(s.getShape(),x,y);
            zoo.addChild(s.getShape());
        }
        p.fill(darkGrey);
        p.noStroke();
        p.rect(x-zoo.getWidth()/2,y-zoo.getHeight()/2 + 5,x+zoo.getWidth()/2 + 20,y+zoo.getHeight()/2 + 15);
        p.fill(color);
        p.shape(zoo,x,y);
        return color.getAlpha().easeTo(100);
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

    public PShape splice(int i, int j){
        PShape total = p.createShape(GROUP);
        for (ShapeWrapper s: tex.subList(i,j)) {
            p.fill(s.getColor());
            //p.shape(s.getShape(),0,0);
            total.addChild(s.getShape());
        }
        p.shape(total);
        return total;
    }
}
