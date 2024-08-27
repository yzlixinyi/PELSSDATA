package broker;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import lombok.Getter;
import problem.*;

import java.util.List;

public class CGSPSolverLeaseTerm {

    private RMSolutionL solution;

    private final CGNode node;

    private final int equipmentIndex;

    @Getter
    private PricingStatus pricingStatus;

    private double[] cDualTB_k;
    private double[] cDualTE_k;
    private double mu;

    /**
     * 子问题列生成
     *
     * @param equipIdx 子问题设备索引
     */
    public CGSPSolverLeaseTerm(CGNode _node, int equipIdx) {
        node = _node;
        equipmentIndex = equipIdx;
    }

    public void setDualLambda(RMSolution _masterSolution) {
        mu = _masterSolution.getDualMu()[equipmentIndex];
        double dualTH = _masterSolution.getDualSumTH()[equipmentIndex];
        double[][] lambdaB_kw = _masterSolution.getDualLambdaB()[equipmentIndex];
        double[][] lambdaE_kw = _masterSolution.getDualLambdaE()[equipmentIndex];
        cDualTB_k = new double[lambdaB_kw.length];
        cDualTE_k = new double[lambdaE_kw.length];
        for (int k = 0; k < cDualTB_k.length; k++) {
            cDualTB_k[k] = Tools.getArraySum(lambdaB_kw[k]) - dualTH;
            cDualTE_k[k] = Tools.getArraySum(lambdaE_kw[k]) + dualTH;
        }
    }

    public RMSolutionL solve(RMProblem problem) throws IloException {

        RMPara para = problem.getPara();
        if (para.isSplSolverDP()) {
            SPLSolverDP dp = new SPLSolverDP(problem, equipmentIndex);
            dp.setObjParams(mu, cDualTB_k, cDualTE_k);
            solution = dp.enumerate(node);
            if (dp.getNColumnAdded() > 0) {
                pricingStatus = PricingStatus.NEW_COLUMN_GENERATED;
            } else if (dp.getNOldColumn() > 0) {
                pricingStatus = PricingStatus.OLD_COLUMN_GENERATED;
            }
        } else {
            Model model = new Model(problem);

            model.reduceDimensionI(equipmentIndex);

            model.definePricingProblemIntLeaseTerm(cDualTB_k, cDualTE_k);
            model.addConsTBE();
            model.addConsTDBE();
            model.addConsGammaSubProblem();
            model.addConsGammaSeq();
            model.addConsTEnd();
            model.addConsBranchLeaseTerm(node);

            Analysis analysis = model.solveDefault();

            // get solution if exists
            if (analysis.solutionStatus != IloCplex.Status.Optimal) {
                model.exportModel("SPL[%d]Node[%d]".formatted(equipmentIndex, node.id));
                model.end();
                return null;
            } else {
                solution = model.getSolutionSPL();
                model.end();
            }


            if (columnPricedOut()) {
                checkNewColumn(problem);
            } else {
                pricingStatus = PricingStatus.NULL_COLUMN_GENERATED;
            }

            if (para.isCheckSPLSolverDP()) {
                SPLSolverDP dp = new SPLSolverDP(problem, equipmentIndex);
                dp.setObjParams(mu, cDualTB_k, cDualTE_k);
                RMSolutionL solutionDP = dp.enumerate(node);
                if (Tools.differ(solutionDP.getObj(), solution.getObj())) {
                    System.out.println("ERROR DP SOLUTION OBJ!!!");
                    // below is for debug only
                    dp = new SPLSolverDP(problem, equipmentIndex);
                    dp.setObjParams(mu, cDualTB_k, cDualTE_k);
                    solutionDP = dp.enumerate(node);
                    System.out.println(solutionDP.getObj());
                }
            }
        }

        return solution;
    }

    private void checkNewColumn(RMProblem problem) {
        RMEquipment equip = problem.getEquipments().get(equipmentIndex);
        List<CGScheme> historyList = node.equipLeaseTermSchemeMap.get(equip);
        CGSchemeLeaseTerms scheme = new CGSchemeLeaseTerms(solution, equip);
        if (scheme.isBrandNew(historyList)) {
            pricingStatus = PricingStatus.NEW_COLUMN_GENERATED;
            historyList.add(scheme);
            node.equipLeaseTermSchemeMap.put(equip, historyList);
        } else { // 退化
            pricingStatus = PricingStatus.OLD_COLUMN_GENERATED;
        }
    }

    /**
     * 若有新列入基，需Pricing obj(原问题检验数)>0
     */
    private boolean columnPricedOut() {
        double pricingObj = solution.getObj() - mu;
        return Tools.nonNegative(pricingObj);
    }

}
