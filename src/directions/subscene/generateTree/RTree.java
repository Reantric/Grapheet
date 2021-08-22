package directions.subscene.generateTree;

import core.Applet;
import processing.core.PApplet;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import util.Mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static processing.core.PConstants.*;
import static util.map.MapEase.EASE_IN;
import static util.map.MapType.QUADRATIC;

public class RTree {
    public RTreeNode root; // for now, its a bin tree
    public int degree;
    Applet p;
    public List<List<RTreeNode>> nodesPerDepth = new ArrayList<>();
    List<Long> incrementor;
    Vector pos = new Vector(0,-400);
    PShape skeleton;
    Color color;
    boolean completedDraw;
    int[] directions;
    int depthCount = 0;
    int oldDepth = 0;
    long frames = 50;

    public RTree(Applet p,int degree){
        this.p = p;
        RTreeNode.p = p;
        this.degree = degree;
    }

    public void init(){
        createNodes(degree);
        this.incrementor = new ArrayList<>(Collections.nCopies(nodesPerDepth.size()*2 - 1, 0L));
        color = new Color(ColorType.WHITE);
    }


    public void setColor(Color color){
        this.color = color;
    }

    private void createNodes(int n){
        List<Integer> buildTree = new ArrayList<>();
        buildTree.add(0);
        for (int i = 0; i < buildTree.size(); i++){
            int b = buildTree.get(i);
            if (b < n){
                buildTree.add(b+1);
                buildTree.add(b+2);
            }
        }

        int L = 1;
        int h = 0;
        root = new RTreeNode(0);
        root.setPos(pos);
        nodesPerDepth.add(Collections.singletonList(root));

        int displacement = 100 * (int) Math.pow(1.66,n-1);
        int spacing = displacement;

        for (int i = 1; i < buildTree.size(); ){
            int j = i;
            int newL = 0;
            int newH = 0;
            List<RTreeNode> currentDepthNodes = new ArrayList<>();
            for (;j <= buildTree.size() && j < i + 2*(L-h); j++){
                int id = buildTree.get(j);
                if (id >= n)
                    newH++;
                newL++;

                RTreeNode parent = nodesPerDepth.get(nodesPerDepth.size()-1).get((j-i)/2);
                RTreeNode child = new RTreeNode(parent,id);

                if (nodesPerDepth.size() == n) // this might need refactoring in the future
                    child.setPos(new Vector(1.7f*(2*(j-i))*spacing-2*displacement,-400+nodesPerDepth.size()*200));
                else
                    child.setPos(new Vector((2*(j-i) + 1)*spacing-2*displacement,-400+nodesPerDepth.size()*200));

                currentDepthNodes.add(child);
            }
            spacing /= 2;
            nodesPerDepth.add(currentDepthNodes);
            i = j;
            L = newL;
            h = newH;
        }
        PApplet.println(nodesPerDepth);
    }

    public boolean draw(){
        if (depthCount < 2*degree && incrementor.get(depthCount) >= frames) {
            depthCount+=2;
            completedDraw = false;
        }
        return draw(depthCount);
    }

    protected float assignInterpVal(int i, int xDepth){
        long inc = incrementor.get(i);
        float c = 0;
        if (i <= xDepth) {
            if (inc < frames) {
                c = (float) Mapper.map2(inc, 0, frames, 0, 1, QUADRATIC, EASE_IN);
                incrementor.set(i, inc + 1);
            }
            else
                c = 1;
        }
        else if (inc > 0) {
            c = (float) Mapper.map2(inc, 0, frames, 0, 1, QUADRATIC, EASE_IN);
            completedDraw = false;
            incrementor.set(i, inc - 1);
        }
        return c;
    }

    public boolean draw(int xDepth){
        if (completedDraw && oldDepth == xDepth && color.getInterpolationStatus()) //TODO: make new method that encapsulates all this!
            return true;
        skeleton = p.createShape(GROUP);
        return this.drawHelper(xDepth);
    }

    public boolean drawHelper(int xDepth){
        if (incrementor.get(xDepth) >= frames) {
            completedDraw = true;
            oldDepth = xDepth;
        }

        p.stroke(color);
        for (int i = 0; i <= 2*degree; i++){
            float c = assignInterpVal(i,xDepth);
            for (RTreeNode n: nodesPerDepth.get((i+1)/2)) {
                n.setColor(color);
                if (i % 2 == 1) {
                    Vector parentPos = n.getParent().getPos();
                    PShape lines;
                    float angle = PApplet.radians(36);
                    float x = parentPos.x + RTreeNode.radius * PApplet.sin(angle) * (2 * n.getChildNumber() - 1);
                    float y = parentPos.y + RTreeNode.radius * PApplet.cos(angle);

                    lines = p.createShape(LINE, x, y, c * n.pos.x + (1 - c) * x, c * (n.pos.y - RTreeNode.radius) + (1 - c) * y);
                    n.getParent().getNodeShape().addChild(lines);
                } else
                    n.draw(c, skeleton);
            }
        }
        return completedDraw;
    }

    public int getDegree() {
        return this.degree;
    }

    public PShape getShape() {
        return this.skeleton;
    }

    public RTreeNode getRoot(){
        return this.root;
    }

    public RPath createPath(int... directions){
        return new RPath(this.p,this.root,directions);
    }
}
