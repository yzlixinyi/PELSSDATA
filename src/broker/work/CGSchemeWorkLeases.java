package broker.work;

import broker.CGScheme;
import broker.LeaseTermDP;
import broker.Tools;
import lombok.Getter;
import lombok.Setter;
import problem.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
public class CGSchemeWorkLeases extends CGScheme {

    double totalAssignCost;
    double totalTravelCost;
    double totalRentalCost;
    double totalCost;

    double spsObj;

    public CGSchemeWorkLeases() {
    }

    public CGSchemeWorkLeases(RMEquipment equip, RMPara para) {
        y_jt = new int[para.getNCusOrder()][para.getNTimePeriod()];
        equipment = equip;
        workOrders = new ArrayList<>();
        leaseTerms = new ArrayList<>();
    }

    /**
     * 判断两排班策略是否相同（设备相同、订单相同、时间相同，即为相同策略）
     */
    @Override
    protected <E> boolean identical(E e) {
        CGSchemeWorkLeases scheme = (CGSchemeWorkLeases) e;
        if (equipment.getIndex() != scheme.equipment.getIndex()
                || workOrders.size() != scheme.workOrders.size()
                || leaseTerms.size() != scheme.leaseTerms.size()
                || Tools.differ(totalAssignCost, scheme.totalAssignCost)
                || Tools.differ(totalTravelCost, scheme.totalTravelCost)
                || Tools.differ(totalRentalCost, scheme.totalRentalCost)) {
            return false;
        }
        for (int w = 0; w < workOrders.size(); w++) {
            RMWorkOrder work1 = workOrders.get(w);
            RMWorkOrder work2 = scheme.workOrders.get(w);
            if (work1.getCusOrder().getId() != work2.getCusOrder().getId() ||
                    work1.getTB() != work2.getTB() || work1.getTE() != work2.getTE()) {
                return false;
            }
        }
        for (int k = 0; k < leaseTerms.size(); k++) {
            RMLeaseTerm rent1 = leaseTerms.get(k);
            RMLeaseTerm rent2 = scheme.leaseTerms.get(k);
            if (rent1.getTB() != rent2.getTB() || rent1.getTE() != rent2.getTE()) {
                return false;
            }
        }
        return true;
    }

    public void completeSchemeWork(List<RMWorkOrder> works) {
        workOrders.addAll(works);
        int w = 0;
        RMEntity previous = equipment;
        for (RMWorkOrder work : works) {
            int j = work.getCusOrder().getIndex();
            Arrays.fill(y_jt[j], work.getTB(), work.getTE(), 1);
            work.affiliated(equipment);
            work.resetPrevious(previous);
            work.resetSequence(w);
            totalAssignCost += work.getFixedCost();
            totalTravelCost += work.getScheduleCost();
            previous = work.getCusOrder();
            w++;
        }
    }

    public void completeSchemeRent() {
        int[] hB = workOrders.stream().mapToInt(RMWorkOrder::getTB).toArray();
        int[] hE = workOrders.stream().mapToInt(RMWorkOrder::getTE).toArray();
        LeaseTermDP dp = new LeaseTermDP(equipment, workOrders.size(), hB, hE);
        leaseTerms = dp.enumerate();
        totalRentalCost = leaseTerms.stream().mapToDouble(RMLeaseTerm::getRentCost).sum();
        totalCost = totalTravelCost + totalRentalCost + totalAssignCost;
    }

    public void completeSchemeRent(List<RMLeaseTerm> tempTerms) {
        int[] tempB = tempTerms.stream().mapToInt(RMLeaseTerm::getTB).toArray();
        int[] tempE = tempTerms.stream().mapToInt(RMLeaseTerm::getTE).toArray();
        LeaseTermDP dp = new LeaseTermDP(equipment, tempB.length, tempB, tempE);
        leaseTerms = dp.enumerate();
        totalRentalCost = leaseTerms.stream().mapToDouble(RMLeaseTerm::getRentCost).sum();
        totalCost = totalTravelCost + totalRentalCost + totalAssignCost;
    }

    public void completeVariables() {
        int nJ = y_jt.length;
        int T = y_jt[0].length;
        int q_i = equipment.getMaxNLeaseTerm();
        int N = equipment.getRentFunction().getNSegment();
        int m_i = equipment.getMaxNWorkOrder();
        x_jw = new int[nJ][m_i];
        s_jj = new int[nJ][nJ];
        hB_w = new int[m_i];
        hE_w = new int[m_i];
        gamma_kn = new int[q_i][N];
        tD_kn = new int[q_i][N];
        tB_k = new int[q_i];
        tE_k = new int[q_i];
        Arrays.fill(hB_w, T);
        Arrays.fill(hE_w, T);
        Arrays.fill(tB_k, T);
        Arrays.fill(tE_k, T);
        // complete work-related variables
        int w = 0;
        int l = -1;
        for (RMWorkOrder work : workOrders) {
            int j = work.getCusOrder().getIndex();
            hB_w[w] = work.getTB();
            hE_w[w] = work.getTE();
            x_jw[j][w] = 1;
            if (w > 0) {
                s_jj[l][j] = 1;
            }
            l = j;
            w++;
        }
        // complete rent-related variables
        int k = 0;
        for (RMLeaseTerm term : leaseTerms) {
            tB_k[k] = term.getTB();
            tE_k[k] = term.getTE();
            tD_kn[k][term.getSegmentIndex()] = term.getTD();
            gamma_kn[k][term.getSegmentIndex()] = 1;
            totalRentalCost += term.getRentCost();
            k++;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("I ").append(equipment == null ? "?" : equipment.getId());
        for (RMLeaseTerm term : leaseTerms) {
            sb.append(" ").append(term.timeWindowToString());
        }
        for (RMWorkOrder work : workOrders) {
            sb.append(" J").append(work.getCusOrder().getId());
            sb.append(" ").append(work.timeWindowToString());
        }
        return sb.toString();
    }

    public RMSolutionS getSolutionS() {
        RMSolutionS solution = new RMSolutionS();
        solution.setObj(spsObj);
        solution.setY_jt(Tools.copyToDouble(y_jt));
        return solution;
    }
}
