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
        this.frames = 40;
    }

    @Override
    public boolean draw(){
        if (depthCount < 2*degree && incrementor.get(depthCount) >= 40) {
            depthCount++;
            completedDraw = false;
        }
        return draw(depthCount);
    }

    @Override
    public boolean draw(int xDepth){
        p.println(completedDraw,oldDepth,xDepth);
        if (completedDraw && oldDepth == xDepth && color.getInterpolationStatus())
            return true;
        skeleton = p.createShape(GROUP);
        skeleton.setName("noLatex");
        return this.drawHelper(xDepth);
    }

    public int getDepthCount(){
        return this.depthCount;
    }
}
