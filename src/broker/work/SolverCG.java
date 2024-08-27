package broker.work;

import broker.*;
import ilog.concert.IloException;
import lombok.Setter;
import problem.RMSolution;
import problem.RMSolutionL;
import problem.RMSolutionW;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * <p>Column Generation</p>
 * <p>based on modelW</p>
 */
@Setter
public class SolverCG extends ColumnGeneration {

    private boolean consMergeNeighborWork;

    /**
     * 工单子问题列生成时间(ms)
     */
    private long pricingTimeW;
    /**
     * 租约子问题列生成时间(ms)
     */
    private long pricingTimeL;
    /**
     * 主问题工单最大总列数
     */
    private int maxNSchemeW;
    /**
     * 主问题租约最大总列数
     */
    private int maxNSchemeL;

    @Override
    protected void initSpec() {
        subProblemStatus = new PricingStatus[2 * para.getNEquipment()];
    }

    @Override
    protected void initRootNodeColumn(CGNode node) {
        node.initRentAndWork(problem);
    }

    @Override
    protected void removeInfeasibleColumns(CGNode node) {
        CGConstraint constraint = node.getConstraint();
        if (constraint.branchVarZ) {
            node.removeInfeasibleColumnZ(node.getEquipWorkOrderSchemeMap());
            return;
        }
        if (constraint.branchSplits) {
            node.removeInfeasibleColumnXSplits(problem.getCusOrders().get(constraint.jIndex), node.getEquipWorkOrderSchemeMap());
            return;
        }
        if (constraint.branchService) {
            node.removeInfeasibleColumnServiceX(node.getEquipWorkOrderSchemeMap());
        } else if (constraint.branchVarX) {
            node.removeInfeasibleColumnXFixed(node.getEquipWorkOrderSchemeMap());
        } else if (constraint.branchVarHB) {
            node.removeInfeasibleColumnHB(node.getEquipWorkOrderSchemeMap());
        } else if (constraint.branchVarHE) {
            node.removeInfeasibleColumnHE(node.getEquipWorkOrderSchemeMap());
        } else if (constraint.branchVarY) {
            node.removeInfeasibleColumnY(node.getEquipWorkOrderSchemeMap());
        } else if (constraint.branchVarGamma) {
            node.removeInfeasibleColumnGammaFixed(node.getEquipLeaseTermSchemeMap());
        } else if (constraint.branchVarTB) {
            node.removeInfeasibleColumnTB(node.getEquipLeaseTermSchemeMap());
        } else if (constraint.branchVarTE) {
            node.removeInfeasibleColumnTE(node.getEquipLeaseTermSchemeMap());
        }
    }

    private void updateUB(CGNode node) {
        double vL = rmpSolution.getObj();
        if (para.isUBNonNegativeSP() || node.isRoot()) {
            for (int i = 0; i < para.getNEquipment(); i++) {
                vL += Math.max(0, node.getPricingLeaseTermsObj()[i] - rmpSolution.getDualMu()[i]);
                vL += Math.max(0, node.getPricingWorkOrdersObj()[i] - rmpSolution.getDualPi()[i]);
            }
        } else {
            vL += Tools.getArraySum(node.getPricingLeaseTermsObj());
            vL -= Tools.getArraySum(rmpSolution.getDualMu());
            vL += Tools.getArraySum(node.getPricingWorkOrdersObj());
            vL -= Tools.getArraySum(rmpSolution.getDualPi());
        }
        dualBound = vL;

        if (Tools.nonNegative(node.getBestUpperBound() - dualBound)) {
            node.setBestUpperBound(dualBound);
            bestUpperBoundDual = rmpSolution.getCopyDual();
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
        CGLRMPSolverByColumn masterProblem = new CGLRMPSolverByColumn(node);
        masterProblem.setConsMergeNeighborWork(consMergeNeighborWork);
        masterProblem.init(problem);
        do {
            loopCount++;
            maxNSchemeL = Math.max(node.getEquipLeaseTermSchemeMap().values().stream().mapToInt(List::size).sum(), maxNSchemeL);
            maxNSchemeW = Math.max(node.getEquipWorkOrderSchemeMap().values().stream().mapToInt(List::size).sum(), maxNSchemeW);
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
        if (node.findFractionalCustomerOrOrder(rmpSolution) || node.findFractionalWork(rmpSolution)
                || node.findFractionalRent(rmpSolution) || node.findFractionV(rmpSolution)) {
            return false;
        } else {
            System.out.println("None fraction found.");
            return true;
        }
    }

    /**
     * 子问题求解
     */
    private RMSolution pricingSP(CGNode node, RMSolution dualSolution) throws IloException {
        RMSolution pricingSolutions = new RMSolution(problem);
        Arrays.fill(subProblemStatus, PricingStatus.NULL_COLUMN_GENERATED);
        double[] spLObj = new double[para.getNEquipment()];
        double[] spWObj = new double[para.getNEquipment()];
        long timeStamp1 = System.currentTimeMillis();
        for (int i = 0; i < para.getNEquipment(); i++) {
            // 租赁子问题
            CGSPSolverLeaseTerm spL = new CGSPSolverLeaseTerm(node, i);
            spL.setDualLambda(dualSolution);
            // search for new columns
            RMSolutionL solutionL = spL.solve(problem);
            if (solutionL != null) {
                spLObj[i] = solutionL.getObj();
                subProblemStatus[i] = spL.getPricingStatus();
                pricingSolutions.setTB(i, solutionL.getTB_k());
                pricingSolutions.setTE(i, solutionL.getTE_k());
                for (int k = 0; k < solutionL.getTD_kn().length; k++) {
                    pricingSolutions.setTD(i, k, solutionL.getTD_kn()[k]);
                    pricingSolutions.setGamma(i, k, solutionL.getGamma_kn()[k]);
                }
            }
        }
        long timeStamp2 = System.currentTimeMillis();
        for (int i = 0; i < para.getNEquipment(); i++) {
            // 排班子问题
            CGSPSolverWorkOrder spW = new CGSPSolverWorkOrder(node, dualSolution, i);
            // search for new columns
            RMSolutionW solutionW = spW.solve(problem);
            if (solutionW != null) {
                spWObj[i] = solutionW.getObj();
                subProblemStatus[i + para.getNEquipment()] = spW.getPricingStatus();
                if (spW.bestSPLAdded) {
                    subProblemStatus[i] = PricingStatus.NEW_COLUMN_GENERATED;
                }
                pricingSolutions.setHB(i, solutionW.getHB_w());
                pricingSolutions.setHE(i, solutionW.getHE_w());
                for (int j = 0; j < para.getNCusOrder(); j++) {
                    pricingSolutions.setY(i, j, solutionW.getY_jt()[j]);
                    pricingSolutions.setX(i, j, solutionW.getX_jw()[j]);
                    pricingSolutions.setS(i, j, solutionW.getS_jj()[j]);
                }
            }
        }
        pricingN += para.getNEquipment();
        pricingTimeW += System.currentTimeMillis() - timeStamp2;
        pricingTimeL += timeStamp2 - timeStamp1;
        node.setPricingLeaseTermsObj(spLObj);
        node.setPricingWorkOrdersObj(spWObj);
        return pricingSolutions;
    }

    public String getDetailedTime() {
        return String.format("%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d", simplexTime, pricingTimeW, pricingTimeL, lagrangeTime, simplexN, pricingN, maxNSchemeW, maxNSchemeL);
    }

}
