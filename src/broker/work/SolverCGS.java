package broker.work;

import broker.*;
import ilog.concert.IloException;
import lombok.Setter;
import problem.RMSolution;
import problem.RMSolutionS;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * <p>Column Generation</p>
 * <p>based on modelW</p>
 */
@Setter
public class SolverCGS extends ColumnGeneration {

    /**
     * 子问题列生成时间(ms)
     */
    private long pricingTimeS;
    /**
     * 主问题最大总列数
     */
    private int maxNScheme;

    @Override
    protected void initSpec() {
        subProblemStatus = new PricingStatus[para.getNEquipment()];
    }

    @Override
    protected void initRootNodeColumn(CGNode node) {
        node.initWorkLease(problem);
    }

    @Override
    protected void removeInfeasibleColumns(CGNode node) {
        CGConstraint constraint = node.getConstraint();
        if (constraint.branchVarZ) {
            node.removeInfeasibleColumnZ(node.getEquipWorkLeaseSchemeMap());
        } else if (constraint.branchSplits) {
            node.removeInfeasibleColumnXSplits(problem.getCusOrders().get(constraint.jIndex), node.getEquipWorkLeaseSchemeMap());
        } else if (constraint.branchService) {
            node.removeInfeasibleColumnServiceX(node.getEquipWorkLeaseSchemeMap());
        } else if (constraint.branchVarX) {
            node.removeInfeasibleColumnXFixed(node.getEquipWorkLeaseSchemeMap());
        } else if (constraint.branchVarHB) {
            node.removeInfeasibleColumnHB(node.getEquipWorkLeaseSchemeMap());
        } else if (constraint.branchVarHE) {
            node.removeInfeasibleColumnHE(node.getEquipWorkLeaseSchemeMap());
        } else if (constraint.branchVarY) {
            node.removeInfeasibleColumnY(node.getEquipWorkLeaseSchemeMap());
        }
        node.removeInfeasibleColumnFlexExclusive(problem, node.getEquipWorkLeaseSchemeMap());
    }

    private void updateUB(CGNode node) {
        double vL = rmpSolution.getObj();
        if (para.isUBNonNegativeSP() || node.isRoot()) {
            for (int i = 0; i < para.getNEquipment(); i++) {
                vL += Math.max(0, node.getPricingWorkLeasesObj()[i] - rmpSolution.getDualMu()[i]);
            }
        } else {
            vL += Tools.getArraySum(node.getPricingWorkLeasesObj());
            vL -= Tools.getArraySum(rmpSolution.getDualMu());
        }
        dualBound = vL;

        if (Tools.nonNegative(node.getBestUpperBound() - dualBound)) {
            node.setBestUpperBound(dualBound);
            bestUpperBoundDual = rmpSolution.getCopyDualS();
        }
    }

    @Override
    protected void solveNode(CGNode node) throws IloException, IOException {
        System.out.println("Solving Node " + node.getId());
        nExplored++;
        integerObjValue = 0;
        ifDegeneration = false;
        greedyUB(node, problem);
        if (Tools.nonNegative(LB - node.getBestUpperBound())) {
            node.setStop(true);
            node.setUnpromising(true);
            return;
        }

        int loopCount = 0; // 计数器
        CGLRMPSolverByColumnS masterProblem = new CGLRMPSolverByColumnS(node);
        masterProblem.init(problem);
        do {
            loopCount++;
            maxNScheme = Math.max(node.getEquipWorkLeaseSchemeMap().values().stream().mapToInt(List::size).sum(), maxNScheme);
            // 求解主问题
            rmpSolution = masterProblem.solve();
            if (stopByMP(node)) {
                break;
            }

            // 求解子问题 pricing problem SP_i 并更新松弛上界 v(RMP) + sum_i{v(SP_i)}
            lagrangianSolution = pricingSP(node, rmpSolution); // 租赁和排班子问题
            updateUB(node);
            checkColumnStatus();
            // v(RMP) <= v(MP) <= v(Lagrange) <= v(RMP) + sum_i{v(SP_i)}
            Tools.printPricingStatus(ifAddNewColumn, ifGenOldColumn);
            System.out.printf("Node %d Iteration %d MP obj %s db %s %n", node.getId(), loopCount, Tools.df1.format(rmpSolution.getObj()), Tools.df1.format(dualBound));
        } while (ifAddNewColumn && !stop() && Tools.nonNegative(dualBound - rmpSolution.getObj()) && Tools.nonNegative(dualBound - LB));

        if (Tools.nonNegative(LB - node.getBestUpperBound())) {
            node.setStop(true);
            node.setUnpromising(true);
        }
        simplexN += loopCount;
        simplexTime += masterProblem.getCalculationTime();
        masterProblem.terminate();
    }

    @Override
    protected boolean isInteger(CGNode node) {
        if (node.findFractionalCustomerOrOrder(rmpSolution) || node.findFractionalWork(rmpSolution)) {
            return false;
        } else {
            System.out.println("None fraction found.");
            return true;
        }
    }

    /**
     * 子问题求解
     */
    private RMSolution pricingSP(CGNode node, RMSolution dualSolution) {
        RMSolution pricingSolutions = new RMSolution(problem);
        Arrays.fill(subProblemStatus, PricingStatus.NULL_COLUMN_GENERATED);
        double[] spObj = new double[para.getNEquipment()];
        long timeStamp = System.currentTimeMillis();
        for (int i = 0; i < para.getNEquipment(); i++) {
            // 排班子问题
            CGSPSolverWorkLease sps = new CGSPSolverWorkLease(node, dualSolution, i);
            // search for new columns
            RMSolutionS solutionS = sps.solve(problem);
            if (solutionS != null) {
                spObj[i] = solutionS.getObj();
                subProblemStatus[i] = sps.getPricingStatus();
                for (int j = 0; j < para.getNCusOrder(); j++) {
                    pricingSolutions.setY(i, j, solutionS.getY_jt()[j]);
                }
            }
        }
        pricingN += para.getNEquipment();
        pricingTimeS += System.currentTimeMillis() - timeStamp;
        node.setPricingWorkLeasesObj(spObj);
        return pricingSolutions;
    }

    public String getDetailedTime() {
        return String.format("%d\t%d\t%d\t%d\t%d\t%d", simplexTime, pricingTimeS, lagrangeTime, simplexN, pricingN, maxNScheme);
    }

}
