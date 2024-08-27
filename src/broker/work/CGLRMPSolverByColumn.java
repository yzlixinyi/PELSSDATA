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
public class CGLRMPSolverByColumn {
    /**
     * 计算耗时(ms)
     */
    private int CAL_TIME = 0;

    private Model model;

    private RMSolution solution;

    private final CGNode node;

    private Map<RMEquipment, List<CGScheme>> addedSchemeUMap = new HashMap<>();

    private Map<RMEquipment, List<CGScheme>> addedSchemeLMap = new HashMap<>();

    public CGLRMPSolverByColumn(CGNode _node) {
        node = _node;
    }

    private boolean consMergeNeighborWork;

    private boolean consUL;

    public void init(RMProblem problem) throws IloException {
        long algoStartTime = System.currentTimeMillis();
        model = new Model(problem);

        // create an objective
        model.initMaxObj();

        // incomplete constraints
        model.initConvexity();

        model.initConsYSatisfyNumZ();

        model.initConsVX();
        model.initConsTVH();
        model.initNumV(consMergeNeighborWork);
        model.initBranchNSplits(node);
        model.addConsBranchMPVZ(node);

        consUL = problem.getPara().isUseConsUL();

        CAL_TIME += (int) (System.currentTimeMillis() - algoStartTime);
    }

    public RMSolution solve() throws IloException {
        long algoStartTime = System.currentTimeMillis();

        for (var entry : node.getEquipLeaseTermSchemeMap().entrySet()) {
            int i = entry.getKey().getIndex();
            List<CGScheme> newSchemeU = new ArrayList<>(entry.getValue());
            model.initUConsUL(i, newSchemeU.size());
            int nU = 0;
            if (addedSchemeUMap.containsKey(entry.getKey())) {
                nU = addedSchemeUMap.get(entry.getKey()).size();
                newSchemeU.removeAll(addedSchemeUMap.get(entry.getKey()));
            }
            for (CGScheme schemeU : newSchemeU) {
                model.addColumnLeaseTermsU((CGSchemeLeaseTerms) schemeU, i, nU);
                nU++;
            }
            addedSchemeUMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        for (var entry : node.getEquipWorkOrderSchemeMap().entrySet()) {
            int i = entry.getKey().getIndex();
            List<CGScheme> newSchemeL = new ArrayList<>(entry.getValue());
            model.initL(i, newSchemeL.size());
            int nL = 0;
            if (addedSchemeLMap.containsKey(entry.getKey())) {
                nL = addedSchemeLMap.get(entry.getKey()).size();
                newSchemeL.removeAll(addedSchemeLMap.get(entry.getKey()));
            }
            for (CGScheme schemeL : newSchemeL) {
                model.addColumnWorkOrdersL(consUL ? node.getEquipLeaseTermSchemeMap().get(entry.getKey()) : new ArrayList<>(),
                        (CGSchemeWorkOrders) schemeL, i, nL, consMergeNeighborWork);
                nL++;
            }
            addedSchemeLMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        Analysis analysis = model.solveByColumn();

        // get solution if exists
        if (analysis.solutionStatus != IloCplex.Status.Optimal) {
            model.exportModel("RMP-CG");
            terminate();
        } else {
            solution = model.getMasterProblemSolution();
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
        double[][] L = solution.getL_is();
        double[][] U = solution.getU_is();
        double[][][] Y_ijt = solution.getY_ijt();
        double[][][] X_ijw = solution.getX_ijw();
        double[][][] S_ijj = solution.getS_ijj();
        double[][] HB_iw = solution.getHB_iw();
        double[][] HE_iw = solution.getHE_iw();
        double[][] TB_ik = solution.getTB_ik();
        double[][] TE_ik = solution.getTE_ik();
        double[][][] TD_ikn = solution.getTD_ikn();
        double[][][] Gamma_ikn = solution.getGamma_ikn();

        for (var entry : node.getEquipWorkOrderSchemeMap().entrySet()) {
            int i = entry.getKey().getIndex();
            for (int s = 0; s < L[i].length; s++) {
                CGScheme scheme = entry.getValue().get(s);
                for (int j = 0; j < scheme.getY_jt().length; j++) {
                    getSchemeSum(Y_ijt[i][j], L[i][s], scheme.getY_jt()[j]);
                    getSchemeSum(X_ijw[i][j], L[i][s], scheme.getX_jw()[j]);
                    getSchemeSum(S_ijj[i][j], L[i][s], scheme.getS_jj()[j]);
                }
                getSchemeSum(HB_iw[i], L[i][s], scheme.getHB_w());
                getSchemeSum(HE_iw[i], L[i][s], scheme.getHE_w());
            }
        }
        for (var entry : node.getEquipLeaseTermSchemeMap().entrySet()) {
            int i = entry.getKey().getIndex();
            for (int s = 0; s < U[i].length; s++) {
                CGScheme scheme = entry.getValue().get(s);
                getSchemeSum(TB_ik[i], U[i][s], scheme.getTB_k());
                getSchemeSum(TE_ik[i], U[i][s], scheme.getTE_k());
                for (int k = 0; k < TB_ik[i].length; k++) {
                    getSchemeSum(TD_ikn[i][k], U[i][s], scheme.getTD_kn()[k]);
                    getSchemeSum(Gamma_ikn[i][k], U[i][s], scheme.getGamma_kn()[k]);
                }
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
