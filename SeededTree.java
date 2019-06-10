package Rtree;

import java.util.ArrayList;
import java.util.Stack;

public class SeededTree {

    private int dimensions = 2;
    protected SeedNode root;
    private int M;
    private int m;
    ArrayList<Record> outliers;
    ArrayList<SeedNode> leaves;

    SeededTree(RTree rTree, int k){
        this.dimensions = rTree.dimensions;
        this.M = rTree.M;
        this.m = rTree.m;
        this.root = new SeedNode(rTree.root, k);
        SeedNode node = this.root;
        leaves = new ArrayList<SeedNode>();
        copy(k-1,this.root, rTree.root);
        outliers = new ArrayList<Record>();

    }

    public class SeedNode extends Entry{
        protected ArrayList<Entry> entries;
        int height;

       public SeedNode (Entry entry, int h){
           super(new MBR(SeededTree.this.dimensions));
           entries = new ArrayList<Entry>();
           this.height = h;
           this.mbr = new MBR(entry.mbr);
       }

    }

    public void copy(int level, SeedNode node, RTree.TreeNode treeNode){
        if (level > 0){
            for(Entry e : treeNode.entries){
                SeedNode seedNode = new SeedNode(e, level);
                node.entries.add(seedNode);
                copy(level - 1, seedNode, (RTree.TreeNode) e);
            }
        }
        else{
            // want to have quick acess to the leaves
            leaves.add(node);
        }
    }

    public void insert(Record... records){
        for(Record record : records) {
            Stack<SeedNode> stack = new Stack<SeedNode>();
            stack.push(root);
            boolean outlier = true;
            while (!stack.empty()) {
                SeedNode node = stack.pop();
                if (node.height == 1) {
                    node.entries.add(record);
                    outlier = false;
                    break;
                }
                for (Entry e : node.entries) {
                    if (e.mbr.contains(record.mbr)) {
                        stack.push((SeedNode) e);
                    }
                }
            }
            if (outlier) {
                outliers.add(record);
            }
        }

    }

    public ArrayList<RTree.TreeNode> buildRtrees(int threshold){
        ArrayList<RTree.TreeNode> rTrees = new ArrayList<RTree.TreeNode>();
        for(SeedNode seedNode : leaves){
            if(seedNode.entries.size() > threshold){
                RTree rTree = new RTree(this.dimensions, this.M, this.m);
                rTree.STRpacking(seedNode.entries.stream().map(entry -> (Record) entry).toArray(Record[]::new));
                rTrees.add(rTree.root);
            } else {
                seedNode.entries.stream().map(entry -> (Record) entry).forEach(outliers::add);
            }
        }
        return rTrees;
    }





}
