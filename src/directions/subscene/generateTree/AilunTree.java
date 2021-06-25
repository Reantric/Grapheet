package directions.subscene.generateTree;
import core.Applet;
import processing.core.PApplet;
import storage.Vector;
import util.Mapper;

import java.util.*;

import static processing.core.PConstants.CENTER;
import static util.map.MapEase.EASE_IN;
import static util.map.MapType.QUADRATIC;

public class AilunTree {
    List<Integer> nodes = new ArrayList<>();
    List<List<Node>> nodesPerDepth = new ArrayList<>();
    Applet p;
    public AilunTree(Applet p, int n){
        Node.p = p;
        createNodes(n);
        this.p = p;
        p.textAlign(CENTER,CENTER);
    }

    public void draw(int depth){
        int displacement = 100 * (int) Math.pow(1.7,nodesPerDepth.size()-1); // switch nodesPerDepth.size() with depth if scal
        int spacing = displacement;
        for (int i = 0; i <= depth; i++){ // i represents current depth!
            List<Node> child = nodesPerDepth.get(i);
            for (int j = 0; j < child.size(); j++){
                Node n = child.get(j);
                if (i == nodesPerDepth.size()-1)
                    n.setPos(new Vector(1.7f*(2*j)*spacing-displacement,-400+i*200));
                else
                    n.setPos(new Vector((2*j + 1)*spacing-displacement,-400+i*200));

                if (i > 0){
                    List<Node> parent = nodesPerDepth.get(i-1);
                    Vector parentPos = parent.get(j/2).pos;

                    float c = p.map(n.angleToMakeCirc,p.PI/2,p.TAU+p.PI/2,0,1);

                    if (j % 2 == 0)
                        p.line(parentPos.x-Node.radius/2,parentPos.y,c*n.pos.x + (1-c)*(parentPos.x-Node.radius/2),c*(n.pos.y-Node.radius/2) + (1-c)*parentPos.y);
                    else
                        p.line(parentPos.x+Node.radius/2,parentPos.y,c*n.pos.x + (1-c)*(parentPos.x+Node.radius/2),c*(n.pos.y-Node.radius/2) + (1-c)*parentPos.y);
                }
                n.draw();
            }
            spacing /= 2;

        }

    }


    void createNodes(int n){
        nodes.add(0);
        for (int i = 0; i < nodes.size(); i++){
            int b = nodes.get(i);
            if (b < n){
                nodes.add(b+1);
                nodes.add(b+2);
            }
        }

        int L = 1;
        int h = 0;
        nodesPerDepth.add(Collections.singletonList(new Node(0)));
        for (int i = 1; i < nodes.size(); ){
            int j = i;
            int newL = 0;
            int newH = 0;
            List<Node> currentDepthNodes = new ArrayList<>();
            for (;j <= nodes.size() && j < i + 2*(L-h); j++){
                int id = nodes.get(j);
                if (id >= n)
                    newH++;
                newL++;
                currentDepthNodes.add(new Node(id));
            }
            nodesPerDepth.add(currentDepthNodes);
            i = j;
            L = newL;
            h = newH;
        }
        p.println(nodesPerDepth);
    }
}

class Node {
    static final int radius = 100;
    int val;
    float angleToMakeCirc = PApplet.PI/2;
    long incrementor = 0;
    Vector pos;
    static Applet p;
    public Node(int val){
        this.val = val;
    }

    public void setPos(Vector pos){ this.pos = pos; }

    public void draw(){
        p.noFill();
        p.strokeWeight(5);
        p.stroke(255,0,255);
        p.textSize(60);
        if (incrementor < 50){
            angleToMakeCirc = (float) Mapper.map2(incrementor++,0,50,p.PI/2,p.TAU+p.PI/2,QUADRATIC,EASE_IN);
            p.arc(pos.x,pos.y,100,-100,p.PI/2,angleToMakeCirc);
            p.fill(255,0,255,p.map(angleToMakeCirc,p.PI/2,p.TAU+p.PI/2,0,255));
            p.text(val,pos.x,pos.y-10);
        }
        else {
            angleToMakeCirc = p.TAU+p.PI/2;
            p.circle(pos.x,pos.y,radius);
            p.fill(255,0,255);
            p.text(val,pos.x,pos.y-10);
        }
    }

    public String toString(){
        return String.valueOf(val);
    }
}
