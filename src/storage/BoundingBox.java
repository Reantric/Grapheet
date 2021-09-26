package storage;

import java.util.ArrayList;
import java.util.List;

public class BoundingBox {
    public List<Float> widths = new ArrayList<>();
    public List<Float> heights = new ArrayList<>();

    public float getBoundingWidth(int t){ // t is index!
        return widths.get(t);
    }
    public float getBoundingHeight(int t){ // t is index!
        return heights.get(t);
    }
}
