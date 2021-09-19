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
    // Possibly add length attribute for number of things in SVG?
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
        color.setAlpha(0);
    }

    public PShape getShape(){
        return latex;
    }

    public void setColor(Color color){
        this.color = color;
        color.setAlpha(0);
    }

    public boolean draw(Vector pos){
        return this.draw(pos.x,pos.y);
    }

    Color darkGrey = new Color(0,0,0,60);
    public boolean draw(float x, float y) {
        darkGrey.setAlpha(0.6f * color.getAlpha().getValue());
        p.fill(darkGrey);
        p.noStroke();
        p.rect(x-latex.getWidth()/2,y-latex.getHeight()/2 + 5,x+latex.getWidth()/2 + 20,y+latex.getHeight()/2 + 15);
        p.fill(color);
        p.shape(latex, x, y);
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
}
