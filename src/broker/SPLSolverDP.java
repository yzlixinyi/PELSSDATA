package broker;

import lombok.Getter;
import problem.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SPLSolverDP {

    private final RMEquipment equip;

    private final RMSegmentFunction function;

    private final int c_i;

    private final int q_i;

    private final int T;
    /**
     * 限制主问题松弛解
     */
    private double mu;
    /**
     * sum_w(lambdaB_ikw + lambdaE_ikw)
     */
    private double[] cBk;
    /**
     * alpha(n) + sum_w(lambdaE_ikw)
     */
    private double[][] cDkn;
    /**
     * (k * T + tL, lease term)
     */
    private Map<Integer, RMLeaseTerm> bestSPLMap;
    /**
     * sum_n[0,n_i](gamma_kn)=0
     */
    private int[] branchGmSum0N;
    /**
     * sum_n[0,n_i](gamma_kn)=1
     */
    private int[] branchGmSum1N;
    /**
     * tB >= T_BR, max{T_BR} \in branch cuts
     */
    private int[] branchTBRk;
    /**
     * tE <= T_EL, min{T_EL} \in branch cuts
     */
    private int[] branchTELk;
    /**
     * tB <= T_BL, min{T_BL} \in branch cuts
     */
    private int[] branchUBtBk;
    /**
     * tE >= T_ER, max{T_ER} \in branch cuts
     */
    private int[] branchLBtEk;

    int nColumnAdded = 0;
    int nOldColumn = 0;
    RMSolutionL solution;

    public SPLSolverDP(RMProblem problem, int i) {
        equip = problem.getEquipments().get(i);
        function = equip.getRentFunction();

        T = problem.getPara().getNTimePeriod();
        q_i = equip.getMaxNLeaseTerm();
        c_i = function.getNSegment();
        solution = new RMSolutionL(q_i, c_i);
    }

    public void setObjParams(double _mu, double[] _cDualTBk, double[] _cDualTEk) {
        mu = _mu;
        cBk = new double[q_i];
        cDkn = new double[q_i][c_i];
        for (int k = 0; k < q_i; k++) {
            cBk[k] = _cDualTBk[k] + _cDualTEk[k];
            for (int n = 0; n < c_i; n++) {
                cDkn[k][n] = function.getSlope(n) + _cDualTEk[k];
            }
        }
    }

    private void initBranchConstraints(CGNode node) {
        branchGmSum0N = new int[q_i];
        branchGmSum1N = new int[q_i];
        branchTBRk = new int[q_i];
        branchTELk = new int[q_i];
        branchUBtBk = new int[q_i];
        branchLBtEk = new int[q_i];
        Arrays.fill(branchGmSum0N, -1);
        Arrays.fill(branchGmSum1N, c_i);
        Arrays.fill(branchTELk, T);
        Arrays.fill(branchUBtBk, T);
        CGNode consNode = node;
        CGConstraint cons;
        while (consNode != null && !consNode.isRoot) {
            cons = consNode.getConstraint();
            if (cons.equipment != null && cons.equipment.getIndex() == equip.getIndex()) {
                if (cons.branchVarGamma) {
                    modConsBranchGamma(cons);
                } else if (cons.branchVarTB) {
                    if (cons.noMoreThan) {
                        branchUBtBk[cons.kIndex] = Math.min(cons.rightHandSide, branchUBtBk[cons.kIndex]);
                    } else {
                        branchTBRk[cons.kIndex] = Math.max(cons.rightHandSide, branchTBRk[cons.kIndex]);
                    }
                } else if (cons.branchVarTE) {
                    if (cons.noMoreThan) {
                        branchTELk[cons.kIndex] = Math.min(cons.rightHandSide, branchTELk[cons.kIndex]);
                    } else {
                        branchLBtEk[cons.kIndex] = Math.max(cons.rightHandSide, branchLBtEk[cons.kIndex]);
                    }
                }
            }
            consNode = consNode.getPredecessor();
        }
    }

    private void modConsBranchGamma(CGConstraint cons) {
        if (cons.noMoreThan) {
            branchGmSum0N[cons.kIndex] = Math.max(cons.nIndex, branchGmSum0N[cons.kIndex]);
        } else {
            branchGmSum1N[cons.kIndex] = Math.min(cons.nIndex, branchGmSum1N[cons.kIndex]);
        }
    }

    public RMSolutionL enumerate(CGNode node, double[] _cDualTBk, double[] _cDualTEk) {
        cBk = new double[q_i];
        cDkn = new double[q_i][c_i];
        for (int k = 0; k < q_i; k++) {
            cBk[k] = _cDualTBk[k] + _cDualTEk[k];
            for (int n = 0; n < c_i; n++) {
                cDkn[k][n] = function.getSlope(n) + _cDualTEk[k];
            }
        }

        initBranchConstraints(node);

        bestSPLMap = new LinkedHashMap<>();

        RMLeaseTerm term0 = dpF(0, equip.getTB());
        CGSchemeLeaseTerms scheme = new CGSchemeLeaseTerms(term0);
        // get solution from scheme
        solution.readSchemeL(scheme);

        return solution;
    }

    public RMSolutionL enumerate(CGNode node) {
        initBranchConstraints(node);
        List<CGScheme> historyList = node.equipLeaseTermSchemeMap.get(equip);

        bestSPLMap = new LinkedHashMap<>();

        RMLeaseTerm term0 = dpF(0, equip.getTB());
        CGSchemeLeaseTerms scheme = new CGSchemeLeaseTerms(term0);
        if ((-scheme.splObj - mu > Tools.PRECISION)) {
            if (scheme.isBrandNew(historyList)) {
                // column priced out and brand new
                historyList.add(scheme);
                nColumnAdded++;
            } else {
                nOldColumn++;
            }
        }
        // update node columns
        node.equipLeaseTermSchemeMap.put(equip, historyList);
        // get solution from scheme
        solution.readSchemeL(scheme);

        return solution;
    }

    /**
     * f_k(t) 从t时间开始安排第k到第q_i个租约的最小成本。其中k=0,...,q_i - 1, t \in (tA_i, T]
     */
    private RMLeaseTerm dpF(int k, int tF) {
        int key = splKey(k, tF);

        if (bestSPLMap.containsKey(key)) {
            return bestSPLMap.get(key);
        }

        if (k == (q_i - 1)) {
            RMLeaseTerm endTerm = spLWithBranch(k, tF, T, false);
            endTerm.setSumObj(endTerm.getSplObj());
            bestSPLMap.put(key, endTerm);
            return endTerm;
        }

        int bestT = T; // tF <= tBest <= T
        RMLeaseTerm nextTerm = dpF(k + 1, bestT);
        boolean nextRented = false;
        double fNext = 0;
        if (nextTerm != null) {
            nextRented = nextTerm.getTD() > 0;
            fNext = nextTerm.getSumObj();
        }
        RMLeaseTerm bestSPL = spLWithBranch(k, tF, bestT, nextRented);
        double bestSum = bestSPL.getSplObj() + fNext;
        for (int t = T; t >= tF; t--) {
            nextTerm = dpF(k + 1, t);
            if (nextTerm != null) {
                nextRented = nextTerm.getTD() > 0;
                fNext = nextTerm.getSumObj();
            } else {
                nextRented = false;
                fNext = 0;
            }
            RMLeaseTerm tempSPL = spLWithBranch(k, tF, t, nextRented);
            double obj = tempSPL.getSplObj() + fNext;
            if (obj < bestSum) {
                bestSum = obj;
                bestSPL = tempSPL;
                bestT = t;
            }
        }

        nextTerm = dpF(k + 1, bestT);
        bestSPL.setNext(nextTerm);
        bestSPL.setSumObj(bestSum);
        bestSPLMap.put(key, bestSPL);
        return bestSPL;
    }

    RMLeaseTerm spLWithBranch(int k, int _tl, int _tr, boolean mustRent) {
        // init bounds with branch constraints
        SPLRanges spl = new SPLRanges();
        spl.tL = Math.max(_tl, branchTBRk[k]);
        spl.tR = Math.min(_tr, branchTELk[k]);
        spl.ubTB = Math.min(branchUBtBk[k], spl.tR);
        spl.lbTE = branchLBtEk[k];
        int gm1nIdx = c_i - 1;
        if (branchGmSum1N[k] < c_i) {
            mustRent = true;
            gm1nIdx = branchGmSum1N[k];
        }
        // init with infeasible decisions
        double minObj = Integer.MAX_VALUE;
        int bestD = 0;
        int bestB = T;
        int bestN = -1;
        if (!mustRent && spl.ubTB >= T) {
            double obj = cBk[k] * T;
            if (obj < minObj) {
                minObj = obj;
            }
        }
        if (spl.feasibleForGammaSum1()) { // Gamma <= 1
            for (int n = branchGmSum0N[k] + 1; n <= gm1nIdx; n++) {
                if (function.getBreakPoint(n - 1) > spl.maxD) {
                    break;
                }
                spl.tDL = Math.max(spl.minD, (int) Math.ceil(function.getBreakPoint(n - 1)));
                spl.tDR = Math.min(spl.maxD, (int) Math.floor(function.getBreakPoint(n)));
                if (spl.generateFeasibleRent(cDkn[k][n], cBk[k], function.getIntercept(n))
                        && spl.obj <= minObj) {
                    minObj = spl.obj;
                    bestD = spl.tD;
                    bestB = spl.tB;
                    bestN = n;
                }
            }
        }

        // tD = 0 included
        // infeasible solution included
        RMLeaseTerm leaseTerm = new RMLeaseTerm(equip, bestB, bestD, bestN);
        leaseTerm.setK(k);
        leaseTerm.setTL(spl.tL);
        leaseTerm.setTR(spl.tR);
        leaseTerm.setSplObj(minObj);
        return leaseTerm;
    }

    /**
     * @param _k  \in [0, q_i - 1]
     * @param _tl \in [0, T]
     * @return k * (T+1) + tl
     */
    private int splKey(int _k, int _tl) {
        return _k * (T + 1) + _tl;
    }

    private static class SPLRanges {
        // 以下参数与k和_tl,_tr有关
        int tL;
        int tR;
        int ubTB;
        int lbTE;
        int tBR;
        int minD;
        int maxD;

        // 以下参数还与n有关
        int tDL;
        int tDR;

        // 决策变量
        int tB;
        int tD;
        double obj;

        SPLRanges() {
        }

        boolean feasibleForGammaSum1() {
            tBR = Math.min(tR, ubTB);
            minD = lbTE - ubTB;
            maxD = tR - tL;
            return tL <= tBR && lbTE <= tR && (minD <= maxD) && maxD > 0;
        }

        boolean generateFeasibleRent(double cDkn, double cBk, double intercept) {
            if (tBR + tDR < lbTE) { // impossible
                return false;
            }
            if (cDkn > Tools.PRECISION) {
                if (cBk > Tools.PRECISION) {
                    if (cDkn - cBk > Tools.PRECISION) {
                        tD = Math.max(tDL, lbTE - tBR);
                        tB = Math.max(tL, lbTE - tD);
                    } else {
                        tB = Math.max(tL, lbTE - tDR);
                        tD = Math.max(tDL, lbTE - tB);
                    }
                } else {
                    tD = Math.max(tDL, lbTE - tBR);
                    tB = Math.min(tBR, tR - tD);
                }
            } else {
                if (cBk > Tools.PRECISION) {
                    tD = tDR;
                    tB = Math.max(tL, lbTE - tD);
                } else {
                    if (cDkn - cBk > Tools.PRECISION) {
                        tB = Math.min(tBR, tR - tDL);
                        tD = Math.min(tDR, tR - tB);
                    } else {
                        tD = Math.min(tDR, tR - tL);
                        tB = Math.min(tBR, tR - tD);
                    }
                }
            }
            obj = intercept + cBk * tB + cDkn * tD;
            // check feasibility
            if (tB + tD < lbTE) {
                System.out.println("ERROR: SPL INFEASIBLE!!!");
                return false;
            }
            return true;
        }
    }
}
