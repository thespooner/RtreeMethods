package Rtree;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

//import java.util.Stack;


public class RTree {

    protected int dimensions = 2;
    protected int M = 40;
    protected int m = 16;
    protected int entryCount;
    protected TreeNode root;

    public RTree(int dimensions, int M, int m) {
        this.dimensions = dimensions;
        this.M = M;
        this.m = m;
        this.root = new TreeNode(1);
        this.entryCount = 0;
    }

    public class TreeNode extends Entry { //  AKA page,
        ArrayList<Entry> entries;
        protected int height = 1; // data records are at zero, simplifies search and insertion + compaction

        //justification for enclosing class is to simplify passing arguments when every a new node is generated.
        private TreeNode(int height){
            super(new MBR(RTree.this.dimensions));
            entries = new ArrayList<Entry>(M);
            this.height = height;
        }
        private TreeNode(TreeNode node){
            super(new MBR(RTree.this.dimensions));
            entries = new ArrayList<Entry>(node.entries);
            this.height = node.height;
            this.mbr = new MBR(node.mbr);
        }

        // necessary when we need to update MBR so that
        private MBR[] getMBRs(){
            MBR[] mbrs = new MBR[entries.size()];
            int i = 0;
            for(Entry e : entries){
                mbrs[i] = e.mbr;
                i++;
            }
            return mbrs;
        }
    }

    private void adjustMbrsAlongPath(Deque<TreeNode> path, TreeNode node){

        node.mbr = MBR.union(node.getMBRs());
        for (TreeNode parent : path) {
            //int index = 0;
            // find the index of the node in the parent
            //while (index < parent.fillCount && parent.entries.get(index) != node){
            //    ++index;
            //}
            MBR beforeUpdate = new MBR(parent.mbr);
            //parent.entries.get(index) = node;
            parent.mbr = MBR.union(parent.getMBRs());
            if (parent.mbr.equals(beforeUpdate)){
                break;
            }
            //node = parent;
        }
    }

    protected void insertion(Record record) {
        TreeNode node = root;
        Deque<TreeNode> path = new ArrayDeque<TreeNode>(); // path along the MBRs.
        int index = 0;

        for (int level = 1; level < root.height; level++) {

            // if several threads are inserting records,
            // should update the mbr right away, considering successful insertion.
            // in one thread per tree, it's better to have a list or array
            // of ancestors(path) as not every mbr along path need to be updated

            path.push(node);
            double bestMatch = -1;

            // choose the one that least expand the mbr
            for (int i = 0; i < node.entries.size(); i++) {
                double enlargment = ((TreeNode) (node.entries.get(i))).mbr.enlargment(record.mbr);
                if (enlargment < bestMatch || bestMatch < 0) {
                    bestMatch = enlargment;
                    index = i;

                }

            // in case of tie, choose the one with the smallest area.
                else if(enlargment <= bestMatch &&
                        ((TreeNode)(node.entries.get(i))).mbr.getArea() < ((TreeNode)(node.entries.get(index))).mbr.getArea()){
                    index = i;
                }
            }
            //path.push((TreeNode) node.entries.get(index));
            node = (TreeNode) node.entries.get(index);

        }

        if(node.entries.size() < this.M){
            node.entries.add(record);
            adjustMbrsAlongPath(path, node);
        } else{
            Boolean split = true;
            node.entries.add(record);
            TreeNode generatedNode = quadraticSplit(node);
            node.mbr = MBR.union(node.getMBRs()); // can't say in advance if cascading splits are going to occur.
            while (!path.isEmpty()){
                node = path.pop();
                if (split) {
                    split = false;
                    if (node.entries.size() < this.M) {
                        node.entries.add(generatedNode);
                    } else {
                        split = true;
                        node.entries.add(generatedNode);
                        generatedNode = quadraticSplit(node);
                    }
                }
                node.mbr = MBR.union(node.getMBRs());
            }
            if (split && node == this.root){
                // create new root
                this.root = new TreeNode(node.height + 1);
                this.root.entries.add(node);
                this.root.entries.add(generatedNode);
                this.root.mbr = MBR.union(root.getMBRs());
            }
        }
        entryCount++;

    }

    /**
     * TODO Implement
     *  primary criterion of this algorithm is to distribute the objects between the two nodes as evenly as possible,
     *  whereas the second criterion is the minimization of the overlapping between them.
     *  Finally, the third criterion is the minimization of the total coverage.
     *
     *  Based on LINEAR R-TREE REVISITED by: A. Al-Badarneh and M. Tawil, 2009
     *
     * @param node
     * @param entry
     */
    public void AngTanLinearSplit(TreeNode node, Entry entry){

        //TODO finish

        /** need a list to keep track of the 3 different list associated with each dimension
         * one list for the mbrs that are closest to the lower bound of the dimension
         * another for the mbrs that are closest to the higher bound of the dimension
         * a final one in case of a tie. a suggest improvement over the original paper, where reinsertion is applied
         */
        List<List<Entry>> lists = new ArrayList<List<Entry>>(dimensions*3);

        node.entries.add(entry); // got M+1 elementes in entries

        // MBR of that will cover all the entries
        MBR coveringRectangle = MBR.union(node.getMBRs());

        for(int i = 0; i<dimensions*3; i++){
            lists.add(new ArrayList<Entry>());
        }

        // ::The distribution phase::

        for(Entry e : node.entries){
            for(int i = 0; i< dimensions; i++){
                if(e.mbr.low[i] - coveringRectangle.low[i] < coveringRectangle.high[i] - e.mbr.high[i]){
                    lists.get(i * 3).add(e);
                } else if (e.mbr.low[i] - coveringRectangle.low[i] > coveringRectangle.high[i] - e.mbr.high[i]){
                    lists.get(i * 3 + 2 ).add(e);
                } else{
                    lists.get(i * 3 + 1 ).add(e);
                }
            }
        }

        //last step of the distribution phase. Can't really have ties, need to place them somewhere.
        // choose the list with the fewest entries.


    }


