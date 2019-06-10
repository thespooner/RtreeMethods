package Rtree;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Created by CarlOtto on 22.02.2019.
 */
public class MBR implements Serializable {
    public int dimensions;
    public float low[], high[];

    public MBR(int dimensions) {
        super();
        this.dimensions = dimensions;
        this.low = new float[dimensions];
        this.high = new float[dimensions];
    }

    public MBR(float[] low, float[] high) {
        this(low.length);
        System.arraycopy(low, 0, this.low, 0, dimensions);
        System.arraycopy(high, 0, this.high, 0, dimensions);
    }
    // If a copy is needed.
    public MBR(MBR mbr){
        this(mbr.dimensions);
        System.arraycopy(mbr.low,0, this.low, 0, dimensions);
        System.arraycopy(mbr.high, 0, this.high, 0, dimensions);
    }

    public MBR(float[] points) {
        this(points, points);
    }

    public double getArea() {
        double area = 1.0;
        for (int i = 0; i < dimensions; i++) {
            area *= (high[i] - low[i]);
        }
        return area;
    }

    public double getMargin() {
        double margin = 0.0F;
        for (int i = 0; i < dimensions; i++) {
            margin += (high[i] - low[i]);
        }
        return margin;
    }

    public static MBR union(MBR... regions) {
        MBR union = new MBR(regions[0].dimensions);
        for (int d = 0; d < union.dimensions; d++) {
            // for each dimension, find the lowest and highest values
            float low = regions[0].low[d];
            float high = regions[0].high[d];

            for (int r = 1; r < regions.length; r++) {
                if (regions[r].low[d] < low) {
                    low = regions[r].low[d];
                }
                if (regions[r].high[d] > high) {
                    high = regions[r].high[d];
                }
            }
            union.low[d] = low;
            union.high[d] = high;
        }
        return union;
    }

    // faster to compute inline how much an mbr will be expanded, than first area with new mbr included and then without.
    // as exemplified bellow
    // union(new mbr, node.mbr).getArea() - node.mbr.getArea()

    public double enlargment(MBR newMBR){
        double area = 1; // The Area without the new MBR as it is in the current state
        double areaWithEnlargment = 1;
        for(int i = 0; i < dimensions ; i++){

            double line1 = high[i] - low[i];
            double line2 = line1;

            if(newMBR.high[i] - high[i] > 0 ){ // need to enlarge in this axis in the upper limit to include entry
                line2 += (newMBR.high[i] - high[i]);
            }
            if(newMBR.low[i] - low[i] < 0 ){ // need to enlarge in this axis in the lower limit to include entry
                line2 +=  low[i] - newMBR.low[i]; // beware of sign
            }
            area *= line1;
            areaWithEnlargment *= line2;
        }
        return areaWithEnlargment - area;
    }


    public static MBR intersection(MBR... r) {
        MBR intersect = new MBR(r[0].low, r[0].high);
        for (int j = 1; j < r.length; j++) {
            for (int i = 0; i < intersect.dimensions; i++) {
                intersect.low[i] = Math.max(r[j].low[i], intersect.low[i]);
                intersect.high[i] = Math.min(r[j].high[i], intersect.high[i]);
            }
        }
        return intersect;
    }

    public static boolean intersects(MBR r1, MBR r2) {
        for (int i = 0; i < r1.dimensions; i++) {
            if ((r2.low[i] > r1.high[i]) || (r1.low[i] > r2.high[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("mbr[dimensions=");
        sb.append(dimensions);
        for (int i = 0; i < low.length; i++) {
            sb.append(", D");
            sb.append(i+1);
            sb.append("=");
            sb.append(low[i]);
            sb.append("-");
            sb.append(high[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public boolean contains(MBR r) {
        for (int i = 0; i < dimensions; i++) {
            if ((r.low[i] < low[i]) || (r.high[i] > high[i])) {
                return false;
            }
        }
        return true;
    }
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MBR)) {
            return false;
        }
        MBR r = (MBR)o;
        return Arrays.equals(low, r.low) && Arrays.equals(high, r.high);
    }
}
