package broker.work;

import broker.CGConstraint;
import broker.CGNode;
import broker.Tools;
import problem.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SPSCoefficient {
    boolean flexibleTime;
    boolean allowRevisit;

    /**
     * eta_pjt
     */
    double[][] etaY_jt;

    /**
     * w1 such that sum_{w=1}^{w1}{X_jw} = 0
     */
    int[] branchXSum0W;
    /**
     * w1 such that sum_{w=1}^{w1}{X_jw} = 1
     */
    int[] branchXSum1W;
    boolean[][] branchYjt0;
    boolean[][] branchYjt1;
    int[] branchLBhBw;
    int[] branchUBhBw;
    int[] branchLBhEw;
    int[] branchUBhEw;
    int[] branchUBSplit;
    int[] branchLBSplit;
    boolean[] branchService1;

    int i;
    RMEquipment equip;
    private final int m_i;
    private final int T;
    protected final List<RMCusOrder> J_i;
    private final boolean isRoot;

    SPSCoefficient(RMProblem problem, RMSolution rmp, int _i, CGNode node) {
        i = _i;
        equip = problem.getEquipments().get(i);
        T = problem.getPara().getNTimePeriod();
        m_i = equip.getMaxNWorkOrder();
        J_i = new ArrayList<>(equip.getTypeMatchOrders());
        isRoot = node.isRoot();

        allowRevisit = problem.getPara().isAllowReVisit();
        flexibleTime = problem.getPara().isFlexibleTimeWindow();

        int nJ = problem.getCusOrders().size();
        etaY_jt = new double[nJ][T];
        if (flexibleTime) {
            for (RMCusOrder order : J_i) {
                int p = order.getAffiliation().getIndex();
                int f = order.getFlexible().getIndex();
                int j = order.getIndex();
                int tD = order.getTD();
                double eta_j = Arrays.stream(rmp.getDualEta()[p][j]).sum();
                for (int t = order.getTB(); t < order.getTE(); t++) {
                    etaY_jt[j][t] = rmp.getDualPhi()[p][f] + tD * rmp.getDualEta()[p][j][t] - eta_j;
                }
            }
        } else {
            for (RMCusOrder order : J_i) {
                int p = order.getAffiliation().getIndex();
                int j = order.getIndex();
                etaY_jt[j] = Tools.copy(rmp.getDualEta()[p][j]);
            }
        }

        if (isRoot) {
            return;
        }
        branchXSum0W = new int[nJ];
        branchXSum1W = new int[nJ];
        branchYjt0 = new boolean[nJ][T];
        branchYjt1 = new boolean[nJ][T];
        branchLBhBw = new int[m_i];
        branchUBhBw = new int[m_i];
        branchLBhEw = new int[m_i];
        branchUBhEw = new int[m_i];
        branchLBSplit = node.getBranchLBSplit();
        branchUBSplit = node.getBranchUBSplit();
        branchService1 = node.getBranchService1()[i];
        Arrays.fill(branchXSum0W, -1);
        Arrays.fill(branchXSum1W, m_i);
        Arrays.fill(branchUBhBw, T);
        Arrays.fill(branchUBhEw, T);
        initBranchConstraints(node);
        J_i.removeIf(order -> branchUBSplit[order.getIndex()] == 0);
        J_i.removeIf(order -> node.getBranchService0()[i][order.getIndex()]);
    }

    private void initBranchConstraints(CGNode node) {
        CGNode consNode = node;
        CGConstraint cons;
        while (consNode != null && !consNode.isRoot()) {
            cons = consNode.getConstraint();
            if (cons.equipment != null && cons.equipment.getIndex() == i) {
                if (cons.branchVarX) {
                    modConsBranchX(cons);
                } else if (cons.branchVarHB) {
                    modConsBranchTimeWindow(cons, branchUBhBw, cons.wIndex, branchLBhBw);
                } else if (cons.branchVarHE) {
                    modConsBranchTimeWindow(cons, branchUBhEw, cons.wIndex, branchLBhEw);
                } else if (cons.branchVarY) {
                    modConsBranchY(cons);
                }
            }
            consNode = consNode.getPredecessor();
        }
    }

    private void modConsBranchTimeWindow(CGConstraint cons, int[] branchUB, int windowIndex, int[] branchLB) {
        if (cons.noMoreThan) {
            branchUB[windowIndex] = Math.min(cons.rightHandSide, branchUB[windowIndex]);
        } else {
            branchLB[windowIndex] = Math.max(cons.rightHandSide, branchLB[windowIndex]);
        }
    }

    private void modConsBranchX(CGConstraint cons) {
        if (cons.noMoreThan) {
            branchXSum0W[cons.jIndex] = Math.max(cons.wIndex, branchXSum0W[cons.jIndex]);
        } else {
            branchXSum1W[cons.jIndex] = Math.min(cons.wIndex, branchXSum1W[cons.jIndex]);
        }
    }

    private void modConsBranchY(CGConstraint cons) {
        if (cons.noMoreThan) {
            branchYjt0[cons.jIndex][cons.tIndex] = true;
        } else {
            branchYjt1[cons.jIndex][cons.tIndex] = true;
        }
    }

    FEASIBLE_TYPE checkFeasibilityWork(CGSchemeWorkLeases scheme) {
        if (isRoot) {
            return FEASIBLE_TYPE.FEASIBLE;
        }
        if (!checkFeasibilitySplit(scheme)) {
            return FEASIBLE_TYPE.INFEASIBLE_J;
        }
        if (checkFeasibilityX(scheme) && checkFeasibilityY(scheme) && checkFeasibilityH(scheme) && checkFeasibilityService(scheme)) {
            return FEASIBLE_TYPE.FEASIBLE;
        } else {
            return FEASIBLE_TYPE.INFEASIBLE_I;
        }
    }

    private boolean checkFeasibilityService(CGSchemeWorkLeases scheme) {
        for (RMCusOrder order : J_i) {
            if (branchService1[order.getIndex()] && !scheme.serveCusOrderOrNot(order.getIndex())) {
                return false;
            }
        }
        return true;
    }

    private boolean checkFeasibilityH(CGSchemeWorkLeases scheme) {
        int w = 0;
        for (RMWorkOrder work : scheme.getWorkOrders()) {
            if (work.getTB() < branchLBhBw[w] || work.getTB() > branchUBhBw[w] || work.getTE() < branchLBhEw[w] || work.getTE() > branchUBhEw[w]) {
                return false;
            }
            w++;
        }
        return true;
    }

    private boolean checkFeasibilityY(CGSchemeWorkLeases scheme) {
        for (RMCusOrder order : J_i) {
            int j = order.getIndex();
            for (int t = order.getTB(); t < order.getTE(); t++) {
                if (branchYjt0[j][t]) {
                    if (scheme.getY_jt()[j][t] > 0) {
                        return false;
                    }
                } else if (branchYjt1[j][t] && (scheme.getY_jt()[j][t] < 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkFeasibilityX(CGSchemeWorkLeases scheme) {
        int w = 0;
        boolean[] orderServed = new boolean[branchXSum1W.length];
        for (RMWorkOrder work : scheme.getWorkOrders()) {
            int j = work.getCusOrder().getIndex();
            if ((w <= branchXSum0W[j]) || (w > branchXSum1W[j])) {
                return false;
            }
            orderServed[j] = true;
            w++;
        }
        for (RMCusOrder order : J_i) {
            int j = order.getIndex();
            if (branchXSum1W[j] < m_i && !orderServed[j]) {
                return false;
            }
        }
        return true;
    }

    private boolean checkFeasibilitySplit(CGSchemeWorkLeases scheme) {
        for (RMWorkOrder work : scheme.getWorkOrders()) {
            int j = work.getCusOrder().getIndex();
            if (branchUBSplit[j] == 0 || (branchUBSplit[j] == 1 && (work.getTD() < work.getCusOrder().getTD()))) {
                return false;
            }
        }
        return true;
    }

    public boolean branchFeasible(int w, RMWorkOrder work) {
        if (isRoot) {
            return true;
        }
        RMCusOrder order = work.getCusOrder();
        int j = order.getIndex();
        int hB = work.getTB();
        int hE = work.getTE();
        if (branchLBSplit[j] > work.getMaxNX()) {
            return false;
        }
        if (branchUBSplit[j] == 1 && work.getTD() < order.getTD()) {
            return false;
        }
        if (branchUBSplit[j] == 2 && hB != order.getTB() && hE != order.getTE()) {
            return false;
        }
        if (w <= branchXSum0W[j] || w > branchXSum1W[j]) {
            return false;
        }
        if (hB < branchLBhBw[w] || hB > branchUBhBw[w]) {
            return false;
        }
        if (hE < branchLBhEw[w] || hE > branchUBhEw[w]) {
            return false;
        }
        for (int t = 0; t < hB; t++) {
            if (branchYjt1[j][t]) {
                return false;
            }
        }
        for (int t = hB; t < hE; t++) {
            if (branchYjt0[j][t]) {
                return false;
            }
        }
        for (int t = hE; t < T; t++) {
            if (branchYjt1[j][t]) {
                return false;
            }
        }
        return true;
    }

    public double newWorkObj(RMWorkOrder work, RMEntity previous) {
        if (previous == null) {
            previous = equip;
        }
        work.resetPrevious(previous);
        double scheduleCost = work.getScheduleCost();
        double timeCost = Tools.getArraySumRange(etaY_jt[work.getCusOrder().getIndex()], work.getTB(), work.getTE());
        return -scheduleCost - timeCost - work.getFixedCost();
    }

    public void computeSchemeObj(CGSchemeWorkLeases scheme) {
        double sumEtaY = scheme.getWorkOrders().stream().mapToDouble(work ->
                Tools.getArraySumRange(etaY_jt[work.getCusOrder().getIndex()], work.getTB(), work.getTE())).sum();
        scheme.spsObj = -sumEtaY - scheme.getTotalCost();
    }

    enum FEASIBLE_TYPE {
        FEASIBLE,
        INFEASIBLE_I,
        INFEASIBLE_J,
    }
}
