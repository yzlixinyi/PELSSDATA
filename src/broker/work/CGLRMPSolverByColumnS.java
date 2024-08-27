package broker.work;

import broker.*;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import lombok.Setter;
import problem.RMEquipment;
import problem.RMProblem;
import problem.RMSolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static broker.Tools.getSchemeSum;

@Setter
public class CGLRMPSolverByColumnS {
    /**
     * 计算耗时(ms)
     */
    private int CAL_TIME = 0;

    private Model model;

    private RMSolution solution;

    private final CGNode node;

    private Map<RMEquipment, List<CGScheme>> addedSchemeMap = new HashMap<>();

    public CGLRMPSolverByColumnS(CGNode _node) {
        node = _node;
    }

    public void init(RMProblem problem) throws IloException {
        long algoStartTime = System.currentTimeMillis();
        model = new Model(problem);

        // create an objective
        model.initMaxObj();

        // incomplete constraints
        model.initConvexityU();

        model.initConsYSatisfyNumZ();

        model.initBranchNSplits(node);
        model.addConsBranchMPZ(node);

        CAL_TIME += (int) (System.currentTimeMillis() - algoStartTime);
    }

    public RMSolution solve() throws IloException {
        long algoStartTime = System.currentTimeMillis();

        for (var entry : node.getEquipWorkLeaseSchemeMap().entrySet()) {
            int i = entry.getKey().getIndex();
            List<CGScheme> newSchemes = new ArrayList<>(entry.getValue());
            model.initU(i, newSchemes.size());
            int nU = 0;
            if (addedSchemeMap.containsKey(entry.getKey())) {
                nU = addedSchemeMap.get(entry.getKey()).size();
                newSchemes.removeAll(addedSchemeMap.get(entry.getKey()));
            }
            for (CGScheme scheme : newSchemes) {
                model.addColumnWorkLeases((CGSchemeWorkLeases) scheme, i, nU);
                nU++;
            }
            addedSchemeMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        Analysis analysis = model.solveByColumn();

        // get solution if exists
        if (analysis.solutionStatus != IloCplex.Status.Optimal) {
            model.exportModel("RMP-CGS");
            terminate();
        } else {
            solution = model.getMasterProblemSolutionS();
            completeSolution();
        }

        CAL_TIME += (int) (System.currentTimeMillis() - algoStartTime);

        return solution;
    }

    public void terminate() {
        model.end();
    }

    /**
     * 补齐TB_ik, TE_ik, TD_ikn, Gamma_ikn, Y_ijt, X_ijw, S_ijj', HB_iw, HE_iw变量值
     */
    private void completeSolution() {
        double[][] U = solution.getL_is();
        double[][][] Y_ijt = solution.getY_ijt();
        double[][][] X_ijw = solution.getX_ijw();
        double[][][] S_ijj = solution.getS_ijj();
        double[][] HB_iw = solution.getHB_iw();
        double[][] HE_iw = solution.getHE_iw();
        double[][] TB_ik = solution.getTB_ik();
        double[][] TE_ik = solution.getTE_ik();
        double[][][] TD_ikn = solution.getTD_ikn();
        double[][][] Gamma_ikn = solution.getGamma_ikn();

        for (var entry : node.getEquipWorkLeaseSchemeMap().entrySet()) {
            int i = entry.getKey().getIndex();
            for (int s = 0; s < U[i].length; s++) {
                if (!Tools.nonNegative(U[i][s])) {
                    continue;
                }
                CGSchemeWorkLeases scheme = (CGSchemeWorkLeases) entry.getValue().get(s);
                scheme.completeVariables();
                for (int j = 0; j < scheme.getY_jt().length; j++) {
                    getSchemeSum(Y_ijt[i][j], U[i][s], scheme.getY_jt()[j]);
                    getSchemeSum(X_ijw[i][j], U[i][s], scheme.getX_jw()[j]);
                    getSchemeSum(S_ijj[i][j], U[i][s], scheme.getS_jj()[j]);
                }
                getSchemeSum(HB_iw[i], U[i][s], scheme.getHB_w());
                getSchemeSum(HE_iw[i], U[i][s], scheme.getHE_w());
                for (int k = 0; k < TB_ik[i].length; k++) {
                    getSchemeSum(TD_ikn[i][k], U[i][s], scheme.getTD_kn()[k]);
                    getSchemeSum(Gamma_ikn[i][k], U[i][s], scheme.getGamma_kn()[k]);
                }
                getSchemeSum(TB_ik[i], U[i][s], scheme.getTB_k());
                getSchemeSum(TE_ik[i], U[i][s], scheme.getTE_k());
            }
        }
        Tools.smooth(Y_ijt);
        Tools.smooth(X_ijw);
        Tools.smooth(S_ijj);
        Tools.smooth(HB_iw);
        Tools.smooth(HE_iw);
        Tools.smooth(TB_ik);
        Tools.smooth(TE_ik);
        Tools.smooth(TD_ikn);
        Tools.smooth(Gamma_ikn);
    }

    public int getCalculationTime() {
        return CAL_TIME;
    }

}
