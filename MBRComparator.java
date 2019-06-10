package Rtree;

import java.util.Comparator;

public class MBRComparator implements Comparator<Entry> {

    private int dimension;
    private boolean low;

    public MBRComparator(int dimension, boolean low) {
        this.dimension = dimension;
        this.low = low;
    }

    public int compare(Entry e1, Entry e2) {
        if (low) {
            return Float.compare(e1.mbr.low[dimension], e2.mbr.low[dimension]);
        } else {
            return Float.compare(e1.mbr.high[dimension], e2.mbr.high[dimension]);
        }
    }
}
