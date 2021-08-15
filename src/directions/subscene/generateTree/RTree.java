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
    int degree;
    Applet p;
    List<List<RTreeNode>> nodesPerDepth = new ArrayList<>();
    List<Long> incrementor;
    Vector pos = new Vector(0,-400);
    PShape skeleton;
    boolean completedDraw;

    public RTree(Applet p,int degree){
        this.p = p;
        RTreeNode.p = p;
        this.degree = degree;
        createNodes(degree);
        this.incrementor = new ArrayList<>(Collections.nCopies(nodesPerDepth.size(), 0L));
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

    public boolean draw(int depth){
        if (completedDraw)
            return true;

        skeleton = p.createShape(GROUP);
        p.stroke(new Color(ColorType.WHITE));
        for (int i = 0; i <= depth; i++){
            List<RTreeNode> children = nodesPerDepth.get(i);
            long inc = incrementor.get(i);
            float c = 1;
            if (inc < 50) {
                c = (float) Mapper.map2(inc,0,50,0,1,QUADRATIC,EASE_IN);
                incrementor.set(i,inc+1);
            } else if (i == degree) // depth for any subtree
                completedDraw = true;

            for (int j = 0; j < children.size(); j++){
                RTreeNode n = children.get(j);
                if (i > 0) {
                    Vector parentPos = n.getParent().getPos();
                    PShape lines;
                    if (j % 2 == 0)
                        lines = p.createShape(LINE, parentPos.x - RTreeNode.radius / 2, parentPos.y, c * n.pos.x + (1 - c) * (parentPos.x - RTreeNode.radius / 2), c * (n.pos.y - AilunNode.radius / 2) + (1 - c) * parentPos.y);
                    else
                        lines = p.createShape(LINE, parentPos.x + RTreeNode.radius / 2, parentPos.y, c * n.pos.x + (1 - c) * (parentPos.x + RTreeNode.radius / 2), c * (n.pos.y - AilunNode.radius / 2) + (1 - c) * parentPos.y);

                    n.getParent().getNodeShape().addChild(lines);
                }
                n.draw(c,skeleton);
            }
        }
        return false;
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
}
