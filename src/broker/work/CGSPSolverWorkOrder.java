package broker.work;

import broker.*;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import lombok.Getter;
import problem.*;

import java.util.List;

public class CGSPSolverWorkOrder {

    private RMSolutionW solution;

    /**
     * 限制主问题松弛解
     */
    private final RMSolution masterSolution;

    private final CGNode node;

    private final int equipmentIndex;

    @Getter
    private PricingStatus pricingStatus;

    boolean bestSPLAdded;

    /**
     * 子问题列生成
     *
     * @param equipIdx 子问题设备索引
     */
    public CGSPSolverWorkOrder(CGNode _node, RMSolution _masterSolution, int equipIdx) {
        node = _node;
        masterSolution = _masterSolution;
        equipmentIndex = equipIdx;
    }

    public RMSolutionW solve(RMProblem problem) throws IloException {

        RMPara.SPWMethod solver = problem.getPara().getSpwSolver();
        if (RMPara.SPWMethod.ENUMERATE.equals(solver)) {
            SPWSolverEnumerate enumerator = new SPWSolverEnumerate(problem, masterSolution, node, equipmentIndex);
            solution = enumerator.enumerate();
            bestSPLAdded = enumerator.bestSPLAdded;
            if (enumerator.nColumnAdded > 0) {
                pricingStatus = PricingStatus.NEW_COLUMN_GENERATED;
            } else if (enumerator.nColumnOld > 0) {
                pricingStatus = PricingStatus.OLD_COLUMN_GENERATED;
            }
            return solution;
        } else if (RMPara.SPWMethod.LABEL.equals(solver)) {
            SPWSolverLabel label = new SPWSolverLabel(problem, masterSolution, node, equipmentIndex);
            solution = label.extend();
            bestSPLAdded = label.bestSPLAdded;
            if (label.nColumnAdded > 0) {
                pricingStatus = PricingStatus.NEW_COLUMN_GENERATED;
            } else if (label.nColumnOld > 0) {
                pricingStatus = PricingStatus.OLD_COLUMN_GENERATED;
            }
            return solution;
        }

        Model model = new Model(problem);

        model.reduceDimensionI(equipmentIndex);

        model.definePricingProblemIntWorkOrder(masterSolution);
        model.addConsYUnify();
        model.addConsXSatisfyY();
        model.addConsXSum();
        model.addConsXSeq();
        model.addConsHBE();
        model.addConsHBEXY();
        model.addConsHBEX();
        model.addConsHXY();
        model.addConsHEnd();
        model.addConsFixedJSubProblem();
        model.addConsBranchWorkOrder(node);

        Analysis analysis = model.solveDefault();

        // get solution if exists
        if (analysis.solutionStatus != IloCplex.Status.Optimal) {
            model.exportModel("SPW[%d]Node[%d]".formatted(equipmentIndex, node.getId()));
            model.end();
            return null;
        } else {
            solution = model.getSolutionSPW();
            model.end();
        }

        if (columnPricedOut()) {
            checkNewColumn(problem);
        } else {
            pricingStatus = PricingStatus.NULL_COLUMN_GENERATED;
        }

        return solution;
    }

    /**
     * 若有新列入基，需Pricing obj(原问题检验数)>0
     */
    private boolean columnPricedOut() {
        double pricingObj = solution.getObj() - masterSolution.getDualPi()[equipmentIndex];
        return Tools.nonNegative(pricingObj);
    }

    private void checkNewColumn(RMProblem problem) {
        RMEquipment equip = problem.getEquipments().get(equipmentIndex);
        List<CGScheme> historyList = node.getEquipWorkOrderSchemeMap().get(equip);
        CGSchemeWorkOrders scheme = new CGSchemeWorkOrders(solution, equip);
        if (scheme.isBrandNew(historyList)) {
            pricingStatus = PricingStatus.NEW_COLUMN_GENERATED;
            historyList.add(scheme);
            node.getEquipWorkOrderSchemeMap().put(equip, historyList);
        } else { // 退化
            pricingStatus = PricingStatus.OLD_COLUMN_GENERATED;
        }
    }

}