    /**
     * The same Spliting method as the quadratic split from R*
     * Originally this particular method was developed by Ottesen
     * @param node that is overflowed
     */
    private TreeNode quadraticSplit(TreeNode node){

        double minMargin = Double.POSITIVE_INFINITY;
        int minAxis = 0;
        ArrayList<Entry> entries = new ArrayList<Entry>(node.entries);
        node.entries.clear();
        ArrayList<Entry> entriesNode1 = new ArrayList<Entry>();
        ArrayList<Entry> entriesNode2 = new ArrayList<Entry>();


        for (int axis = 0; axis < dimensions; axis++) {
            for (int low = 0; low < 2; low++) {

                entries.sort(new MBRComparator(axis, low == 1));

                for (int k = m; k <= entries.size() - m; k++) {
                    MBR u1 = MBR.union(entries.subList(0, k).stream().map( e -> e.mbr).toArray(MBR[]::new));
                    MBR u2 = MBR.union(entries.subList(k, entries.size()).stream().map( e -> e.mbr).toArray(MBR[]::new));

                    double margin = u1.getMargin() + u2.getMargin();

                    if (margin < minMargin) {
                        minMargin = margin;
                        minAxis = axis;
                    }
                }
            }
        }

        double minOverlap = Double.POSITIVE_INFINITY;
        double minArea = Double.POSITIVE_INFINITY;

        for (int low = 0; low < 2; low++) {

            entries.sort(new MBRComparator(minAxis, low == 1));
            for (int k = m; k <= entries.size() - m; k++) {
                MBR u1 = MBR.union(entries.subList(0, k).stream().map( e -> e.mbr).toArray(MBR[]::new));
                MBR u2 = MBR.union(entries.subList(k, entries.size()).stream().map( e -> e.mbr).toArray(MBR[]::new));

                MBR intersection = MBR.intersection(u1, u2);
                double overlap = intersection.getArea();
                double area = u1.getArea() + u2.getArea();

                if ((overlap < minOverlap) || ((overlap == minOverlap) && (area < minArea))) {
                    minOverlap = overlap;
                    minArea = area;

                    entriesNode1.clear();
                    entriesNode1.addAll(entries.subList(0, k));
                    entriesNode2.clear();
                    entriesNode2.addAll(entries.subList(k, entries.size()));
                }
            }
        }

        node.entries.addAll(entriesNode1);
        TreeNode generatedNode = new TreeNode(node.height);
        generatedNode.entries.addAll(entriesNode2);
        generatedNode.mbr = MBR.union(generatedNode.getMBRs());
        return generatedNode;
    }

    public ArrayList<Record> search(MBR area){
        Queue<Entry> queue = new LinkedBlockingQueue<>();
        TreeNode node = this.root;
        queue.add(node);
        ArrayList<Record> result = new ArrayList<Record> ();
        while (!queue.isEmpty()) {
            node = (TreeNode) queue.poll();
            for (Entry child : node.entries) {
                if (MBR.intersects(child.mbr, area)) {
                    TreeNode page = (TreeNode) child;
                    if (page.height == 1){
                        for(Entry entry: page.entries){
                            if (MBR.intersects(entry.mbr, area)) {
                                result.add((Record) entry);
                            }
                        }
                    }else {
                        queue.add(page);
                    }

                }
            }
        }
        return result;
    }

    public ArrayList<MBR> getMBRs(int level) {
        Queue<Entry> queue = new LinkedList<>();
        TreeNode node = this.root;
        //System.out.println("level: "+node.level);
        ArrayList<MBR> result = new ArrayList<MBR>();

        // If root level, than return the MBR of the whole tree.
        if (node.height == level){
            result.add(node.mbr);
            return result;
        } else if(node.height-1 == level){
            for(Entry e : node.entries){
                result.add(e.mbr);
            }
            return result;
        }
        queue.add(node);
        while (!queue.isEmpty()) {
            node = (TreeNode) queue.poll();
            for (Entry child : node.entries) {
                if (((TreeNode)child).height == level + 1){
                    for(Entry e : ((TreeNode)child).entries){
                        result.add(e.mbr);
                    }
                }else if(level + 1 < ((TreeNode)child).height){
                    queue.add(child);
                }
            }
        }
        return result;
    }

