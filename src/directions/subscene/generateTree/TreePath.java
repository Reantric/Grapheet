package directions.subscene.generateTree;

import storage.Color;

import java.util.Arrays;
import java.util.List;

public class TreePath {
    List<RTreeNode> path;
    Color color;
    public TreePath(RTreeNode... nodes){
        path = Arrays.asList(nodes);
    }

    public void setColor(Color color){
        this.color = color;
    }

    public List<RTreeNode> getPath(){
        return this.path;
    }



}
