package directions.subscene.generateTree;

import core.Applet;
import processing.core.PApplet;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;
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
    static float radius = 50;
    int val;
    static Applet p;
    float angleToMakeCirc = PApplet.PI/2;
    int childNumber;
    Color color = new Color(ColorType.WHITE);

    public RTreeNode(RTreeNode parent, int val){
        this.parent = parent;
        parent.addChildren(this);
        childNumber = parent.getChildren().size()-1;
        this.val = val;
        latex = new ImmutableLaTeX(p,Integer.toString(val));
    }

    public RTreeNode(RTreeNode o, boolean root){
        this.childNumber = o.getChildNumber();
        this.pos = o.getPos();
        this.val = o.val;
        this.latex = o.latex;
        if (!root)
            this.parent = o.getParent();
    }

    public void setColor(Color color){
        this.color = color;
    }

    public RTreeNode(RTreeNode o){
        this(o,false);
    }

    public RTreeNode(int val){
        this.val = val;
        childNumber = -1;
        latex = new ImmutableLaTeX(p,Integer.toString(val));
    }

    public PShape getNodeShape(){
        return this.nodeShape;
    }

    public void addChildren(RTreeNode child){
        this.children.add(child);
    }

    public void setPos(Vector pos){
        this.pos = pos;
        latex.setPos(pos.x-24,pos.y-10-20);
    }

    public void draw(float c, PShape groupShape){ // only place where addChild should occur
        p.noFill();
        p.strokeWeight(5);
        p.textSize(60);
        angleToMakeCirc = PApplet.map(c,0,1,p.PI/2,p.TAU+p.PI/2);
        resetNodeShape();
        PShape text = latex.getShape();
        PShape circShape;
        if (c < 0.99){
            circShape = p.createShape(ARC,pos.x,pos.y,radius*2,radius*2,-angleToMakeCirc,-p.PI/2); // constructor shit
            circShape.setStroke(p.color(color));
            text.setFill(p.color(255,0,255,PApplet.map(angleToMakeCirc,p.PI/2,p.TAU+p.PI/2,0,255)));
        }
        else {
            angleToMakeCirc = p.TAU+p.PI/2;
            circShape = p.createShape(ELLIPSE,pos.x,pos.y,radius*2,radius*2);
            circShape.setStroke(p.color(color));
            text.setFill(p.color(255,0,255));
        }
        nodeShape.addChild(circShape);
        nodeShape.addChild(text);
        //nodeShape.disableStyle();
        groupShape.addChild(nodeShape);
    }

    public void resetNodeShape(){
        this.nodeShape = p.createShape(GROUP);
    }

    public RTreeNode getParent(){
        return this.parent;
    }

    public List<RTreeNode> getChildren(){
        return this.children;
    }

    public RTreeNode getChildren(int ind){
        return this.children.get(ind);
    }

    public String toString(){
        return String.valueOf(val);
    }

    public Vector getPos() {
        return this.pos;
    }

    public int getChildNumber(){return this.childNumber;}

    public void setChildNumber(int childNumber) {
        this.childNumber = childNumber;
    }
}
