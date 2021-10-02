package storage;

import java.util.ArrayList;
import java.util.List;

public class BoundingBox { // vec.x is low, vec.y is high
    public List<Vector> widths = new ArrayList<>();
    public List<Vector> heights = new ArrayList<>();

    public Vector getBoundingWidth(int t){ // t is index!
        return widths.get(t);
    }
    public Vector getBoundingHeight(int t){ // t is index!
        return heights.get(t);
    }
}
