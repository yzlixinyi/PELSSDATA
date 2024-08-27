package broker;

import problem.RMEquipment;
import problem.RMLeaseTerm;
import problem.RMSolutionL;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LeaseTermDP {

    private final int[] hB;
    private final int[] hE;

    private final int max_w;

    private final RMEquipment equip;

    private Map<Integer, CGSchemeLeaseTerms> bestSchemeMap;

    double[][] V_kw;

    public LeaseTermDP(RMEquipment facility, int m_i, int[] _hB, int[] _hE) {
        hB = _hB;
        hE = _hE;
        max_w = m_i;
        equip = facility;
    }

    public List<RMLeaseTerm> enumerate() {
        int q_i = equip.getMaxNLeaseTerm();
        if (max_w == 0) {
            return Collections.emptyList();
        }

        int maxK = Math.min(q_i, max_w);
        bestSchemeMap = new HashMap<>();
        CGSchemeLeaseTerms bestScheme = forwardKW(maxK - 1, max_w);

        return bestScheme.leaseTerms;
    }

    public void enumerate(RMSolutionL solution) {
        int q_i = equip.getMaxNLeaseTerm();
        V_kw = new double[q_i][equip.getMaxNWorkOrder()];
        if (max_w == 0) {
            return;
        }

        int maxK = Math.min(q_i, max_w);
        bestSchemeMap = new HashMap<>();
        CGSchemeLeaseTerms bestScheme = forwardKW(maxK - 1, max_w);

        List<RMLeaseTerm> terms = bestScheme.leaseTerms;

        // get partial solution from scheme (lease terms)
        double[] TB = solution.getTB_k();
        double[] TE = solution.getTE_k();
        double[][] TD = solution.getTD_kn();
        double[][] Gamma = solution.getGamma_kn();
        for (int k = 0; k < terms.size(); k++) {
            RMLeaseTerm term = terms.get(k);
            TB[k] = term.getTB();
            TE[k] = term.getTE();
            int n = term.getSegmentIndex();
            TD[k][n] = term.getTD();
            Gamma[k][n] = 1;
        }
        double sumRent = terms.stream().mapToDouble(RMLeaseTerm::getRentCost).sum();
        solution.setObj(sumRent);

        int k = 0;
        for (int w = 0; w < max_w; w++) {
            while (TE[k] < hE[w]) {
                k++;
            }
            V_kw[k][w] = 1;
        }
    }

    /**
     * F_1(nW) = g(HE_nW - HB_1), F_k(nW) = min{F_{k-1}(nW), min_{1<w<=nW}{F_{k-1}(w-1)+g(HE_nW-HB_w)}
     *
     * @param k  \in [0, max_k - 1], the index of the last available lease terms
     * @param nW \in [1, max_w], the number of work orders (note that nW is not an index here)
     */
    private CGSchemeLeaseTerms forwardKW(int k, int nW) {
        int key = wKey(k, nW);

        if (bestSchemeMap.containsKey(key)) {
            return bestSchemeMap.get(key);
        }

        if (k == 0) {
            RMLeaseTerm term = new RMLeaseTerm(equip, hB[0], hE[nW - 1]);
            CGSchemeLeaseTerms scheme = new CGSchemeLeaseTerms(equip);
            scheme.leaseTerms.add(term);
            scheme.totalLeasingCost = term.getRentCost();
            bestSchemeMap.put(key, scheme);
            return scheme;
        }

        RMLeaseTerm bestRent = null;
        int bestKey = wKey(k - 1, nW);
        double bestSum = forwardKW(k - 1, nW).totalLeasingCost;
        for (int w = nW - 1; w >= 1; w--) {
            int lastKey = wKey(k - 1, w);
            double lastF = forwardKW(k - 1, w).totalLeasingCost;
            RMLeaseTerm rent = new RMLeaseTerm(equip, hB[w], hE[nW - 1]);
            double sum = rent.getRentCost() + lastF;
            if (sum < bestSum) {
                bestSum = sum;
                bestRent = rent;
                bestKey = lastKey;
            }
        }

        CGSchemeLeaseTerms partScheme = bestSchemeMap.get(bestKey);
        CGSchemeLeaseTerms bestScheme = new CGSchemeLeaseTerms(equip);
        bestScheme.leaseTerms.addAll(partScheme.leaseTerms);
        if (bestRent != null) {
            bestScheme.leaseTerms.add(bestRent);
        }
        bestScheme.totalLeasingCost = bestSum;
        bestSchemeMap.put(key, bestScheme);

        return bestScheme;
    }

    /**
     * @param _k lease term index, \in [0, max_k - 1]
     * @param _w work order id, \in [1, max_w]
     * @return k * max_w + w
     */
    private int wKey(int _k, int _w) {
        return _k * max_w + _w;
    }

    public double[][] getV() {
        return V_kw;
    }
}
