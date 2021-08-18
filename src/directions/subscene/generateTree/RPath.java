package directions.subscene.generateTree;

import core.Applet;
import processing.core.PApplet;
import processing.core.PShape;
import storage.Vector;
import util.Mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static processing.core.PConstants.GROUP;
import static processing.core.PConstants.LINE;
import static util.map.MapEase.EASE_IN;
import static util.map.MapType.QUADRATIC;

public class RPath extends RTree {
    public RPath(Applet p, RTreeNode root, int... directions){
        super(p,directions.length);
        this.directions = directions;
        nodesPerDepth = new ArrayList<>();
        RTreeNode nRoot = new RTreeNode(root.val);
        nRoot.setPos(root.getPos());
        nRoot.setChildNumber(-1);
        nodesPerDepth.add(Collections.singletonList(nRoot));
        for (int i: directions){ // for bin, switch to something else for ternary+! (0 <= i < numOfChild)
            if (i == -1)
                root = root.getParent();
            else
                root = root.getChildren(i);
            RTreeNode parent = nodesPerDepth.get(nodesPerDepth.size()-1).get(0);
            RTreeNode child = new RTreeNode(parent,root.val);
            child.setPos(root.getPos());
            child.setChildNumber(root.getChildNumber());
            nodesPerDepth.add(Collections.singletonList(child));
        }
        System.out.println(nodesPerDepth);
        this.incrementor = new ArrayList<>(Collections.nCopies(nodesPerDepth.size()*2 - 1, 0L));
    }
    @Override
    public boolean draw(){
        if (depthCount < 2*degree && incrementor.get(depthCount) >= 40)
            depthCount++;
        return draw(depthCount);
    }

    @Override
    public boolean draw(int xDepth){
        if (completedDraw)
            return true;
        skeleton = p.createShape(GROUP);
        skeleton.setName("noLatex");
        p.stroke(color);
        for (int i = 0; i <= xDepth; i++){
            long inc = incrementor.get(i);
            float c = 1;
            if (inc < 40) {
                c = (float) Mapper.map2(inc,0,40,0,1,QUADRATIC,EASE_IN);
                incrementor.set(i,inc+1);
            } else if (i == 2*degree) // depth for any subtree
                completedDraw = true;

            RTreeNode n = nodesPerDepth.get((i+1)/2).get(0); // i % 2 == 1 means line!
            n.setColor(color);
            if (i % 2 == 1) {
                Vector parentPos = n.getParent().getPos();
                PShape lines;
                float angle = PApplet.radians(36);
                float x = parentPos.x + RTreeNode.radius*PApplet.sin(angle)*(2*n.getChildNumber()-1);
                float y = parentPos.y + RTreeNode.radius*PApplet.cos(angle);

                lines = p.createShape(LINE, x, y, c * n.pos.x + (1 - c) * x, c * (n.pos.y - RTreeNode.radius) + (1 - c) * y);
                n.getParent().getNodeShape().addChild(lines);
            }
            else
                n.draw(c, skeleton); // hmmm no need to draw the latex right?
        }
        return false;
    }
}
