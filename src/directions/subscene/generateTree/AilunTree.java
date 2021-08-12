package directions.subscene.generateTree;
import core.Applet;
import processing.core.PApplet;
import processing.core.PShape;
import storage.Color;
import storage.ColorType;
import storage.Vector;
import util.Mapper;

import java.util.*;

import static processing.core.PConstants.*;
import static util.map.MapEase.EASE_IN;
import static util.map.MapType.QUADRATIC;

public class AilunTree {
    List<Integer> nodes = new ArrayList<>();
    public List<List<AilunNode>> nodesPerDepth = new ArrayList<>();
    Applet p;
    List<Long> incrementor;
    Vector pos = new Vector(0,-400);
    PShape skeleton;
    boolean completedDraw = false;
    int degree;

    public AilunTree(Applet p, int n){
        this.degree = n;
        AilunNode.p = p;
        createNodes(n);
        this.incrementor = new ArrayList<>(Collections.nCopies(nodesPerDepth.size(), 0L));
        this.p = p;
        p.textAlign(CENTER,CENTER);
        skeleton = p.createShape(GROUP);
    }

    public boolean draw(int depth){
        if (completedDraw)
            return true;

        skeleton = p.createShape(GROUP);
        p.stroke(new Color(ColorType.WHITE));
      //  int displacement = 100 * (int) Math.pow(1.7,nodesPerDepth.size()-1); // switch AilunNodesPerDepth.size() with depth if scal
      //  int spacing = displacement;
        for (int i = 0; i <= depth; i++){ // i represents current depth!
            List<AilunNode> child = nodesPerDepth.get(i);
            long inc = incrementor.get(i);
            float c = 1;
            if (inc < 50) {
                c = (float) Mapper.map2(inc,0,50,0,1,QUADRATIC,EASE_IN);
                incrementor.set(i,inc+1);
            } else if (i == degree) // depth for any subtree
                completedDraw = true;

            for (int j = 0; j < child.size(); j++){
                AilunNode n = child.get(j);

              /*  if (i == nodesPerDepth.size()-1)
                    n.setPos(new Vector(1.7f*(2*j)*spacing-displacement,-400+i*200));
                else
                   n.setPos(new Vector((2*j + 1)*spacing-displacement,-400+i*200)); */

                if (i > 0){
                    List<AilunNode> parents = nodesPerDepth.get(i-1);
                    AilunNode parentAilunNode = parents.get(j/2);
                    Vector parentPos = parentAilunNode.pos;

                    PShape lines;
                    if (j % 2 == 0)
                        lines = p.createShape(LINE,parentPos.x - AilunNode.radius / 2, parentPos.y,c * n.pos.x + (1 - c) * (parentPos.x - AilunNode.radius / 2), c * (n.pos.y - AilunNode.radius / 2) + (1 - c) * parentPos.y);

                    else
                        lines = p.createShape(LINE,parentPos.x+AilunNode.radius/2,parentPos.y,c*n.pos.x + (1-c)*(parentPos.x+AilunNode.radius/2),c*(n.pos.y-AilunNode.radius/2) + (1-c)*parentPos.y);

                    skeleton.addChild(lines);
                }
                n.draw(c,skeleton);
            }
        }
        return false;
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
        AilunNode root = new AilunNode(0);
        root.setPos(pos);
        nodesPerDepth.add(Collections.singletonList(root));

        int displacement = 100 * (int) Math.pow(1.66,n-1);
        int spacing = displacement;

        for (int i = 1; i < nodes.size(); ){
            int j = i;
            int newL = 0;
            int newH = 0;
            List<AilunNode> currentDepthNodes = new ArrayList<>();
            for (;j <= nodes.size() && j < i + 2*(L-h); j++){
                int id = nodes.get(j);
                if (id >= n)
                    newH++;
                newL++;

                AilunNode child = new AilunNode(id);
                if (nodesPerDepth.size() == n)
                    child.setPos(new Vector(1.7f*(2*(j-i))*spacing-2*displacement,-400+nodesPerDepth.size()*200));
                else
                    child.setPos(new Vector((2*(j-i) + 1)*spacing-2*displacement,-400+nodesPerDepth.size()*200));

                currentDepthNodes.add(child);

                if (nodesPerDepth.size() > 1){
                //    AilunNode parent = nodesPerDepth.get(nodesPerDepth.size()-1).get((j-i)/2);
                    // maybe have childPos depend on parent, idk?!
                 //   PShape parentShape = parent.getNodeShape();
                 //   parentShape.addChild(child.getNodeShape());
                }
            }
            spacing /= 2;
            nodesPerDepth.add(currentDepthNodes);
            i = j;
            L = newL;
            h = newH;
        }
        PApplet.println(nodesPerDepth);


        /*for (List<AilunNode> lst: nodesPerDepth){
            for (AilunNode node: lst){
                System.out.print(node.pos + " ");
            }
            System.out.println();
        } */
    }

    public PShape getSkeleton() {
        return skeleton;
    }

    public int getDegree() {
        return degree;
    }
}
