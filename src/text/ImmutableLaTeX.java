package text;

import core.Applet;
import core.ShapeWrapper;
import processing.core.PApplet;
import processing.core.PShape;
import processing.data.XML;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import util.tex.SVGConverter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static processing.core.PConstants.CORNER;
import static processing.core.PConstants.GROUP;

public class ImmutableLaTeX {
    PShape latex;
    Color color;
    SVGConverter converter;
    Applet p;
    Vector dim;
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
        dim = new Vector(latex.getWidth(),latex.getHeight());
        XML xml = p.loadXML(".\\temp\\" + id + ".svg").getChild("g");

        tex = new ArrayList<>();
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
                tex.add(new ShapeWrapper(child.getChild(0),color,new Vector(
                        Float.parseFloat(xmlChild.getChild(0).getString("x")),Float.parseFloat(xmlChild.getChild(0).getString("y"))
                )));
                tex.add(new ShapeWrapper(child.getChild(1),color,pos));
            } else {
                tex.add(new ShapeWrapper(child.getChild(0),color,pos));
            }
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

        p.fill(darkGrey);
        p.noStroke();
        //p.rect(x-zoo.getWidth()/2,y-zoo.getHeight()/2 + 5,x+zoo.getWidth()/2 + 20,y+zoo.getHeight()/2 + 15);

        float width = PApplet.map(tex.get(tex.size()-1).getPos().x,tex.get(0).getPos().x,tex.get(tex.size()-1).getPos().x,-dim.x/2,dim.x/2);
        p.rect(x - dim.x/2, y-dim.y/2 + 5,x + width + 20, y+dim.y/2 + 15);
        p.shapeMode(CORNER); // necessary evil
        for (ShapeWrapper s: tex) {
             Vector pos = s.getPos();
             p.fill(s.getColor());
             p.shape(s.getShape(),x+pos.x - dim.x/2 ,y+pos.y - dim.y/2);
        }
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

    //TODO: add getWidth(List<ShapeWrapper>, options) method
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