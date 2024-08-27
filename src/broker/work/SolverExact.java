package broker.work;

import broker.Analysis;
import broker.Model;
import broker.Tools;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import lombok.Getter;
import lombok.Setter;
import problem.RMProblem;
import problem.RMSolution;

@Getter
@Setter
public class SolverExact {
    /**
     * 计算耗时(ms)
     */
    private int CAL_TIME_MS = 0;

    private Analysis analysis;

    private RMSolution solution;

    private RestrainLevel restrict = RestrainLevel.SEQ_END;

    private boolean linearRelaxation;

    private boolean forbidSplit;

    private boolean reportScale;

    private boolean saveFeasible;

    private String numVarCons;

    private String recordPath;

    public RMSolution solve(RMProblem rmp) throws IloException {
        long algoStartTime = System.currentTimeMillis();

        Model model = new Model(rmp);

        if (linearRelaxation) {
            model.defineNumZ();
            model.defineNumXYS();
            model.defineNumV();
            model.defineNumWorkOrder();
            model.defineNumLeaseTerm();
        } else {
            model.defineIntZ();
            model.defineIntXY();
            model.defineIntS();
            model.defineIntV();
            model.defineIntWorkOrder();
            model.defineIntLeaseTerm();
        }

        if (rmp.getPara().isAllowReVisit()) {
            model.defineIntL();
            model.addConsLUnify();
            model.addConsXSatisfyL();
            model.addConsXSumJ();
            model.addConsXSeqH();
            model.addConsHBEL();
            model.addConsHL();
            model.addConsFixedJL();
        } else {
            model.addConsYUnify();
            model.addConsXSatisfyY();
            model.addConsXSum();
            model.addConsHBEXY();
            model.addConsHXY();
            model.addConsFixedJ();
        }
        model.addConsFulfilment();
        model.addConsHBEX();
        model.addConsXSeq();
        model.addConsVXSum();
        model.addConsVXSeq();
        if (restrict == RestrainLevel.SEQ) {
            model.addConsHBE();
            model.addConsTBE();
        } else if (restrict == RestrainLevel.SEQ_END) {
            model.addConsHBE();
            model.addConsTBE();
            model.addConsHEnd();
            model.addConsTEnd();
        } else if (restrict == RestrainLevel.SEQ_VALID) {
            model.addConsInterH();
            model.addConsInterT();
        } else if (restrict == RestrainLevel.SEQ_VALID_END) {
            model.addConsInterH();
            model.addConsInterT();
            model.addConsHEnd();
            model.addConsTEnd();
        }
        if (rmp.getPara().isForbidSplits()) {
            model.addConsForbidSplit();
        }

        model.addConsTVH();
        model.addConsTDBE();
        model.addConsGamma();
        model.addConsGammaSeq();

        if (reportScale) {
            numVarCons = model.getVarConsNumber();
        } else {
            analysis = model.solveWithCallback(recordPath, saveFeasible);

            // get solution if exists
            if (analysis.solutionStatus != IloCplex.Status.Feasible && analysis.solutionStatus != IloCplex.Status.Optimal) {
                model.exportModel("ExactModel");
            } else {
                solution = model.getSolution();
            }
        }
        model.end();

        CAL_TIME_MS = (int) (System.currentTimeMillis() - algoStartTime);

        return solution;
    }

    /**
     * @return "ob1(df1), gap(df6), t(ms)"
     */
    public String getSolverReport() {
        return String.format("%s\t%s\t%d\t%s",
                Tools.df1.format(solution == null ? -1001 : solution.getObj()), Tools.df6.format(analysis.getGAP()), CAL_TIME_MS, Tools.df3.format(analysis.getT_I()));
    }

    public void recordProcess(String path, boolean saveFeasibleSolution) {
        recordPath = path + getClass().getSimpleName().substring(6) + '/';
        saveFeasible = saveFeasibleSolution;
    }

    public enum RestrainLevel {
        SEQ,
        SEQ_END,
        SEQ_VALID,
        SEQ_VALID_END,
    }
}