    public void STRpacking(Record... records){
        ArrayList<Entry> entries = new ArrayList<Entry>(Arrays.stream(records).collect(Collectors.toList()));
        this.entryCount = entries.size();
        int dim = this.dimensions;
        double p = entries.size() / M;
        entries.sort(new MBRComparator(0, true));
        entries = STRrecursive(dim, p,  entries);
        STRpackNodes(entries);

    }

    public ArrayList<Entry> STRSeededClustering(ArrayList<Entry> entries, int height){
        int dim = this.dimensions;
        double p = Math.ceil(entries.size() / M);
        entries.sort(new MBRComparator(0, true));
        entries = STRrecursive(dim, p,  entries);
        ArrayList<Entry> nextLevel = new ArrayList<Entry>();

        TreeNode node = new TreeNode(height);
        for (Entry e : entries) {
            node.entries.add(e);
            if (node.entries.size() == M) {
                node.mbr = MBR.union(node.getMBRs());
                nextLevel.add(new TreeNode(node));
                node = new TreeNode(height);
            }
        }
        // in the case of node underflow
        if (node.entries.size() > 0) {
            /**
             //ALT 1: better for 2d
             while (node.entries.size() < m){
             TreeNode swapped = (TreeNode)nextLevel.get(nextLevel.size() -1);
             node.entries.add(0,swapped.entries.remove(swapped.entries.size()-1));
             swapped.mbr = MBR.union(swapped.getMBRs());
             }
             node.mbr = MBR.union(node.getMBRs());
             nextLevel.add(new TreeNode(node));

             **/
            //Alternative 2: same partitioning problem as encountered earlier, better when d>2

            if(m > node.entries.size()) {
                TreeNode swapped = (TreeNode) nextLevel.get(nextLevel.size() - 1);
                swapped.entries.addAll(node.entries);
                node.entries.clear();
                node = generalistQuadraticSplit(swapped);
                nextLevel.add(new TreeNode(node));
            }else {
                node.mbr = MBR.union(node.getMBRs());
                nextLevel.add(new TreeNode(node));
            }
            //**/
        }

        return nextLevel;

    }

    public void STRpackNodes(ArrayList<Entry> entries){
        int height = 1;
        while (entries.size() > M) {
            // need to avoid node underflow
            ArrayList<Entry> nextLevel = new ArrayList<Entry>();

            TreeNode node = new TreeNode(height);
            for (Entry e : entries) {
                node.entries.add(e);
                if (node.entries.size() == M) {
                    node.mbr = MBR.union(node.getMBRs());
                    nextLevel.add(new TreeNode(node));
                    node = new TreeNode(height);
                }
            }
            if (node.entries.size() > 0 ) {
                /**
                //ALT 1: better for 2d
                while (node.entries.size() < m){
                    TreeNode swapped = (TreeNode)nextLevel.get(nextLevel.size() -1);
                    node.entries.add(0,swapped.entries.remove(swapped.entries.size()-1));
                    swapped.mbr = MBR.union(swapped.getMBRs());
                }
                 node.mbr = MBR.union(node.getMBRs());
                 nextLevel.add(new TreeNode(node));

                **/
                //Alternative 2: same partitioning problem as encountered earlier, better when d>2

                if(m > node.entries.size()) {
                    TreeNode swapped = (TreeNode) nextLevel.get(nextLevel.size() - 1);
                    swapped.entries.addAll(node.entries);
                    node.entries.clear();
                    node = generalistQuadraticSplit(swapped);
                    nextLevel.add(new TreeNode(node));
                }else {
                    node.mbr = MBR.union(node.getMBRs());
                    nextLevel.add(new TreeNode(node));
                }
                //**/
            }

            entries.clear();
            double p = nextLevel.size() / M;
            nextLevel.sort(new MBRComparator(0, true));
            nextLevel = STRrecursive(this.dimensions,p,nextLevel);
            nextLevel.forEach(entries::add);
            height++;
        }
        if(entries.size() == 1){
            this.root = (TreeNode) entries.get(0);
        }else{
            this.root.height = height;
            this.root.entries = entries;
            this.root.mbr = MBR.union(this.root.getMBRs());
        }
    }

    private ArrayList<Entry> STRrecursive(int d, double p, ArrayList<Entry> entries ){
            if(d == 1){
                return entries;
            }
            double s = Math.pow(Math.E, Math.log(p)/d);
            ArrayList<Entry> list = new ArrayList<Entry>();
            ArrayList<Entry> entriesSorted = new ArrayList<Entry>();
            int ctr = 0;
            for(Entry entry : entries){
                list.add(entry);
                ctr++;
                if(ctr == Math.ceil((p/s))*M){//Math.ceil(Math.pow(Math.E, Math.log(p)*((float)(d-1)/d)))*M){ // s*M for 2 d
                    list.sort(new MBRComparator(d - 1, true));
                    // origial : //list = STRrecursive(d - 1,ctr, list );
                    list = STRrecursive(d - 1,ctr/M, list );
                    list.forEach(entriesSorted::add);
                    list = new ArrayList<Entry>();
                    ctr = 0;
                }
            }
            if(list.size() > 0){
                list.sort(new MBRComparator(d - 1, true));
                //list = STRrecursive(d - 1,p, list );
                list = STRrecursive(d - 1,ctr/M, list );
                list.forEach(entriesSorted::add); // add remaining entries
            }
            return entriesSorted;
    }

