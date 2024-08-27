package broker;

import problem.RMPara;
import problem.RMProblem;
import problem.RMSegmentFunction;
import problem.RMSolution;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CaseIO {

    private static final String SOLUTION = "solution/";

    private CaseIO() {
    }

    public static void writeCase(RMProblem problem, String path) {
        List<String> caseInfo = problem.collectCaseInfo();
        String casePath = path + "case/";
        WriteText.createFolder(casePath);
        String fileName = casePath + problem.getName() + "_case.txt";
        WriteText.writeDataList(caseInfo, fileName);
    }

    public static void writeSolution(RMProblem problem, String path, boolean print) {
        List<String> solutionInfo = problem.collectSolutionInfo();
        String solutionPath = path + SOLUTION;
        WriteText.createFolder(solutionPath);
        String fileName = solutionPath + problem.getName() + "_solution.txt";
        WriteText.writeDataList(solutionInfo, fileName);
        if (print) {
            solutionInfo.forEach(System.out::println);
        }
    }

    public static void writeFeasibleSolution(RMSolution solution, RMProblem problem, String path) {
        problem.translateSolution(solution);
        List<String> solutionInfo = problem.collectSolutionInfo();
        WriteText.createFolder(path);
        String fileName = "%s%s_[%d].txt".formatted(path, problem.getName(), problem.getSolutionInt().getObj());
        WriteText.writeDataList(solutionInfo, fileName);
    }

    public static void writePara(RMPara para, String path) throws IOException {
        WriteText.createTxtFile("para", path);
        WriteText.appendTxtLine(para.toString());
    }

    public static Map<String, List<Integer>> readRent(String path) throws IOException {
        Map<String, List<Integer>> typeRentMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] seq = line.split("\\s");
                if (seq.length < 2) {
                    continue;
                }
                String type = seq[0];
                int rent = Integer.parseInt(seq[1]);
                if (typeRentMap.containsKey(type)) {
                    typeRentMap.get(type).add(rent);
                } else {
                    List<Integer> rents = new ArrayList<>();
                    rents.add(rent);
                    typeRentMap.put(type, rents);
                }
            }
        }
        return typeRentMap;
    }

    public static void writeGSF(List<RMSegmentFunction> gList, String path, String caseName) throws IOException {
        for (RMSegmentFunction gsf : gList) {
            WriteText.createTxtFile(gsf.getId() + "_" + gsf.getEquipmentType(), path + caseName + "/");
            double[] slopes = gsf.getSlopes();
            double[] intercepts = gsf.getIntercepts();
            double[] breakPoints = gsf.getBreakPoints();
            WriteText.appendTxtLine("N\t" + gsf.getNSegment());
            for (int n = 0; n < gsf.getNSegment(); n++) {
                WriteText.appendTxtLine("%d\t%s\t%s\t%s".formatted(n + 1,
                        Tools.df1.format(slopes[n]), Tools.df1.format(intercepts[n]), Tools.df1.format(breakPoints[n] - 0.5)));
            }
        }
    }
}
