package directions.engine;

import storage.Subcolor;
import storage.Vector;

public final class Values {
    private Values() {
    }

    public static FloatValue of(Subcolor value) {
        return new FloatValue() {
            @Override
            public float get() {
                return value.getValue();
            }

            @Override
            public void set(float newValue) {
                value.setValue(newValue);
            }
        };
    }

    public static VectorValue of(Vector value) {
        return new VectorValue() {
            @Override
            public float x() {
                return value.x;
            }

            @Override
            public float y() {
                return value.y;
            }

            @Override
            public void set(float x, float y) {
                value.x = x;
                value.y = y;
            }
        };
    }
}
