package directions.subscene.generateTree;

import core.Applet;

import java.util.ArrayList;
import java.util.Collections;

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
        this.incrementor = new ArrayList<>(Collections.nCopies(nodesPerDepth.size(), 0L));
    }
}
