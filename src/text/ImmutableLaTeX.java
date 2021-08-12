package text;

import core.Applet;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import util.tex.SVGConverter;

import java.io.File;

import static processing.core.PConstants.CENTER;
import static processing.core.PConstants.CORNER;

public class ImmutableLaTeX {
    PShape latex;
    Color color;
    SVGConverter converter;
    Applet p;
    public ImmutableLaTeX(Applet p, String str) {
        this.p = p;
        String id = encode(str);
        if (!new File(".\\temp\\" + id).exists()) {
            this.color = new Color(ColorType.RED);
            converter = new SVGConverter(color); // TODO: Modify Later
            converter.write(str, ".\\temp\\" + id + ".svg", 60);
        }
        this.latex = p.loadShape(".\\temp\\" + id + ".svg").getChild("eq");
        this.latex.disableStyle();
    }

    public PShape getShape(){
        return latex;
    }

    public void draw(Vector pos){
        this.draw(pos.x,pos.y);
    }

    public void draw(float x, float y) {
        p.fill(color);
        p.noStroke();
        p.shape(latex, x, y);
    }

    public void setPos(float x, float y){
        latex.translate(x,y);
    }

    public static String encode(String s){
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
}
