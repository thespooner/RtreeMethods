package Rtree;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Stack;

public class Visualization extends JComponent{
    private static final int RECT_X = 400;
    private static final int RECT_Y = RECT_X;
    private static final int RECT_WIDTH = 400;
    private static final int RECT_HEIGHT = RECT_WIDTH;
    final static int dimentions = 2;

    static ArrayList<MBR> mbrList = new ArrayList<MBR>();
    static Stack<ArrayList<MBR>> stack = new Stack<ArrayList<MBR>>();

    @Override
    protected void paintComponent(Graphics g) {
        int i = 0;
        super.paintComponent(g);
        while (!stack.isEmpty()) {
            i++;
            mbrList = stack.pop();
            g.setColor(iterateColor(i));
            for (MBR mbr : mbrList) {
                g.drawRect((int) mbr.low[0] * 20, (int) mbr.low[1] * 20,
                        (int) (mbr.high[0] * 20 - mbr.low[0] * 20), (int) (mbr.high[1] * 20 - mbr.low[1] * 20));
            }
        }
        //g.drawRect(RECT_X, RECT_Y, RECT_WIDTH, RECT_HEIGHT);
        //g.setColor(Color.red);
        //g.drawRect(RECT_X +1 , RECT_Y+2, RECT_WIDTH+2, RECT_HEIGHT+2);
    }
    private Color iterateColor(int i){
        switch (i%6){
            case 0:
                return Color.red;
            case 1:
                return Color.blue;
            case 2:
                return Color.green;
            case 3:
                return Color.gray;
            case 4:
                return Color.black;
            case 5:
                return Color.yellow;
        }
        return Color.red;
    }

    @Override
    public Dimension getPreferredSize() {
        // so that our GUI is big enough
        return new Dimension(RECT_WIDTH + 2 * RECT_X, RECT_HEIGHT + 2 * RECT_Y);
    }

    // create the GUI explicitly on the Swing event thread
    private static void createAndShowGui() {
        Visualization viz = new Visualization();

        JFrame frame = new JFrame("Visualization of MBRs");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(viz);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }


    public static void main(String[] args) throws IOException{
        int nRecs = 75;
        float[] low = new float[dimentions];
        float[] high = new float[dimentions];
        RTree rt = new RTree(2,5,2);
        Record[] records = new Record[nRecs];

        for (int i=0;i<nRecs;i++) {
            for(int j = 0; j<dimentions; j++ ){
                Random r1 = new Random();
                Random r2 = new Random();
                low[j] = r1.nextFloat() * (30);
                high[j] = low[j];
            }
            Record record = new Record(new MBR(low,high));
            records[i] = record;
            rt.insertion(record);
        }
        //rt.STRpacking(records);

        int level = rt.root.height;
        for(int i = 0 ; i <= level ; i++ ){
            stack.add(mbrList = rt.getMBRs(i));
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGui();
            }
        });
    }
}
