package directions.subscene.generateTree;

import core.Applet;
import processing.core.PApplet;
import processing.core.PShape;
import storage.Vector;
import text.ImmutableLaTeX;

import java.util.ArrayList;
import java.util.List;

import static processing.core.PConstants.*;

public class RTreeNode {
    RTreeNode parent;
    List<RTreeNode> children = new ArrayList<>();
    Vector pos;
    PShape nodeShape;
    ImmutableLaTeX latex;
    float radius = 100;
    int val;
    static Applet p;
    float angleToMakeCirc = PApplet.PI/2;

    public RTreeNode(RTreeNode parent, int val){
        this.parent = parent;
        parent.addChildren(this);
        this.val = val;
        nodeShape = p.createShape(GROUP);
        latex = new ImmutableLaTeX(p,Integer.toString(val));
    }

    public RTreeNode(int val){
        this.val = val;
        nodeShape = p.createShape(GROUP);
        latex = new ImmutableLaTeX(p,Integer.toString(val));
    }

    public void addChildren(RTreeNode child){
        this.children.add(child);
    }

    public void setPos(Vector pos){
        this.pos = pos;
        latex.setPos(pos.x-24,pos.y-10-20);
    }

    public void draw(float c, PShape groupShape){
        p.noFill();
        p.strokeWeight(5);
        p.stroke(255,0,255);
        p.textSize(60);
        angleToMakeCirc = PApplet.map(c,0,1,p.PI/2,p.TAU+p.PI/2);
        PShape text = latex.getShape();
        if (c < 0.99){
            nodeShape = p.createShape(ARC,pos.x,pos.y,100,100,-angleToMakeCirc,-p.PI/2); // constructor shit
            nodeShape.setStroke(p.color(0,0,255,255));
            parent.nodeShape.addChild(nodeShape);
            text.setFill(p.color(255,0,255,PApplet.map(angleToMakeCirc,p.PI/2,p.TAU+p.PI/2,0,255)));
        }
        else {
            angleToMakeCirc = p.TAU+p.PI/2;
            nodeShape = p.createShape(ELLIPSE,pos.x,pos.y,radius,radius);
            nodeShape.setStroke(p.color(0,0,255,255));
            parent.nodeShape.addChild(nodeShape);
            text.setFill(p.color(255,0,255));
        }
        nodeShape.addChild(text);
    }

    public RTreeNode getParent(){
        return this.parent;
    }

    public List<RTreeNode> getChildren(){
        return this.children;
    }

    public String toString(){
        return String.valueOf(val);
    }

    public Vector getPos() {
        return this.pos;
    }
}
