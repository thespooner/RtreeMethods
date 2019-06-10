package Rtree;

import java.io.Serializable;

public abstract class Entry implements Serializable {
    MBR mbr;
    Entry(MBR mbr){
        this.mbr = mbr;
    }
}
