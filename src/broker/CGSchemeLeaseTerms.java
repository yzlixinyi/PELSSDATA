package broker;

import broker.work.CGSchemeWorkOrders;
import lombok.Getter;
import lombok.Setter;
import problem.RMEquipment;
import problem.RMLeaseTerm;
import problem.RMPara;
import problem.RMSolutionL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 一个设备的一种租法（租赁方式）
 */
@Getter
@Setter
public class CGSchemeLeaseTerms extends CGScheme {
    int modelIdx;

    double totalLeasingCost;

    /**
     * totalLeasingCost + lambdaB * TB + lambdaE * TE
     */
    double splObj;

    List<CGSchemeWorkOrders> bestCoveredSchemes;

    /**
     * temporary storage of lease terms
     */
    public CGSchemeLeaseTerms(RMEquipment equip) {
        int q_i = equip.getMaxNLeaseTerm();
        int N = equip.getRentFunction().getNSegment();
        gamma_kn = new int[q_i][N];
        tD_kn = new int[q_i][N];
        tB_k = new int[q_i];
        tE_k = new int[q_i];
        equipment = equip;
        totalLeasingCost = 0;
        leaseTerms = new ArrayList<>();
    }

    public CGSchemeLeaseTerms(RMEquipment equip, RMPara para) {
        int q_i = equip.getMaxNLeaseTerm();
        int N = equip.getRentFunction().getNSegment();
        int T = para.getNTimePeriod();
        gamma_kn = new int[q_i][N];
        tD_kn = new int[q_i][N];
        tB_k = new int[q_i];
        tE_k = new int[q_i];
        equipment = equip;
        totalLeasingCost = 0;
        leaseTerms = new ArrayList<>();
        Arrays.fill(tB_k, T);
        Arrays.fill(tE_k, T);
        bestCoveredSchemes = new ArrayList<>();
    }

    public CGSchemeLeaseTerms(RMEquipment equip, RMPara para, List<RMLeaseTerm> terms) {
        int q_i = equip.getMaxNLeaseTerm();
        int N = equip.getRentFunction().getNSegment();
        int T = para.getNTimePeriod();
        gamma_kn = new int[q_i][N];
        tD_kn = new int[q_i][N];
        tB_k = new int[q_i];
        tE_k = new int[q_i];
        equipment = equip;
        totalLeasingCost = 0;
        Arrays.fill(tB_k, T);
        Arrays.fill(tE_k, T);
        leaseTerms = terms;
        for (int k = 0; k < terms.size(); k++) {
            RMLeaseTerm term = terms.get(k);
            tB_k[k] = term.getTB();
            tE_k[k] = term.getTE();
            tD_kn[k][term.getSegmentIndex()] = term.getTD();
            gamma_kn[k][term.getSegmentIndex()] = 1;
            totalLeasingCost += term.getRentCost();
        }
        bestCoveredSchemes = new ArrayList<>();
    }

    /**
     * 空租期
     */
    public void initNullRent() {
        RMLeaseTerm rent = new RMLeaseTerm(equipment, tB_k[0], 0, -1);
        leaseTerms.add(rent);
    }

    /**
     * 租一次，租满规划期内可用时段
     */
    public void initFullRent() {
        RMLeaseTerm rent = new RMLeaseTerm(equipment, equipment.getTB(), equipment.getTE());
        leaseTerms.add(rent);
        tB_k[0] = rent.getTB();
        tE_k[0] = rent.getTE();
        tD_kn[0][rent.getSegmentIndex()] = rent.getTD();
        gamma_kn[0][rent.getSegmentIndex()] = 1;
        totalLeasingCost = rent.getRentCost();
    }

    /**
     * @param solution       子问题解
     * @param equip          设备
     */
    public CGSchemeLeaseTerms(RMSolutionL solution, RMEquipment equip) {
        tB_k = Tools.copyToInt(solution.getTB_k());
        tE_k = Tools.copyToInt(solution.getTE_k());
        tD_kn = Tools.copyToInt(solution.getTD_kn());
        gamma_kn = Tools.copyToInt(solution.getGamma_kn());
        equipment = equip;
        leaseTerms = new ArrayList<>();
        for (int k = 0; k < equip.getMaxNLeaseTerm(); k++) {
            if (tE_k[k] == tB_k[k] && k != 0) {
                break;
            }
            RMLeaseTerm rent = new RMLeaseTerm(equipment, tB_k[k], tE_k[k], gamma_kn[k]);
            leaseTerms.add(rent);
            totalLeasingCost += rent.getRentCost();
        }
        bestCoveredSchemes = new ArrayList<>();
    }

    public CGSchemeLeaseTerms(RMLeaseTerm term0) {
        equipment = (RMEquipment) term0.getAffiliation();
        int q_i = equipment.getMaxNLeaseTerm();
        int c_i = equipment.getRentFunction().getNSegment();
        tD_kn = new int[q_i][c_i];
        gamma_kn = new int[q_i][c_i];
        tB_k = new int[q_i];
        tE_k = new int[q_i];
        Arrays.fill(tB_k, equipment.getTE());
        Arrays.fill(tE_k, equipment.getTE());
        leaseTerms = new ArrayList<>();
        int n = term0.getSegmentIndex();
        if (n > -1) {
            tD_kn[0][n] = term0.getTD();
            gamma_kn[0][n] = 1;
        }
        tB_k[0] = term0.getTB();
        tE_k[0] = term0.getTE();
        term0.setId(1);
        leaseTerms.add(term0);
        totalLeasingCost += term0.getRentCost();
        RMLeaseTerm rent = term0.getNext();
        while (rent != null && rent.getTD() > 0) {
            int k = rent.getK();
            n = rent.getSegmentIndex();
            tD_kn[k][n] = rent.getTD();
            gamma_kn[k][n] = 1;
            tB_k[k] = rent.getTB();
            tE_k[k] = rent.getTE();
            rent.setId(k + 1);
            leaseTerms.add(rent);
            totalLeasingCost += rent.getRentCost();
            rent = rent.getNext();
        }
        splObj = term0.getSumObj();
        bestCoveredSchemes = new ArrayList<>();
    }

    /**
     * 找出列表种完全一样的策略
     */
    public CGSchemeLeaseTerms existed(List<CGScheme> schemeList) {
        return (CGSchemeLeaseTerms) schemeList.stream().filter(this::identical).findFirst().orElse(null);
    }

    /**
     * 判断两租赁策略是否相同（设备相同、时间相同，即为相同策略）
     */
    @Override
    protected <E> boolean identical(E e) {
        CGSchemeLeaseTerms scheme = (CGSchemeLeaseTerms) e;
        if (equipment.getIndex() != scheme.equipment.getIndex() || leaseTerms.size() != scheme.leaseTerms.size() ||
                Tools.differ(totalLeasingCost, scheme.totalLeasingCost)) {
            return false;
        }
        for (int k = 0; k < tB_k.length; k++) {
            if (tB_k[k] != scheme.tB_k[k] || tE_k[k] != scheme.tE_k[k]) {
                return false;
            }
            for (int n = 0; n < gamma_kn[k].length; n++) {
                if (gamma_kn[k][n] != scheme.gamma_kn[k][n]) {
                    return false;
                }
            }
        }
        return true;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("I ").append(equipment.getId());
        for (RMLeaseTerm term : leaseTerms) {
            sb.append(" n ").append(term.getSegmentIndex()).append(" ").append(term.timeWindowToString());
        }
        return sb.toString();
    }
}
