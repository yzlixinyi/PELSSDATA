package broker;

import problem.RMProblem;
import problem.RMSegmentFunction;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

public class Main {

    /**
     * T >=15, {2, 3, 4}; T < 15, {2, 3}
     */
    static final int N_SEGMENT = 3;

    static final int N_REPLICATION = 3;

    static final int MAP_SCALE = 100;

    static final int RND_SEED_FINAL = 1018;

    static final int[][] scalesT = new int[][]{
            {7, 5, 5, 1},
            {7, 5, 10, 1},
            {7, 10, 5, 1},
            {7, 10, 10, 1},
            {10, 10, 10, 1},
            {10, 10, 15, 1},
            {10, 15, 10, 1},
            {10, 15, 15, 1},
            {15, 20, 20, 30},
            {15, 20, 30, 30},
            {30, 40, 40, 30},
            {30, 40, 50, 30},
    };

    public static void main(String[] args) throws IOException {

        // 5.2 performance tests
        for (int[] scale : scalesT) {
            testSegmentFunction(scale[0], scale[1], scale[2], scale[3]);
            testSegmentFunction(scale[0], scale[1], scale[2], scale[3]);
            testSegmentFunction(scale[0], scale[1], scale[2], scale[3]);
        }

        // 5.3 benefit of split service
        int[] unitScheduleCosts = new int[]{0, 1, 10, 50, 100, 200, 400, 500, 600, 700, 800, 900, 1000, 1200, 1400, 1600, 1800, 2000, 2500, 3000};
        testSplit(new int[]{15, 20, 20}, unitScheduleCosts, false);
        testSplit(new int[]{15, 20, 20}, unitScheduleCosts, true);
        testSplit(new int[]{30, 40, 40}, unitScheduleCosts, false);
        testSplit(new int[]{30, 40, 40}, unitScheduleCosts, true);

        // 5.4 number of segments and splits
        int[] nSegments = new int[]{1, 2, 3, 4, 6, 12};
        writePieces(12, 15, nSegments);
        for (int tD = 1; tD < 12; tD++) {
            testNPieces(12, 15, nSegments, tD);
        }

    }

    private static void writePieces(int T, int J, int[] nSegments) throws IOException {
        String path = "./data/testNPiece/%s/".formatted(fileTimeStamp());
        WriteText.createFolder(path);
        for (int c = 0; c < N_REPLICATION; c++) {
            for (int nSegment : nSegments) {
                CaseRandom generator = new CaseRandom(T, -1, J, -1, nSegment);
                generator.setSeed(RND_SEED_FINAL);
                List<RMSegmentFunction> gList = generator.replicateGByDayMonth(c).getRentFunctions();
                CaseIO.writeGSF(gList, path, generator.getTIJSeed(c));
            }
        }
    }

    private static void testNPieces(int T, int J, int[] nSegments, int tD) throws IOException {
        String path = "./data/testNPiece/%s_tD%d/".formatted(fileTimeStamp(), tD);
        WriteText.createFolder(path);
        for (int nSegment : nSegments) {
            CaseRandom generator = new CaseRandom(T, -1, J, -1, nSegment);
            generator.setMapScale(10);
            generator.setSeed(RND_SEED_FINAL);
            generator.setTimeLimit(1);
            for (int c = 0; c < N_REPLICATION; c++) {
                RMProblem problem = generator.replicateTDHetero(c, tD);
                CaseIO.writeCase(problem, path);
            }
        }
    }

    private static void testSplit(int[] scale, int[] mapScales, boolean split) throws IOException {
        for (int mapScale : mapScales) {
            String path = "./data/testSplit/%s_split%d_C%d/".formatted(fileTimeStamp(), split ? 1 : 0, mapScale);
            WriteText.createFolder(path);
            CaseRandom generator = new CaseRandom(scale[0], scale[1], scale[2], -1, N_SEGMENT);
            generator.setMapScale(mapScale);
            generator.setSeed(RND_SEED_FINAL);
            generator.setTimeLimit(1);
            CaseIO.writePara(generator.para, path);
            for (int c = 0; c < N_REPLICATION; c++) {
                RMProblem problem = generator.replicate1(c);
                CaseIO.writeCase(problem, path);
            }
        }
    }

    private static void testSegmentFunction(int T, int I, int J, double timeLimit) throws IOException {
        String path = "./data/%s/".formatted(fileTimeStamp());
        WriteText.createFolder(path);

        CaseRandom generator = new CaseRandom(T, I, J, -1, N_SEGMENT);
        generator.setMapScale(MAP_SCALE);
        generator.setSeed(RND_SEED_FINAL);
        generator.setTimeLimit(timeLimit);
        String scaleString = generator.getScale();
        System.out.println(scaleString);
        for (int c = 0; c < N_REPLICATION; c++) {
            RMProblem problem = generator.replicate1(c);
            CaseIO.writeCase(problem, path);
        }
    }

    private static String fileTimeStamp() {
        Calendar calendar = Calendar.getInstance(); // get current instance of the calendar
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss");
        return formatter.format(calendar.getTime());
    }

}
