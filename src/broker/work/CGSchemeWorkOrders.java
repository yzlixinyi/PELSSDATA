package broker.work;

import broker.CGScheme;
import broker.CGSchemeLeaseTerms;
import broker.Tools;
import lombok.Getter;
import lombok.Setter;
import problem.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class CGSchemeWorkOrders extends CGScheme {

    double totalSchedulingCost;

    double spwYCost;
    double spwObjX;
    double spwObjH;
    /**
     * objX + objH - YCost - schedulingCost
     */
    double spwObj;

    int penaltyX;
    int penaltyY;
    int penaltyH;
    int penaltySplit;
    int penaltyService;
    int penaltySum;
    /**
     * spwObj - (penaltyX + penaltyY + penaltyT)
     */
    double spwPenalObj;

    CGSchemeLeaseTerms bestCoverScheme;

    public CGSchemeWorkOrders() {
    }

    public CGSchemeWorkOrders(RMEquipment equip, RMPara para) {
        int m_i = equip.getMaxNWorkOrder();
        int nJ = para.getNCusOrder();
        int T = para.getNTimePeriod();
        y_jt = new int[nJ][T];
        x_jw = new int[nJ][m_i];
        s_jj = new int[nJ][nJ];
        hB_w = new int[m_i];
        hE_w = new int[m_i];
        equipment = equip;
        totalSchedulingCost = 0;
        workOrders = new ArrayList<>();
        Arrays.fill(hB_w, T);
        Arrays.fill(hE_w, T);
    }

    public CGSchemeWorkOrders(RMSolutionW solution, RMEquipment equip) {
        y_jt = Tools.copyToInt(solution.getY_jt());
        x_jw = Tools.copyToInt(solution.getX_jw());
        s_jj = Tools.copyToInt(solution.getS_jj());
        hB_w = Tools.copyToInt(solution.getHB_w());
        hE_w = Tools.copyToInt(solution.getHE_w());
        equipment = equip;
        workOrders = new ArrayList<>();
        RMEntity previous = equip;
        for (int w = 0; w < equip.getMaxNWorkOrder(); w++) {
            if (hE_w[w] == hB_w[w]) {
                break;
            }
            for (RMCusOrder cusOrder : equip.getTypeMatchOrders()) {
                if (x_jw[cusOrder.getIndex()][w] == 1) {
                    RMWorkOrder work = new RMWorkOrder(w, equipment, cusOrder, previous);
                    work.setTimeRange(hB_w[w], hE_w[w]);
                    previous = cusOrder;
                    totalSchedulingCost += work.getScheduleCost() + work.getFixedCost();
                    workOrders.add(work);
                    break;
                }
            }
        }
    }

    /**
     * 从Y_jt初始化工单列表
     */
    public CGSchemeWorkOrders(RMEquipment equip, int[][] Yjt) {
        int m_i = equip.getMaxNWorkOrder();
        int nJ = Yjt.length;
        int T = Yjt[0].length;
        y_jt = Tools.copy(Yjt);
        x_jw = new int[nJ][m_i];
        s_jj = new int[nJ][nJ];
        hB_w = new int[m_i];
        hE_w = new int[m_i];
        equipment = equip;
        totalSchedulingCost = 0;
        workOrders = new ArrayList<>();
        Arrays.fill(hB_w, T);
        Arrays.fill(hE_w, T);

        List<RMWorkOrder> parsedWorks = new ArrayList<>();
        for (RMCusOrder order : equip.getTypeMatchOrders()) {
            int j = order.getIndex();
            parsedWorks.addAll(equip.parseWorkOrder(y_jt[j], order));
        }
        if (!parsedWorks.isEmpty()) {
            parsedWorks.sort(Comparator.comparingInt(RMEntity::getTB));
            completeScheme(parsedWorks);
        }
    }

    /**
     * 判断两排班策略是否相同（设备相同、订单相同、时间相同，即为相同策略）
     */
    @Override
    protected <E> boolean identical(E e) {
        CGSchemeWorkOrders scheme = (CGSchemeWorkOrders) e;
        if (equipment.getIndex() != scheme.equipment.getIndex() || workOrders.size() != scheme.workOrders.size() ||
                Tools.differ(totalSchedulingCost, scheme.totalSchedulingCost)) {
            return false;
        }
        for (int j = 0; j < x_jw.length; j++) {
            for (int w = 0; w < x_jw[j].length; w++) {
                if (x_jw[j][w] != scheme.x_jw[j][w]) {
                    return false;
                }
            }
            for (int t = 0; t < y_jt[j].length; t++) {
                if (y_jt[j][t] != scheme.y_jt[j][t]) {
                    return false;
                }
            }
        }
        for (int w = 0; w < hB_w.length; w++) {
            if (hB_w[w] != scheme.hB_w[w] || hE_w[w] != scheme.hE_w[w]) {
                return false;
            }
        }
        return true;
    }

    public CGSchemeWorkOrders getCopy() {
        CGSchemeWorkOrders copy = new CGSchemeWorkOrders();
        copy.equipment = equipment;
        copy.y_jt = Tools.copy(y_jt);
        copy.x_jw = Tools.copy(x_jw);
        copy.s_jj = Tools.copy(s_jj);
        copy.hB_w = Tools.copy(hB_w);
        copy.hE_w = Tools.copy(hE_w);
        copy.totalSchedulingCost = totalSchedulingCost;
        copy.spwYCost = spwYCost;
        copy.spwObjX = spwObjX;
        copy.spwObjH = spwObjH;
        copy.spwObj = spwObj;
        copy.penaltyX = penaltyX;
        copy.penaltyY = penaltyY;
        copy.penaltyH = penaltyH;
        copy.penaltySplit = penaltySplit;
        copy.penaltyService = penaltyService;
        copy.penaltySum = penaltySum;
        copy.spwPenalObj = spwPenalObj;
        copy.workOrders = new ArrayList<>();
        for (RMWorkOrder work : workOrders) {
            RMWorkOrder workCopy = work.getCopy();
            copy.workOrders.add(workCopy);
        }
        return copy;
    }

    public List<RMCusOrder> getServedCusOrders() {
        return workOrders.stream().map(RMWorkOrder::getCusOrder).toList();
    }

    public void calPenalObjSPW() {
        penaltySum = penaltyX + penaltyY + penaltyH + penaltySplit + penaltyService;
        spwPenalObj = spwObj - penaltySum;
    }

    public int[] getCusOrdersServedOrNot() {
        return Arrays.stream(x_jw).mapToInt(xj_w -> Arrays.stream(xj_w).sum()).toArray();
    }

    public List<CGSchemeWorkOrders> extendOneWork(List<RMCusOrder> candidateOrders, boolean allowReVisit) {
        List<CGSchemeWorkOrders> appendedSchemes = new ArrayList<>();
        int hE = workOrders.get(workOrders.size() - 1).getTE();
        if (!allowReVisit) {
            candidateOrders.removeAll(getServedCusOrders());
        }
        for (RMCusOrder order : candidateOrders) {
            for (RMWorkOrder work : order.getCandidateWorkOrders()) {
                if (allowReVisit && order.equals(workOrders.get(workOrders.size() - 1).getCusOrder()) && work.getTB() == hE) {
                    continue;
                }
                if (work.getTB() >= hE) {
                    CGSchemeWorkOrders scheme = new CGSchemeWorkOrders();
                    List<RMWorkOrder> works = workOrders.stream().map(RMWorkOrder::getCopySimple).collect(Collectors.toList());
                    works.add(work.getCopySimple());
                    scheme.setWorkOrders(works);
                    appendedSchemes.add(scheme);
                }
            }
        }
        return appendedSchemes;
    }

    public void completeScheme(List<RMWorkOrder> works) {
        workOrders.addAll(works);
        int w = 0;
        RMEntity previous = equipment;
        for (RMWorkOrder work : works) {
            int j = work.getCusOrder().getIndex();
            Arrays.fill(y_jt[j], work.getTB(), work.getTE(), 1);
            hB_w[w] = work.getTB();
            hE_w[w] = work.getTE();
            x_jw[j][w] = 1;
            if (w > 0) {
                s_jj[previous.getIndex()][j] = 1;
            }
            work.affiliated(equipment);
            work.resetPrevious(previous);
            work.resetSequence(w);
            totalSchedulingCost += work.getScheduleCost() + work.getFixedCost();
            previous = work.getCusOrder();
            w++;
        }
    }

    public boolean serveCusOrderAtT(int jIndex, int tIndex) {
        for (RMWorkOrder work : workOrders) {
            if (work.getCusOrder().getIndex() == jIndex) {
                return work.getTB() <= tIndex && work.getTE() > tIndex;
            }
        }
        return false;
    }

    public RMWorkOrder getWorkServingCusOrder(int jIndex) {
        for (RMWorkOrder work : workOrders) {
            if (work.getCusOrder().getIndex() == jIndex) {
                return work;
            }
        }
        return null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("I ").append(equipment == null ? "?" : equipment.getId());
        for (RMWorkOrder work : workOrders) {
            sb.append(" J ").append(work.getCusOrder().getId());
            sb.append(" ").append(work.timeWindowToString());
        }
        return sb.toString();
    }

    public RMSolutionW getSolutionW() {
        RMSolutionW solution = new RMSolutionW();
        solution.setObj(spwObj);
        solution.setY_jt(Tools.copyToDouble(y_jt));
        solution.setX_jw(Tools.copyToDouble(x_jw));
        solution.setS_jj(Tools.copyToDouble(s_jj));
        solution.setHB_w(Tools.copyToDouble(hB_w));
        solution.setHE_w(Tools.copyToDouble(hE_w));
        return solution;
    }
}