    public void STRStar(Record... records){
        ArrayList<Entry> entries = new ArrayList<Entry>(Arrays.stream(records).collect(Collectors.toList()));
        this.entryCount = entries.size();
        entries = STRSeededClustering(entries,1);
        int height = 1;
        while (entries.size()>M) {
            height++;
            ArrayList<TreeNode> overflowedNodes = new ArrayList<TreeNode>();
            TreeNode node = new TreeNode(height);
            node.entries.addAll(entries);
            entries.clear();
            node.mbr = MBR.union(node.getMBRs());
            overflowedNodes.add(node);
            boolean overFlow = true;
            while (overFlow) {
                overFlow = false;
                ArrayList<TreeNode> generatednodes = new ArrayList<TreeNode>();
                for (TreeNode n : overflowedNodes) {
                    if (n.entries.size() > M) {
                        generatednodes.add(generalistQuadraticSplit(n));
                        overFlow = true;
                    }
                }
                overflowedNodes.addAll(generatednodes);
            }
            entries.addAll(overflowedNodes);
        }

        if(entries.size() == 1){
            this.root = (TreeNode) entries.get(0);
        }else{
            this.root.height = height+1;
            this.root.entries = entries;
            this.root.mbr = MBR.union(this.root.getMBRs());
        }

    }

    public void SeededClustering(int k, int treshold, Record... records){
        SeededTree seededTree = new SeededTree(this, k);
        seededTree.insert(records);
        entryCount += records.length;
        ArrayList<TreeNode> roots = seededTree.buildRtrees(treshold);
        roots.forEach(this::insertTreeSC);
        entryCount -= seededTree.outliers.size();
        seededTree.outliers.forEach(this::insertion);

    }

    public void insertTreeSC(TreeNode inputRoot){
        // height of input tree
        int hi = inputRoot.height;
        // check if valid tree
        if(this.root.height - hi < 1){
            inputRoot.entries.stream().map(entry -> (TreeNode) entry).forEach(this::insertTreeSC);
            return;
        }

        Deque<TreeNode> path = new ArrayDeque<TreeNode>(); // path along the MBRs.
        int index = 0;
        TreeNode node = this.root;
        for (int level = 1 ; level < this.root.height - hi; level++){
            path.push(node);
            double bestMatch = -1;

            // choose the one that least expand the mbr
            for (int i = 0; i < node.entries.size(); i++) {
                double enlargment = ((TreeNode) (node.entries.get(i))).mbr.enlargment(inputRoot.mbr);
                if (enlargment < bestMatch || bestMatch < 0) {
                    bestMatch = enlargment;
                    index = i;

                }

                // in case of tie, choose the one with the smallest area.
                else if(enlargment <= bestMatch &&
                        ((TreeNode)(node.entries.get(i))).mbr.getArea() < ((TreeNode)(node.entries.get(index))).mbr.getArea()){
                    index = i;
                }
            }
            node = (TreeNode) node.entries.get(index);

        }

        // attempt to avoid creating illegal R-tree
        // TODO find a more optimal method for solving this problem.
        // Bulk Insertion for R-tree by Seeded Clustering, does not go into details
        if(inputRoot.entries.size() < this.m){
            ArrayList<Entry> noMatch = new ArrayList<Entry>();
            for(Entry e : inputRoot.entries){
                boolean flag = true;
                for(Entry sibling : node.entries){
                    if(((TreeNode) sibling).entries.size() < M && sibling.mbr.contains(e.mbr)){
                        ((TreeNode) sibling).entries.add(e);
                        flag = false;
                        break;
                    }
                }
                if(flag){
                    noMatch.add(e);
                }
            }
            if(inputRoot.height == 1){
                entryCount-= noMatch.size();
                noMatch.stream().map(entry -> (Record) entry).forEach(this::insertion);
            } else{
                noMatch.stream().map(entry -> (TreeNode) entry).forEach(this::insertTreeSC);
            }
        }
        // The case when there are more than m entries in root of the tree.
        else{
            SeedClusteringPostProcess(node,inputRoot);
            if(node.entries.size()<=M){
                adjustMbrsAlongPath(path,node);
            }else{
                Boolean split = true;
                ArrayList<TreeNode> overflow = new ArrayList<TreeNode>();
                while (node.entries.size() > M + 1){
                    overflow.add((TreeNode) node.entries.remove(node.entries.size()-1));
                }
                TreeNode generatedNode = quadraticSplit(node);
                node.mbr = MBR.union(node.getMBRs()); // can't say in advance if cascading splits are going to occur.
                while (!path.isEmpty()){
                    node = path.pop();
                    if (split) {
                        split = false;
                        if (node.entries.size() < this.M) {
                            node.entries.add(generatedNode);
                        } else {
                            split = true;
                            node.entries.add(generatedNode);
                            generatedNode = quadraticSplit(node);
                        }
                    }
                    node.mbr = MBR.union(node.getMBRs());
                }
                if (split && node == this.root){
                    // create new root
                    this.root = new TreeNode(node.height + 1);
                    this.root.entries.add(node);
                    this.root.entries.add(generatedNode);
                    this.root.mbr = MBR.union(root.getMBRs());
                }
                overflow.forEach(this::insertTreeSC);
            }
        }



    }

