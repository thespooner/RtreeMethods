package Rtree;


import java.util.Arrays;
import java.util.Random;

public class ConstructionTime {
    static int n = 5;
    static int dim = 2;
    static int M = 40;
    static int m = 16;
    final private static int border = 100;

    public static void main(String[]args) throws Exception {
        for (int j = 1; j < 11; j++) {
            int N = 1000000*j;
            float[] low = new float[dim];
            float[] high = new float[dim];
            Record[] records = new Record[N];
            for (int i = 0; i < N; i++) {
                for (int v = 0; v < dim; v++) {
                    Random r1 = new Random();
                    low[v] = r1.nextFloat() * border;// (border - line);
                    high[v] = low[v];//+ line;

                }
                Record rec = new Record(new MBR(low, high));
                records[i] = rec;
            }


            System.out.println("the round: " + j);

            System.out.println("Time OBO: avg after " + n);
            long[] runs = new long[n];
            for (int i = 0; i < n; i++) {
                RTree rTree = new RTree(dim, M, m);
                long t0 = System.currentTimeMillis();
                for (Record record : records) {
                    rTree.insertion(record);
                }
                runs[i] = System.currentTimeMillis() - t0;
            }
            long sum = 0;
            for (long r : runs) sum += r;
            System.out.println(1.0d * sum / n);

            System.out.println("Time STR: avg after " + n);
            for (int i = 0; i < n; i++) {
                RTree rTree = new RTree(dim, M, m);
                long t0 = System.currentTimeMillis();
                rTree.STRpacking(records);
                runs[i] = System.currentTimeMillis() - t0;
            }
            sum = 0;
            for (long r : runs) sum += r;
            System.out.println(1.0d * sum / n);

            System.out.println("Time merging without buffers half: avg after " + n);
            for (int i = 0; i < n; i++) {
                RTree rTree1 = new RTree(dim, M, m);
                RTree rTree2 = new RTree(dim, M, m);
                rTree1.STRpacking(Arrays.copyOfRange(records, 0, N/2));
                rTree2.STRpacking(Arrays.copyOfRange(records, N/2, N));
                long t0 = System.currentTimeMillis();
                rTree1.mergingGBIPostPruning(rTree2);
                runs[i] = System.currentTimeMillis() - t0;
            }
            sum = 0;
            for (long r : runs) sum += r;
            System.out.println(1.0d * sum / n);

            System.out.println("Time merging with buffers half: avg after " + n);
            for (int i = 0; i < n; i++) {
                RTree rTree1 = new RTree(dim, M, m);
                RTree rTree2 = new RTree(dim, M, m);
                rTree1.STRpacking(Arrays.copyOfRange(records, 0, N/2));
                rTree2.STRpacking(Arrays.copyOfRange(records, N/2, N));
                //records = Experiment.randomize(records,3173958);
                long t0 = System.currentTimeMillis();
                rTree1.mergingWithBuffers(rTree2);
                runs[i] = System.currentTimeMillis() - t0;
            }
            sum = 0;
            for (long r : runs) sum += r;
            System.out.println(1.0d * sum / n);

            System.out.println("Time Seeded clustering half: avg after " + n);
            for (int i = 0; i < n; i++) {
                RTree rTree1 = new RTree(dim, M, m);
                rTree1.STRpacking(Arrays.copyOfRange(records, 0, N/2));
                Record[] recs = (Arrays.copyOfRange(records, N/2, N));
                long t0 = System.currentTimeMillis();
                rTree1.SeededClustering(3, 16, recs);
                runs[i] = System.currentTimeMillis() - t0;
            }
            sum = 0;
            for (long r : runs) sum += r;
            System.out.println(1.0d * sum / n);


            System.out.println("Time merging without buffers 9 to 9: avg after " + n);
            for (int i = 0; i < n; i++) {
                RTree rTree1 = new RTree(dim, M, m);
                rTree1.STRpacking(Arrays.copyOfRange(records, 0, N/9));
                long partsum = 0;
                for (int r = 1; r < 9; r++) {
                    RTree rTree2 = new RTree(dim, M, m);
                    rTree2.STRpacking(Arrays.copyOfRange(records, N/9 * r, N/9 * (r + 1)));
                    long t0 = System.currentTimeMillis();
                    rTree1 = rTree1.mergingGBIPostPruning(rTree2);
                    partsum += System.currentTimeMillis() - t0;
                }
                runs[i] = partsum;
            }
            sum = 0;
            for (long r : runs) sum += r;
            System.out.println(1.0d * sum / n);

            System.out.println("Time merging with buffers 9 to 9: avg after " + n);
            for (int i = 0; i < n; i++) {
                RTree rTree1 = new RTree(dim, M, m);
                rTree1.STRpacking(Arrays.copyOfRange(records, 0, N/9));
                long partsum = 0;
                for (int r = 1; r < 9; r++) {
                    RTree rTree2 = new RTree(dim, M, m);
                    rTree2.STRpacking(Arrays.copyOfRange(records, N/9 * r, N/9 * (r + 1)));
                    long t0 = System.currentTimeMillis();
                    rTree1 = rTree1.mergingWithBuffers(rTree2);
                    partsum += System.currentTimeMillis() - t0;
                }

                runs[i] = partsum;
            }
            sum = 0;
            for (long r : runs) sum += r;
            System.out.println(1.0d * sum / n);

            System.out.println("Time Seeded Clustering 9 to 9: avg after " + n);
            for (int i = 0; i < n; i++) {
                RTree rTree1 = new RTree(dim, M, m);
                rTree1.STRpacking(Arrays.copyOfRange(records, 0, N/9));
                long partsum = 0;
                for (int r = 1; r < 9; r++) {
                    long t0 = System.currentTimeMillis();
                    rTree1.SeededClustering(3, 16, Arrays.copyOfRange(records, N/9 * r, N/9 * (r + 1)));
                    partsum += System.currentTimeMillis() - t0;
                }
                runs[i] = partsum;
            }
            sum = 0;
            for (long r : runs) sum += r;
            System.out.println(1.0d * sum / n);
        }
    }
}
