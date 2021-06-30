package directions.subscene.generateTree;

import core.Applet;
import processing.core.PApplet;
import processing.core.PShape;
import storage.Vector;
import text.ImmutableLaTeX;

import static processing.core.PConstants.*;

public class AilunNode {
    static final int radius = 100;
    int val;
    float angleToMakeCirc = PApplet.PI/2;
    Vector pos;
    static Applet p;
    PShape nodeShape;
    ImmutableLaTeX latex;

    public AilunNode(int val){
        this.val = val;
        nodeShape = p.createShape(GROUP);
        latex = new ImmutableLaTeX(p,Integer.toString(val));
    }

    public void setPos(Vector pos){
        this.pos = pos;
        latex.setPos(pos.x-24,pos.y-10-20); // once resetMatrix issue is worked out, move back down!
    }

    public PShape getNodeShape(){
        return this.nodeShape;
    }

    public void draw(float c, PShape groupShape){
        p.noFill();
        p.strokeWeight(5);
        p.stroke(255,0,255);
        p.textSize(60);
        angleToMakeCirc = PApplet.map(c,0,1,p.PI/2,p.TAU+p.PI/2);
        PShape text = latex.getShape();
        if (c < 0.99){
            //p.arc(pos.x,pos.y,100,100,-angleToMakeCirc,-p.PI/2);
            nodeShape = p.createShape(ARC,pos.x,pos.y,100,100,-angleToMakeCirc,-p.PI/2); // constructor shit
            nodeShape.setStroke(p.color(0,0,255,255));
            groupShape.addChild(nodeShape);
            //p.fill(255,0,255,PApplet.map(angleToMakeCirc,p.PI/2,p.TAU+p.PI/2,0,255));
            text.setFill(p.color(255,0,255,PApplet.map(angleToMakeCirc,p.PI/2,p.TAU+p.PI/2,0,255)));
        }
        else {
            angleToMakeCirc = p.TAU+p.PI/2;
            //p.circle(pos.x,pos.y,radius);
            nodeShape = p.createShape(ELLIPSE,pos.x,pos.y,radius,radius);
            nodeShape.setStroke(p.color(0,0,255,255));
            groupShape.addChild(nodeShape);
            //p.fill(255,0,255);
            text.setFill(p.color(255,0,255));
        }
        groupShape.addChild(text);
        //latex.draw(pos.x,pos.y-10);
       // p.text(val,pos.x,pos.y-10);
    }

    public String toString(){
        return String.valueOf(val);
    }
}
