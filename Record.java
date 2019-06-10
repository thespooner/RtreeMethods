package Rtree;

import java.io.Serializable;

/**
 * Created by CarlOtto on 25.02.2019.
 */

public class Record extends Entry implements Serializable {
    String descript = "";
    Record(MBR mbr){
       super(mbr);
    }

    Record(String s){
        super(new MBR(2));
        s = s.substring(1,s.length() -1);
        String[] attributes = s.split(",");
        this.descript = attributes[0];
        float[] low = new float[2];
        float[] high = new float[2];
        float cordinate1 = Float.parseFloat(attributes[1].replace('\'',' '));
        float cordinate2 = Float.parseFloat(attributes[2].replace('\'',' '));
        low[0] = cordinate1;
        high[0] = cordinate1;
        low[1] = cordinate2;
        high[1] = cordinate2;
        this.mbr = (new MBR(low,high));
    }

    @Override
    public String toString() {
        return descript +":" + this.mbr ;
    }
}
