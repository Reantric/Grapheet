package directions.subscene.generateTree;

import core.Applet;
import storage.Vector;
import text.ImmutableTex;

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

    public void removeNode(int depth){
        p.println(incrementor);
        RTreeNode removal = this.nodesPerDepth.get(depth).get(0);
        removal.getParent().children.clear();

        if (removal.hasChildren()) // If this is not a leaf, add nodeToBeDeleted's children to parent!
            removal.getParent().addChildren(removal.getChildren(0));
        this.nodesPerDepth.remove(depth);
        this.incrementor.remove(depth);
        this.degree--;
        this.oldDepth-=2;
        this.depthCount-=2;
    }

    public void addNode(RTreeNode node){
        RTreeNode parent = this.nodesPerDepth.get(nodesPerDepth.size()-1).get(0);
        this.nodesPerDepth.add(Collections.singletonList(node));
        parent.addChildren(node); // if the node already has a parent, what happens? TODO: maybe add some feature that allows chaining of nodes
        this.degree++;
        System.out.println(incrementor);
        this.incrementor.add(0L);
        this.incrementor.add(0L);
        node.latex = new ImmutableTex(p,"th"); // me amo ape
        node.setPos(new Vector(parent.pos.x,parent.pos.y+200));
      //  completedDraw = false;
    }

    public RTreeNode getNode(int depth){
        return this.nodesPerDepth.get(depth).get(0);
    }

    public int getDepthCount(){
        return this.depthCount;
    }
}
