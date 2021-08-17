package directions.subscene.generateTree;

import core.Applet;
import processing.core.PConstants;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TreePath {
    List<RTreeNode> path;
    Color color;
    Applet p;
    int[] directions;
    public TreePath(Applet p, RTreeNode... nodes){
        this.p = p;
        path = new ArrayList<>();
        path.add(nodes[0]);
        directions = new int[nodes.length-1];
        for (int i = 1; i < nodes.length; i++){
            directions[i-1] = nodes[i].childNumber;
            path.add(nodes[i]);
        }
    }

    public TreePath(Applet p, RTreeNode root, int... directions){
        this.p = p;
        this.directions = directions;
        path = new ArrayList<>();
        path.add(root);
        for (int i: directions){ // for bin, switch to something else for ternary+! (0 <= i < numOfChild)
            if (i == -1)
                root = root.getParent();
            else
                root = root.getChildren(i);
            path.add(root);
        }
    }

    public void setColor(Color color){
        this.color = color;
    }

    public List<RTreeNode> getPath(){
        return this.path;
    }

    public boolean draw(){
        p.stroke(color);
        PShape johnson = p.createShape(PConstants.GROUP);
        for (int i = 0; i < directions.length; i++){ // full depth!
            PShape bruh = path.get(i).getNodeShape();
            johnson.addChild(bruh.getChild(0));
            johnson.addChild(bruh.getChild(2 + directions[i]));
           // p.shape(bruh.getChild(0));
           // p.shape(bruh.getChild(2 + directions[i]));
        }
        p.shape(johnson);
       // p.shape(path.get(path.size()-1).getNodeShape().getChild(0));
        return true;
    }
}