    private void SeedClusteringPostProcess( TreeNode nt, TreeNode ni){
        ArrayList<Entry> noOverlap = new ArrayList<Entry>(nt.entries);
        ArrayList<Entry> overlap = new ArrayList<Entry>();
        for(Entry sibling : nt.entries){
            if(MBR.intersects(sibling.mbr, ni.mbr)){
                overlap.add(sibling);
            }
        }
        noOverlap.removeAll(overlap);
        ArrayList<Entry> repacked = RepackSC(overlap,ni);
        nt.entries.clear();
        nt.entries.addAll(noOverlap);
        nt.entries.addAll(repacked);
        nt.mbr = MBR.union(nt.getMBRs());
    }

    private ArrayList<Entry> RepackSC(ArrayList<Entry> set, TreeNode ni){
        ArrayList<Entry> entries = new ArrayList<Entry>();
        set.stream().map(entry -> ((TreeNode) entry).entries).forEach(entries::addAll);
        ArrayList<Entry> repacked = new ArrayList<Entry>();

        //check if leaf
        if(ni.height == 1){
            ArrayList<Entry> union = new ArrayList<Entry>();
            union.addAll(entries);
            union.addAll(ni.entries);
            return STRSeededClustering(union, ni.height);
        } else {
            // entries with no overlap.
            ArrayList<Entry> noOverlap = new ArrayList<Entry>(entries);
            for (Entry entry : entries) {
                for (Entry e : ni.entries) {
                    if (MBR.intersects(entry.mbr, e.mbr)) {
                        noOverlap.remove(entry);
                        break;
                    }
                }
            }

            entries.removeAll(noOverlap);


            for (Entry e : ni.entries) {
                ArrayList<Entry> overlap = new ArrayList<Entry>();
                for (Entry entry : entries) {
                    if (MBR.intersects(e.mbr, entry.mbr)) {
                        overlap.add(entry);
                    }
                }
            /*
            * There is this problem if more than one entry of ni overlapping with the same entry.
            * They should be repacked more than once.
            * For sake of simplicity and to avoid cascading computations, entries are removed after they are repacked
            * */

                //ArrayList<Entry> newlyPacked = RepackSC(overlap, (TreeNode) e);
                //newlyPacked.stream().map(entry -> ((TreeNode) entry).entries).forEach(entries::addAll);


                entries.removeAll(overlap);
                if (overlap.size() > 0) {
                    repacked.addAll(RepackSC(overlap, (TreeNode) e));
                } else {
                    repacked.add(e);
                }
            }
            ArrayList<Entry> union = new ArrayList<Entry>();
            union.addAll(noOverlap);
            union.addAll(repacked);
            return STRSeededClustering(union, ni.height);
        }
    }

    public RTree mergingGBIPostPruning(RTree inputRTree){
        if(inputRTree.root.height > this.root.height){
            return inputRTree.mergingGBIPostPruning(this);
        }
        entryCount += inputRTree.entryCount;
        Queue<Entry> insertionQueue = new LinkedList<Entry>();

        if(inputRTree.root.height == this.root.height){
            insertionQueue.addAll(inputRTree.root.entries);
        } else{
            insertionQueue.add(inputRTree.root);
        }

        while (!insertionQueue.isEmpty()) {

            Entry entry = insertionQueue.remove();
            if (entry instanceof TreeNode) {
                TreeNode subTree = (TreeNode) entry;
                double area1 = checkAreaEnlargement(subTree, subTree.height);
                double area2 = 0;
                for (Entry e : subTree.entries) {
                    area2 += checkAreaEnlargement(e, subTree.height - 1);
                }
                if (area1 > area2) {
                    insertionQueue.addAll(subTree.entries);
                }
                else{
                    insertTreeGBIPostPruning(subTree, false);
                }
            }
            else{
                this.entryCount--;
                this.insertion((Record) entry);
            }

        }
        return this;

    }

