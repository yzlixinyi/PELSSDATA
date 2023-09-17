package broker;

import problem.RMPara;
import problem.RMProblem;
import problem.RMSegmentFunction;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CaseIO {

    private CaseIO() {
    }

    public static void writeCase(RMProblem problem, String path) throws IOException {
        List<String> caseInfo = problem.collectCaseInfo();
        String casePath = path + "case/";
        WriteText.createFolder(casePath);
        String fileName = casePath + problem.getName() + "_case.txt";
        WriteText.writeDataList(caseInfo, fileName);
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
            DecimalFormat df1 = new DecimalFormat("0.0");
            for (int n = 0; n < gsf.getNSegment(); n++) {
                WriteText.appendTxtLine("%d\t%s\t%s\t%s".formatted(n + 1,
                        df1.format(slopes[n]), df1.format(intercepts[n]), df1.format(breakPoints[n] - 0.5)));
            }
        }
    }
}
