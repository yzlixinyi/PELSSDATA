package broker;

import broker.work.SolverCG;
import broker.work.SolverCGS;
import broker.work.SolverExact;
import ilog.concert.IloException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import problem.RMEquipment;
import problem.RMProblem;
import problem.RMSegmentFunction;
import problem.RMSolution;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static broker.Tools.fileTimeStamp;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    enum SolverType {
        CPLEX,
        CG,
        CGS
    }

    enum FixedCostType {
        SWITCH,
        SETUP,
    }

    static final int N_SEGMENT = 3;

    static final int N_REPLICATION = 3;

    static final int MAP_SCALE = 100;

    static final int RND_SEED_FINAL = 1018;

    static final int[][] scalesT = new int[][]{
            {7, 5, 5},
            {7, 5, 10},
            {7, 5, 15},
            {7, 10, 10},
            {7, 10, 15},
            {7, 10, 20},
            {10, 10, 10},
            {10, 10, 15},
            {10, 10, 20},
            {10, 15, 15},
            {10, 15, 20},
            {10, 15, 30},
            {10, 20, 20},
            {10, 20, 30},
            {15, 20, 20},
            {15, 20, 30},
            {30, 40, 40},
            {30, 40, 50},
    };

    static RentData rentData;

    public static void main(String[] args) throws IloException, IOException {

        rentData = new RentData();

        // 5.2 performance tests
        saveCaseAndVarConsNum();
        testPerformance(SolverType.CGS);
        testPerformance(SolverType.CG);
        testPerformance(SolverType.CPLEX);
        // termination at root node
        testRootNote(SolverType.CGS);
        testRootNote(SolverType.CG);

        // 5.3 benefit of split service
        int[] unitScheduleCosts = new int[]{0, 1, 10, 50, 100, 200, 400, 500, 600, 700, 800, 900, 1000, 1200, 1400, 1600, 1800, 2000, 2500, 3000};
        testSplit(new int[]{15, 20, 20, -1}, unitScheduleCosts, 0);
        testSplit(new int[]{15, 20, 20, -1}, unitScheduleCosts, 1);
        testSplit(new int[]{15, 20, 20, -1}, unitScheduleCosts, 2); // Appendix B3: allowing revisits
        unitScheduleCosts = Arrays.copyOf(unitScheduleCosts, 16);
        testSplit(new int[]{30, 40, 40, -1}, unitScheduleCosts, 0);
        testSplit(new int[]{30, 40, 40, -1}, unitScheduleCosts, 1);
        testSplit(new int[]{30, 40, 40, -1}, unitScheduleCosts, 2); // Appendix B3: allowing revisits

        // 5.4 number of segments and splits
        int[] nSegments = new int[]{1, 2, 3, 4, 6, 12};
        writePieces(12, 15, nSegments);
        for (int tD = 1; tD < 12; tD++) {
            testNPieces(12, 15, nSegments, tD);
        }

        // Appendix B1: flexible time frames
        int[] flexTestScale = new int[]{10, 10, 10};
        int[][] flexPara = new int[][]{{1, 10, 10}, {2, 10, 10}};
        for (int[] fp : flexPara) {
            writeFlexCase(flexTestScale, fp[0], fp[1], fp[2]);
            testFlexible(SolverType.CGS, flexTestScale, fp[0], fp[1], fp[2]);
        }
        // validation of model
        testFlexible(SolverType.CGS, scalesT[0], 1, 5, 5);
        testFlexible(SolverType.CPLEX, scalesT[0], 1, 5, 5);

        // Appendix B2: setup or switch costs
        int[] fixedCostTestScale = new int[]{15, 20, 20};
        int[] costLevel = new int[]{0, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1200, 1400, 1600, 1800, 2000};
        testSetupCost(fixedCostTestScale, FixedCostType.SETUP, costLevel);
        testSetupCost(fixedCostTestScale, FixedCostType.SWITCH, costLevel);
    }

    private static void writePieces(int T, int J, int[] nSegments) throws IOException {
        String path = "./data/testNPiece/%s/".formatted(fileTimeStamp());
        WriteText.createFolder(path);
        for (int c = 0; c < N_REPLICATION; c++) {
            for (int nSegment : nSegments) {
                CaseRandom generator = new CaseRandom(T, -1, J, nSegment);
                generator.setSeed(RND_SEED_FINAL);
                generator.setRentData(rentData);
                List<RMSegmentFunction> gList = generator.replicateGByDayMonth(c).getRentFunctions();
                CaseIO.writeGSF(gList, path, generator.getTIJSeed(c));
            }
        }
    }

    private static void testNPieces(int T, int J, int[] nSegments, int tD) throws IOException, IloException {
        String folder = "./data/testNPiece/";
        String path = "%s%s_tD%d/".formatted(folder, fileTimeStamp(), tD);
        WriteText.createFolder(path);
        for (int nSegment : nSegments) {
            CaseRandom generator = new CaseRandom(T, -1, J, nSegment);
            generator.setMapScale(10);
            generator.setSeed(RND_SEED_FINAL);
            generator.setRentData(rentData);
            CaseIO.writePara(generator.para, path);
            for (int c = 0; c < N_REPLICATION; c++) {
                RMProblem problem = generator.replicateTDHetero(c, tD);
                CaseIO.writeCase(problem, path);

                SolverCGS cgs = new SolverCGS();
                cgs.writeFeasibleSolution(path);
                RMSolution solutionCGW = cgs.solve(problem);
                problem.translateSolution(solutionCGW);
                CaseIO.writeSolution(problem, path, true);

                WriteText.createTxtFile("report", path);
                String nLine = "%d\t%d\t%d\t%s\t%s".formatted(nSegment, c + 1, problem.getEquipments().size(),
                        cgs.getSimpleReport(), problem.getReport());
                WriteText.appendTxtLine(nLine);
                WriteText.createTxtFile("summary", folder);
                WriteText.appendTxtLine("%d\t%s".formatted(tD, nLine));
            }
        }
    }

    private static void testSplit(int[] scale, int[] mapScales, int revisit) throws IOException, IloException {
        String folder = "./data/testSplit/";
        String summaryTitle = "T%d_revisit%d".formatted(scale[0], revisit);
        for (int mapScale : mapScales) {
            String path = "%s%s_revisit%d_C%d/".formatted(folder, fileTimeStamp(), revisit, mapScale);
            WriteText.createFolder(path);
            CaseRandom generator = new CaseRandom(scale[0], scale[1], scale[2], N_SEGMENT);
            generator.setMapScale(mapScale);
            generator.setSeed(RND_SEED_FINAL);
            generator.setRentData(rentData);
            if (scale.length > 3 && scale[3] > 0) generator.setTimeLimit(scale[3]);
            generator.setRevisit(revisit);
            for (int c = 0; c < N_REPLICATION; c++) {
                RMProblem problem = generator.replicate1(c);
                CaseIO.writeCase(problem, path);

                SolverCGS cgs = new SolverCGS();
                cgs.writeFeasibleSolution(path);
                RMSolution solutionCGW = cgs.solve(problem);
                problem.translateSolution(solutionCGW);
                CaseIO.writeSolution(problem, path, true);

                WriteText.createTxtFile("simpleReport", path);
                String line = "%s\t%d\t%s\t%s".formatted(Arrays.toString(scale), c + 1,
                        cgs.getSimpleReport(), problem.getReport());
                WriteText.appendTxtLine(line);
                WriteText.createTxtFile(summaryTitle, folder);
                WriteText.appendTxtLine("%d\t%s".formatted(mapScale, line));
            }
            CaseIO.writePara(generator.para, path);
        }
    }

    private static void testRootNote(SolverType solver) throws IOException {
        String path = "./data/testRootNode/%s/%s/".formatted(solver.toString(), fileTimeStamp());
        WriteText.createFolder(path);

        for (int[] scale : scalesT) {
            CaseRandom generator = new CaseRandom(scale[0], scale[1], scale[2], N_SEGMENT);
            generator.setMapScale(MAP_SCALE);
            generator.setSeed(RND_SEED_FINAL);
            generator.setRentData(rentData);
            generator.setRootNodeEnd();
            String scaleString = generator.getScale();
            System.out.println(scaleString);
            CaseIO.writePara(generator.para, path);
            for (int c = 0; c < N_REPLICATION; c++) {
                RMProblem problem = generator.replicate1(c);
                CaseIO.writeCase(problem, path);
                StringBuilder sb = new StringBuilder();
                sb.append(scaleString).append("\t").append(c + 1);

                try {
                    if (solver == SolverType.CGS) {
                        SolverCGS cgs = new SolverCGS();
                        cgs.solve(problem);
                        sb.append("\t").append(cgs.getDetailReport());
                    } else if (solver == SolverType.CG) {
                        SolverCG cgw = new SolverCG();
                        cgw.solve(problem);
                        sb.append("\t").append(cgw.getDetailReport());
                    }
                } catch (IloException e) {
                    logger.error(e);
                }

                System.out.println(sb);
                WriteText.createTxtFile(solver.toString(), path);
                WriteText.appendTxtLine(sb.toString());
            }
        }
    }

    private static void saveCaseAndVarConsNum() throws IOException {
        String path = "./data/CPLEX/%s/".formatted(fileTimeStamp());
        WriteText.createFolder(path);
        for (int[] scale : scalesT) {
            String head = "T%dI%dJ%d".formatted(scale[0], scale[1], scale[2]);
            CaseRandom generator = new CaseRandom(scale[0], scale[1], scale[2], N_SEGMENT);
            generator.setMapScale(MAP_SCALE);
            generator.setSeed(RND_SEED_FINAL);
            generator.setRentData(rentData);
            CaseIO.writePara(generator.para, path);
            for (int c = 0; c < N_REPLICATION; c++) {
                RMProblem problem = generator.replicate1(c);
                CaseIO.writeCase(problem, path);

                StringBuilder sb = new StringBuilder();
                sb.append(head).append("\t").append(c + 1);
                try {
                    SolverExact cplex = new SolverExact();
                    cplex.setReportScale(true);
                    cplex.solve(problem);
                    sb.append("\t").append(cplex.getNumVarCons());
                } catch (IloException e) {
                    logger.error(e);
                }
                sb.append("\t").append(Tools.df1.format(problem.getEquipments().stream().mapToDouble(RMEquipment::getAvgNTimeBorder).sum()));
                sb.append("\t").append(Arrays.stream(problem.getMIs()).sum());
                System.out.println(sb);
                WriteText.createTxtFile("scale", path);
                WriteText.appendTxtLine(sb.toString());
            }
        }
    }

    private static void testSetupCost(int[] scale, FixedCostType type, int[] costLevel) throws IOException, IloException {
        String folder = "./data/test%s/".formatted(type.toString());
        String summaryTitle = "%s_%s".formatted(fileTimeStamp(), Arrays.toString(scale));
        for (int cost : costLevel) {
            String path = "%s%s_%d/".formatted(folder, fileTimeStamp(), cost);
            WriteText.createFolder(path);
            CaseRandom generator = new CaseRandom(scale[0], scale[1], scale[2], N_SEGMENT);
            generator.setMapScale(MAP_SCALE);
            generator.setSeed(RND_SEED_FINAL);
            generator.setRentData(rentData);
            if (type.equals(FixedCostType.SETUP)) {
                generator.setSetupCost(cost);
            } else {
                generator.setSwitchCost(cost);
            }
            CaseIO.writePara(generator.para, path);
            for (int c = 0; c < N_REPLICATION; c++) {
                RMProblem problem = generator.replicate1(c);

                SolverCGS cgs = new SolverCGS();
                cgs.writeFeasibleSolution(path);
                RMSolution solution = cgs.solve(problem);
                problem.translateSolution(solution);
                CaseIO.writeSolution(problem, path, true);
                String line = "%d\t%s\t%d\t%s\t%s%s".formatted(c + 1, cgs.getSimpleReport(), problem.getNR1(), problem.getReport(),
                        cgs.getExploreReport(), problem.reportBranch(7));

                WriteText.createTxtFile("summary", path);
                WriteText.appendTxtLine(line);
                WriteText.createTxtFile(summaryTitle, folder);
                WriteText.appendTxtLine("%d\t%s".formatted(cost, line));
            }
        }
    }

    private static void testFlexible(SolverType type, int[] scale, int flexLevel, int divisor, int nRemainder) throws IOException, IloException {
        String folder = "./data/testFlex/%S/".formatted(type.toString());
        String summaryTitle = "%s_%s_%d_%d_%d".formatted(fileTimeStamp(), Arrays.toString(scale), flexLevel, divisor, nRemainder);
        for (int nRmd = 1; nRmd <= nRemainder; nRmd++) {
            String path = "%s%s_F%d_D%d_N%d/".formatted(folder, fileTimeStamp(), flexLevel, divisor, nRmd);
            WriteText.createFolder(path);
            CaseRandom generator = new CaseRandom(scale[0], scale[1], scale[2], N_SEGMENT);
            generator.setMapScale(MAP_SCALE);
            generator.setSeed(RND_SEED_FINAL);
            generator.setRentData(rentData);
            generator.setFlexible(flexLevel, divisor, nRmd);
            CaseIO.writePara(generator.para, path);
            for (int c = 0; c < N_REPLICATION; c++) {
                RMProblem problem = generator.replicate1(c);

                String line = "%d\t%d\t%d\t".formatted(c + 1, generator.getNFlex(), problem.getPara().getNCusOrder());
                if (type == SolverType.CPLEX) {
                    SolverExact cplex = new SolverExact();
                    cplex.recordProcess(path, true);
                    RMSolution solution = cplex.solve(problem);
                    problem.translateSolution(solution);
                    line += cplex.getSolverReport();
                } else {
                    SolverCGS cgs = new SolverCGS();
                    cgs.writeFeasibleSolution(path);
                    RMSolution solutionCGW = cgs.solve(problem);
                    problem.translateSolution(solutionCGW);
                    line += "%s\t%s\t%s%s".formatted(cgs.getSimpleReport(), problem.getReport(),
                            cgs.getExploreReport(), problem.reportBranch(7));
                }
                CaseIO.writeSolution(problem, path, true);

                WriteText.createTxtFile("summary", path);
                WriteText.appendTxtLine(line);
                WriteText.createTxtFile(summaryTitle, folder);
                WriteText.appendTxtLine("%d\t%d\t%d\t%s".formatted(flexLevel, divisor, nRmd, line));
            }
        }
    }

    private static void writeFlexCase(int[] scale, int flexLevel, int divisor, int nRemainder) throws IOException {
        String folder = "./data/testFlex/case/";
        String summaryTitle = fileTimeStamp() + "_" + Arrays.toString(scale);
        for (int nRmd = 1; nRmd <= nRemainder; nRmd++) {
            String path = "%s%s_F%d_D%d_N%d/".formatted(folder, fileTimeStamp(), flexLevel, divisor, nRmd);
            WriteText.createFolder(path);
            CaseRandom generator = new CaseRandom(scale[0], scale[1], scale[2], N_SEGMENT);
            generator.setMapScale(MAP_SCALE);
            generator.setSeed(RND_SEED_FINAL);
            generator.setRentData(rentData);
            generator.setFlexible(flexLevel, divisor, nRmd);
            CaseIO.writePara(generator.para, path);
            for (int c = 0; c < N_REPLICATION; c++) {
                RMProblem problem = generator.replicate1(c);
                CaseIO.writeCase(problem, path);

                String line = "%d\t%d\t%d".formatted(c + 1, generator.getNFlex(), problem.getPara().getNCusOrder());
                WriteText.createTxtFile(summaryTitle, folder);
                WriteText.appendTxtLine("%d\t%d\t%d\t%s".formatted(flexLevel, divisor, nRmd, line));
            }
        }
    }

    private static void testPerformance(SolverType solver) throws IOException {
        String path = "./data/%s/%s/".formatted(solver.toString(), fileTimeStamp());
        WriteText.createFolder(path);

        for (int[] scale : scalesT) {
            CaseRandom generator = new CaseRandom(scale[0], scale[1], scale[2], N_SEGMENT);
            generator.setMapScale(MAP_SCALE);
            generator.setSeed(RND_SEED_FINAL);
            generator.setRentData(rentData);
            String scaleString = generator.getScale();
            System.out.println(scaleString);
            CaseIO.writePara(generator.para, path);
            for (int c = 0; c < N_REPLICATION; c++) {
                RMProblem problem = generator.replicate1(c);

                String recordLine = "%s_%d\t".formatted(scaleString, c + 1);
                try {
                    switch (solver) {
                        case CPLEX -> {
                            SolverExact cplex = new SolverExact();
                            cplex.recordProcess(path, true);
                            RMSolution solution = cplex.solve(problem);
                            problem.translateSolution(solution);
                            CaseIO.writeSolution(problem, path, false);
                            recordLine += cplex.getSolverReport();
                        }
                        case CGS -> {
                            SolverCGS cgs = new SolverCGS();
                            cgs.writeFeasibleSolution(path);
                            cgs.writeBranchDecisions(path);
                            RMSolution solutionCGW = cgs.solve(problem);
                            problem.translateSolution(solutionCGW);
                            CaseIO.writeSolution(problem, path, true);
                            recordLine += (cgs.getDetailReport() + problem.reportBranch(7));
                        }
                        case CG -> {
                            SolverCG cgw = new SolverCG();
                            cgw.writeFeasibleSolution(path);
                            cgw.writeBranchDecisions(path);
                            RMSolution solutionCGW = cgw.solve(problem);
                            problem.translateSolution(solutionCGW);
                            CaseIO.writeSolution(problem, path, true);
                            recordLine += (cgw.getDetailReport() + problem.reportBranch(-1));
                        }
                    }
                } catch (IloException e) {
                    logger.error(e);
                }

                System.out.println(recordLine);
                WriteText.createTxtFile(solver.toString(), path);
                WriteText.appendTxtLine(recordLine);
            }
        }
    }

}
