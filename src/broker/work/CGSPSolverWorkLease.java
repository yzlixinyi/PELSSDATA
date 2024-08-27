package broker.work;

import broker.CGNode;
import broker.PricingStatus;
import lombok.Getter;
import problem.RMPara;
import problem.RMProblem;
import problem.RMSolution;
import problem.RMSolutionS;

public class CGSPSolverWorkLease {

    /**
     * 限制主问题松弛解
     */
    private final RMSolution masterSolution;

    private final CGNode node;

    private final int equipmentIndex;

    @Getter
    private PricingStatus pricingStatus;

    /**
     * 子问题列生成
     *
     * @param equipIdx 子问题设备索引
     */
    public CGSPSolverWorkLease(CGNode _node, RMSolution _masterSolution, int equipIdx) {
        node = _node;
        masterSolution = _masterSolution;
        equipmentIndex = equipIdx;
    }

    public RMSolutionS solve(RMProblem problem) {
        RMPara.SPWMethod solver = problem.getPara().getSpwSolver();
        if (RMPara.SPWMethod.LABEL.equals(solver)) {
            SPSSolverLabel label = new SPSSolverLabel(problem, masterSolution, node, equipmentIndex);
            RMSolutionS solution = label.extend();
            if (label.nColumnAdded > 0) {
                pricingStatus = PricingStatus.NEW_COLUMN_GENERATED;
            } else if (label.nColumnOld > 0) {
                pricingStatus = PricingStatus.OLD_COLUMN_GENERATED;
            }
            return solution;
        } else {
            SPSSolverEnumerate enumerator = new SPSSolverEnumerate(problem, masterSolution, node, equipmentIndex);
            RMSolutionS solution = enumerator.enumerate();
            if (enumerator.nColumnAdded > 0) {
                pricingStatus = PricingStatus.NEW_COLUMN_GENERATED;
            } else if (enumerator.nColumnOld > 0) {
                pricingStatus = PricingStatus.OLD_COLUMN_GENERATED;
            }
            return solution;
        }
    }

}