    public void insertTreeGBIPostPruning(TreeNode inputRoot, boolean reinserted){
        int hi = inputRoot.height;
        Deque<TreeNode> path = new ArrayDeque<TreeNode>(); // path along the MBRs.
        int index = 0;
        TreeNode node = root;
        for (int level = 1 ; level < this.root.height - hi; level++) {
            path.push(node);
            double bestMatch = -1;

            // choose the one that least expand the mbr
            for (int i = 0; i < node.entries.size(); i++) {
                double enlargment = ((TreeNode) (node.entries.get(i))).mbr.enlargment(inputRoot.mbr);
                if (enlargment < bestMatch || bestMatch < 0) {
                    bestMatch = enlargment;
                    index = i;

                }
            }

            node = (TreeNode) node.entries.get(index);
        }
        if(inputRoot.entries.size() < this.m){
            ArrayList<Entry> noMatch = new ArrayList<Entry>();
            for(Entry e : inputRoot.entries){
                boolean flag = true;
                for(Entry sibling : node.entries){
                    if(((TreeNode) sibling).entries.size() < M && sibling.mbr.contains(e.mbr)){
                        ((TreeNode) sibling).entries.add(e);
                        flag = false;
                        break;
                    }
                }
                if(flag){
                    noMatch.add(e);
                }
            }
            if(inputRoot.height == 1){
                entryCount--;
                noMatch.stream().map(entry -> (Record) entry).forEach(this::insertion);
                } else{
                for(Entry entry : noMatch){
                    insertTreeGBIPostPruning(((TreeNode) entry),reinserted);
                }
            }
        }
        else {
            SeedClusteringPostProcess(node, inputRoot);
            while (node.entries.size() > M + 1){
                if (!canReinsert(node,reinserted)){
                    break;
                }
            }
            if (node.entries.size() <= M) {
                adjustMbrsAlongPath(path, node);
            } else {
                Boolean split = true;
                ArrayList<TreeNode> overflow = new ArrayList<TreeNode>();
                TreeNode generatedNode = quadraticSplit(node);
                node.mbr = MBR.union(node.getMBRs()); // can't say in advance if cascading splits are going to occur.
                while (!path.isEmpty()) {
                    node = path.pop();
                    if (split) {
                        split = false;
                        if (node.entries.size() < this.M) {
                            node.entries.add(generatedNode);
                        } else {
                            split = true;
                            node.entries.add(generatedNode);
                            generatedNode = quadraticSplit(node);
                        }
                    }
                    node.mbr = MBR.union(node.getMBRs());
                }
                if (split && node == this.root) {
                    // create new root
                    this.root = new TreeNode(node.height + 1);
                    this.root.entries.add(node);
                    this.root.entries.add(generatedNode);
                    this.root.mbr = MBR.union(root.getMBRs());
                }
            }
        }
    }

    private double checkAreaEnlargement(Entry entry, int height){
        int index = 0;
        TreeNode node = root;
        double areaexpansion = node.mbr.enlargment(entry.mbr);
        for (int level = 1 ; level < this.root.height - height; level++){
            double bestMatch = -1;

            // choose the one that least expand the mbr
            for (int i = 0; i < node.entries.size(); i++) {
                double enlargment = ((TreeNode) (node.entries.get(i))).mbr.enlargment(entry.mbr);
                if (enlargment < bestMatch || bestMatch < 0) {
                    bestMatch = enlargment;
                    index = i;

                }
            }
            areaexpansion = bestMatch;
            node = (TreeNode) node.entries.get(index);
        }
        return areaexpansion;
    }

    private boolean canReinsert(TreeNode node, boolean reinserted) {
        if(reinserted){
            return false;
        }
        float[] center = new float[dimensions];
        for (int d = 0; d < dimensions; d++) {
            center[d] = (node.mbr.high[d] + node.mbr.low[d]) / 2;
        }
        float[][] centers = new float[node.entries.size()][dimensions];
        for(int i = 0 ; i < node.entries.size(); i++) {
            for (int d = 0; d < dimensions; d++) {
                centers[i][d] = (node.entries.get(i).mbr.high[d] + node.entries.get(i).mbr.low[d]) / 2;
            }
        }
        float longestDistance = 0;
        int index = 0;
        for (int d = 0; d < dimensions; d++) {
            longestDistance += Math.abs(center[d] - centers[index][d]);
        }
        for(int i = 1 ; i < node.entries.size(); i++){
            float manhattenDistance = 0;
            for (int d = 0; d < dimensions; d++) {
                manhattenDistance += Math.abs(center[d] - centers[i][d]);
            }
            if(manhattenDistance > longestDistance){
                longestDistance = manhattenDistance;
                index = i;
            }

        }
        if (node.height > 1){
            TreeNode reinsertNode = (TreeNode) node.entries.remove(index);
            node.mbr = MBR.union(node.getMBRs());
            insertTreeGBIPostPruning(reinsertNode, true);
        }else {
            Record record = (Record) node.entries.remove(index);
            node.mbr = MBR.union(node.getMBRs());
            this.entryCount--;
            insertion(record);
        }
        return true;
    }

