package util.map;

public interface Easable<T> {
    // time in seconds
    boolean easeTo(T other, MapType type, double time);
}
