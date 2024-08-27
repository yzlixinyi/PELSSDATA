package broker.work;

import broker.CGConstraint;
import broker.CGNode;
import broker.Tools;
import problem.*;

import java.util.Arrays;

public class SPWCoefficient {
    boolean allowRevisit;

    double[] cX_j;
    /**
     * <p>w=1, c = delta_i + zeta_iw</p>
     * <p>w>1, c = sum_k(xi_{ik,w-1}) + zeta_iw</p>
     */
    double[] cX_w;
    /**
     * sum_k(lambdaB_ikw)
     */
    double[] lambdaB_w;
    /**
     * sum_k(lambdaE_ikw)
     */
    double[] lambdaE_w;
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
    boolean[] branchService0;
    boolean[] branchService1;

    int penalty;

    int i;
    RMEquipment equip;
    private final int m_i;
    private final int T;

    SPWCoefficient(RMProblem problem, RMSolution rmp, int _i, CGNode node) {
        allowRevisit = problem.getPara().isAllowReVisit();
        i = _i;
        equip = problem.getEquipments().get(i);
        T = problem.getPara().getNTimePeriod();
        m_i = equip.getMaxNWorkOrder();
        int q_i = equip.getMaxNLeaseTerm();
        double dualTH = rmp.getDualSumTH()[i];
        lambdaB_w = new double[m_i];
        lambdaE_w = new double[m_i];
        Arrays.fill(lambdaB_w, -dualTH);
        Arrays.fill(lambdaE_w, dualTH);
        for (int k = 0; k < q_i; k++) {
            for (int w = 0; w < m_i; w++) {
                lambdaB_w[w] += rmp.getDualLambdaB()[i][k][w];
                lambdaE_w[w] += rmp.getDualLambdaE()[i][k][w];
            }
        }
        for (int w = 0; w < m_i - 1; w++) {
            double pIw = rmp.getDualPIw()[i][w];
            lambdaB_w[w + 1] += pIw;
            lambdaE_w[w] -= pIw;
        }
        cX_w = Tools.copy(rmp.getDualZeta()[i]);
        cX_w[0] += rmp.getDualDelta()[i];
        for (int w = 1; w < m_i; w++) {
            for (int k = 0; k < q_i - 1; k++) {
                cX_w[w] += rmp.getDualXi()[i][k][w - 1];
            }
        }
        cX_j = Tools.plus(rmp.getDualSplitL(), rmp.getDualSplitR());

        int nJ = problem.getCusOrders().size();
        etaY_jt = new double[nJ][T];
        for (int p = 0; p < rmp.getDualEta().length; p++) {
            RMCustomer customer = problem.getCustomers().get(p);
            for (RMEntity order : customer.getComponents()) {
                etaY_jt[order.getIndex()] = Tools.copy(rmp.getDualEta()[p][order.getIndex()]);
            }
        }

        int sumLambda = (int) Tools.getAbsoluteSum(lambdaB_w) + (int) Tools.getAbsoluteSum(lambdaE_w);
        int sumCX = (int) Tools.getAbsoluteSum(cX_w) + (int) Tools.getAbsoluteSum(cX_j);
        int sumEta = (int) Tools.getAbsoluteSum(etaY_jt);
        int sumConst = sumLambda + sumCX + sumEta;
        penalty = Math.max(1000, sumConst);

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
        branchService0 = node.getBranchService0()[i];
        branchService1 = node.getBranchService1()[i];
        Arrays.fill(branchXSum0W, -1);
        Arrays.fill(branchXSum1W, m_i);
        Arrays.fill(branchUBhBw, T);
        Arrays.fill(branchUBhEw, T);
        initBranchConstraints(node);
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
                    if (cons.noMoreThan) {
                        branchUBhBw[cons.wIndex] = Math.min(cons.rightHandSide, branchUBhBw[cons.wIndex]);
                    } else {
                        branchLBhBw[cons.wIndex] = Math.max(cons.rightHandSide, branchLBhBw[cons.wIndex]);
                    }
                } else if (cons.branchVarHE) {
                    if (cons.noMoreThan) {
                        branchUBhEw[cons.wIndex] = Math.min(cons.rightHandSide, branchUBhEw[cons.wIndex]);
                    } else {
                        branchLBhEw[cons.wIndex] = Math.max(cons.rightHandSide, branchLBhEw[cons.wIndex]);
                    }
                } else if (cons.branchVarY) {
                    modConsBranchY(cons);
                }
            }
            consNode = consNode.getPredecessor();
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

    public void checkFeasibility(CGSchemeWorkOrders scheme) {
        checkFeasibilityX(scheme);
        checkFeasibilityY(scheme);
        checkFeasibilityH(scheme);
        checkFeasibilitySplit(scheme);
        checkFeasibilityService(scheme);
        scheme.calPenalObjSPW();
    }

    void checkFeasibilityService(CGSchemeWorkOrders scheme) {
        int nP = 0;
        int[] servedOrNot = scheme.getCusOrdersServedOrNot();
        for (int j = 0; j < branchService0.length; j++) {
            if (servedOrNot[j] > 0) {
                nP += branchService0[j] ? 1 : 0;
            } else {
                nP += branchService1[j] ? 1 : 0;
            }
        }
        scheme.penaltyService = nP * penalty;
    }

    void checkFeasibilityH(CGSchemeWorkOrders scheme) {
        int pB = 0;
        int pE = 0;
        for (int w = 0; w < m_i; w++) {
            pB += punishHB(w, scheme.getHB_w()[w]);
            pE += punishHE(w, scheme.getHE_w()[w]);
        }
        scheme.penaltyH = (pB + pE);
    }

    private int punishHB(int w, int hB) {
        int nL = Math.max(0, branchLBhBw[w] - hB);
        int nU = Math.max(0, hB - branchUBhBw[w]);
        return penalty * (nL + nU);
    }

    private int punishHE(int w, int hE) {
        int nL = Math.max(0, branchLBhEw[w] - hE);
        int nU = Math.max(0, hE - branchUBhEw[w]);
        return penalty * (nL + nU);
    }

    void checkFeasibilityY(CGSchemeWorkOrders scheme) {
        int nPt = 0;
        for (int j = 0; j < branchYjt0.length; j++) {
            for (int t = 0; t < T; t++) {
                if (branchYjt0[j][t]) {
                    nPt += scheme.getY_jt()[j][t];
                } else if (branchYjt1[j][t]) {
                    nPt += (1 - scheme.getY_jt()[j][t]);
                }
            }
        }
        scheme.penaltyY = nPt * penalty;
    }

    void checkFeasibilityX(CGSchemeWorkOrders scheme) {
        int nPx = 0;
        int w = 0;
        boolean[] orderServed = new boolean[branchXSum1W.length];
        for (RMWorkOrder work : scheme.getWorkOrders()) {
            int j = work.getCusOrder().getIndex();
            if (w <= branchXSum0W[j]) {
                nPx += branchXSum0W[j] - w + 1;
            }
            if (w > branchXSum1W[j]) {
                nPx += w - branchXSum1W[j];
            }
            orderServed[j] = true;
            w++;
        }
        for (int j = 0; j < branchXSum1W.length; j++) {
            if (branchXSum1W[j] < m_i && !orderServed[j]) {
                nPx += m_i - branchXSum1W[j];
            }
        }
        scheme.penaltyX = nPx * penalty;
    }

    void checkFeasibilitySplit(CGSchemeWorkOrders scheme) {
        int nPs = 0;
        for (RMWorkOrder work : scheme.getWorkOrders()) {
            int j = work.getCusOrder().getIndex();
            if (branchUBSplit[j] == 0 || (branchUBSplit[j] == 1 && (work.getTD() < work.getCusOrder().getTD()))) {
                nPs++;
            }
        }
        scheme.penaltySplit = nPs * penalty;
    }

    public double computePartialObj(int w, RMWorkOrder work, RMEntity previous) {
        if (previous == null) {
            previous = equip;
        }
        double setupGain = cX_w[w] - cX_j[work.getCusOrder().getIndex()];
        work.resetPrevious(previous);
        double scheduleCost = work.getScheduleCost();
        double timeCost = Tools.getArraySumRange(etaY_jt[work.getCusOrder().getIndex()], work.getTB(), work.getTE());
        double rangeCost = lambdaB_w[w] * work.getTB() + lambdaE_w[w] * work.getTE();
        return setupGain - scheduleCost - timeCost + rangeCost;
    }

    public boolean branchFeasible(int w, RMWorkOrder work) {
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
}