    public RTree mergingWithBuffers(RTree inputRTree){
        if(inputRTree.root.height > this.root.height){
            return inputRTree.mergingWithBuffers(this);
        }
        entryCount += inputRTree.entryCount;
        Stack<Entry> insertionQueue = new Stack<Entry>();

        if(inputRTree.root.height == this.root.height){
            insertionQueue.addAll(inputRTree.root.entries);
        } else{
            insertionQueue.add(inputRTree.root);
        }

        HashMap<TreeNode, ArrayList<Entry>> localInsetionQueues = new HashMap<TreeNode, ArrayList<Entry>>();
        HashMap<TreeNode, TreeNode> parentPointer = new HashMap<TreeNode, TreeNode>();
        TreeNode head = root;
        if (head.height > 1){
            for(Entry entry :head.entries){
                parentPointer.put((TreeNode) entry, head );
            }
        }

        while (!insertionQueue.isEmpty()) {
            Entry entry = insertionQueue.pop();
            if (entry instanceof TreeNode) {
                TreeNode subTree = (TreeNode) entry;
                head = root;
                for (int level = 1 ; level < this.root.height - subTree.height; level++){
                    int index = 0;
                    double bestMatch = -1;
                    // choose the one that least expand the mbr
                    for (int i = 0; i < head.entries.size(); i++) {
                        double enlargment = ((TreeNode) (head.entries.get(i))).mbr.enlargment(subTree.mbr);
                        if (enlargment < bestMatch || bestMatch < 0) {
                            bestMatch = enlargment;
                            index = i;
                        }
                    }
                    double areaChildren = 0;

                    /*
                    * Area Criterion
                    * */

                    if(subTree.height + 1 < ((TreeNode) head.entries.get(index)).height) {
                        for (Entry e : subTree.entries) {
                            areaChildren += AreaEnlargementChild(e, (TreeNode) head.entries.get(index));
                        }
                        if ((bestMatch > areaChildren)) {
                            insertionQueue.addAll(subTree.entries);
                            break;
                        }
                    }

                    if(parentPointer.containsKey((TreeNode) head.entries.get(index))){
                        head = (TreeNode) head.entries.get(index);
                    } else {
                        parentPointer.put((TreeNode) head.entries.get(index), head);
                        head = (TreeNode) head.entries.get(index);
                    }
                }


                /*
                 * Overlap Criterion
                 * */

                if (head.height == subTree.height +1) {

                    if (!checkOverlap(head, subTree)) {
                        insertionQueue.addAll(subTree.entries);
                    } else {
                        if (localInsetionQueues.containsKey(head)) {
                            localInsetionQueues.get(head).add(subTree);
                        } else {
                            localInsetionQueues.put(head, new ArrayList<Entry>());
                            localInsetionQueues.get(head).add(subTree);
                        }
                    }
                }
            }

            else{
                //this.entryCount--;
                //this.insertion((Record) entry);
                head = root;
                for (int level = 1; level < root.height; level++) {
                    int index = 0;
                    double bestMatch = -1;
                    // choose the one that least expand the mbr
                    for (int i = 0; i < head.entries.size(); i++) {
                        double enlargment = ((TreeNode) (head.entries.get(i))).mbr.enlargment(entry.mbr);
                        if (enlargment < bestMatch || bestMatch < 0) {
                            bestMatch = enlargment;
                            index = i;
                        }
                        // in case of tie, choose the one with the smallest area.
                        else if(enlargment <= bestMatch &&
                                ((TreeNode)(head.entries.get(i))).mbr.getArea() < ((TreeNode)(head.entries.get(index))).mbr.getArea()){
                            index = i;
                        }
                    }
                    if(parentPointer.containsKey((TreeNode) head.entries.get(index))){
                        head = (TreeNode) head.entries.get(index);
                    } else {
                        parentPointer.put((TreeNode) head.entries.get(index), head);
                        head = (TreeNode) head.entries.get(index);
                    }
                }

                if(localInsetionQueues.containsKey(head)){
                    localInsetionQueues.get(head).add(entry);
                }else {
                    localInsetionQueues.put(head, new ArrayList<Entry>());
                    localInsetionQueues.get(head).add(entry);
                }
            }
        }
        while (!localInsetionQueues.isEmpty()){
            ArrayList<TreeNode> nodes = new ArrayList<TreeNode>(localInsetionQueues.keySet());
            for (TreeNode node : nodes){
                node.entries.addAll(localInsetionQueues.get(node));
                node.mbr = MBR.union(node.getMBRs());
                if(node.entries.size() > M){
                    ArrayList<TreeNode> overflowedNodes = new ArrayList<TreeNode>();
                    overflowedNodes.add(node);
                    //overflowedNodes.add(generalistQuadraticSplit(node));
                    while (true){
                        boolean overFlow = false;
                        ArrayList<TreeNode> generatednodes = new ArrayList<TreeNode>();
                        for(TreeNode n : overflowedNodes){
                            if(n.entries.size() > M){
                                generatednodes.add(generalistQuadraticSplit(n));
                                overFlow = true;
                            }
                        }
                        overflowedNodes.addAll(generatednodes);
                        if(!overFlow){
                            break;
                        }
                    }
                    if(node.height == root.height){
                        this.root = new TreeNode(node.height + 1);
                        parentPointer.put(node,root);
                        localInsetionQueues.put(parentPointer.get(node) ,new ArrayList<Entry>());
                        localInsetionQueues.get(parentPointer.get(node)).add(node);

                    }
                    if(!localInsetionQueues.containsKey(parentPointer.get(node))){
                        localInsetionQueues.put(parentPointer.get(node) ,new ArrayList<Entry>());
                    }
                    localInsetionQueues.get(parentPointer.get(node)).addAll(overflowedNodes.subList(1,overflowedNodes.size()));
                }
                localInsetionQueues.remove(node);
            }
        }
        return this;
    }

    private double AreaEnlargementChild(Entry entry, TreeNode node){
        double bestMatch = -1;
        for (int level = 0 ; level < 1; level++){
            for (int i = 0; i < node.entries.size(); i++) {
                double enlargment = ((TreeNode) (node.entries.get(i))).mbr.enlargment(entry.mbr);
                if (enlargment < bestMatch || bestMatch < 0) {
                    bestMatch = enlargment;
                }
            }
        }
        return bestMatch;
    }

    private TreeNode generalistQuadraticSplit(TreeNode node){

        double minMargin = Double.POSITIVE_INFINITY;
        int minAxis = 0;
        ArrayList<Entry> entries = new ArrayList<Entry>(node.entries);
        node.entries.clear();
        int l = (int) Math.ceil((entries.size()*m) / (M+1));
        ArrayList<Entry> entriesNode1 = new ArrayList<Entry>();
        ArrayList<Entry> entriesNode2 = new ArrayList<Entry>();


        for (int axis = 0; axis < dimensions; axis++) {
            for (int low = 0; low < 2; low++) {

                entries.sort(new MBRComparator(axis, low == 1));

                for (int k = l; k <= entries.size() - l; k++) {
                    MBR u1 = MBR.union(entries.subList(0, k).stream().map( e -> e.mbr).toArray(MBR[]::new));
                    MBR u2 = MBR.union(entries.subList(k, entries.size()).stream().map( e -> e.mbr).toArray(MBR[]::new));

                    double margin = u1.getMargin() + u2.getMargin();

                    if (margin < minMargin) {
                        minMargin = margin;
                        minAxis = axis;
                    }
                }
            }
        }

        double minOverlap = Double.POSITIVE_INFINITY;
        double minArea = Double.POSITIVE_INFINITY;

        // need a list of mbrs

        for (int low = 0; low < 2; low++) {

            entries.sort(new MBRComparator(minAxis, low == 1));
            for (int k = l; k <= entries.size() - l; k++) {
                MBR u1 = MBR.union(entries.subList(0, k).stream().map( e -> e.mbr).toArray(MBR[]::new));
                MBR u2 = MBR.union(entries.subList(k, entries.size()).stream().map( e -> e.mbr).toArray(MBR[]::new));

                MBR intersection = MBR.intersection(u1, u2);
                double overlap = intersection.getArea();
                double area = u1.getArea() + u2.getArea();

                if ((overlap < minOverlap) || ((overlap == minOverlap) && (area < minArea))) {
                    minOverlap = overlap;
                    minArea = area;

                    entriesNode1.clear();
                    entriesNode1.addAll(entries.subList(0, k));
                    entriesNode2.clear();
                    entriesNode2.addAll(entries.subList(k, entries.size()));
                }
            }
        }

        node.entries.addAll(entriesNode1);
        node.mbr = MBR.union(node.getMBRs());
        TreeNode generatedNode = new TreeNode(node.height);
        generatedNode.entries.addAll(entriesNode2);
        generatedNode.mbr = MBR.union(generatedNode.getMBRs());

        return generatedNode;
    }

    private boolean checkOverlap(TreeNode nt, Entry e) {

        // no best place to insert regardless
        if(nt.height == 1){
            return true;
        }

        double totalOverlap1 = 0;
        for (Entry sibling : nt.entries) {
            totalOverlap1 += MBR.intersection(e.mbr, sibling.mbr).getArea();
        }

        double totalOverlap2 = 0;

        // check the total generated overlap of the children of e
        for (Entry entry : ((TreeNode)e).entries) {

            double bestMatch = -1;
            int index = 0;
            TreeNode node = nt;


            // choose the one that least expand the mbr
            for (int i = 0; i < node.entries.size(); i++) {
                double enlargment = ((TreeNode) (node.entries.get(i))).mbr.enlargment(entry.mbr);
                if (enlargment < bestMatch || bestMatch < 0) {
                    bestMatch = enlargment;
                    index = i;

                }

                // in case of tie, choose the one with the smallest area.
                else if (enlargment <= bestMatch &&
                        ((TreeNode) (node.entries.get(i))).mbr.getArea() < ((TreeNode) (node.entries.get(index))).mbr.getArea()) {
                    index = i;
                }
            }
            node = (TreeNode) node.entries.get(index);

            for (Entry sibling : node.entries) {
                totalOverlap2 += MBR.intersection(e.mbr, sibling.mbr).getArea();
            }
        }
        if(totalOverlap1 < totalOverlap2){
            return true;
        }else return false;
    }

    public int IOcountQuery(MBR area){
        Queue<Entry> queue = new LinkedList<Entry>();
        TreeNode node = this.root;
        queue.add(node);
        int IOcount = 0;
        while (!queue.isEmpty()) {
            node = (TreeNode) queue.poll();
            ++IOcount;
            for (Entry child : node.entries) {
                if (MBR.intersects(child.mbr, area)) {
                    TreeNode page = (TreeNode) child;
                    if (page.height == 1){
                        for(Entry entry: page.entries){
                            if (MBR.intersects(entry.mbr, area)) {
                                ++IOcount;
                            }
                        }
                    }else {
                        queue.add(page);
                        }

                    }
                }
            }
            return IOcount;
        }


}
