package problem;

import lombok.Getter;
import lombok.Setter;

import static problem.RMProblem.BASE_SCHEDULE_COST;

@Getter
@Setter
public class RMWorkOrder extends RMEntity {

    /**
     * 工单索引
     */
    int workSequenceIndex = RM_INVALID_INDEX;

    RMCusOrder cusOrder;
    /**
     * 前序（订单或设备）
     */
    RMEntity previous;
    /**
     * 调度此工单的成本
     */
    double scheduleCost = 0;
    /**
     * 辅助计算，覆盖订单的时长
     */
    int coveredTime = 0;
    /**
     * 辅助判断，服务订单含此工单的最大可能工单数
     */
    int maxNX;

    public RMWorkOrder() {
        super(RMType.WORK_ORDER);
    }

    public RMWorkOrder(int _tB, int _tE, RMCusOrder order) {
        super(RMType.WORK_ORDER);
        this.setTimeRange(_tB, _tE);
        cusOrder = order;
    }

    public RMWorkOrder(int _tB, int _tE, RMEquipment equip) {
        super(RMType.WORK_ORDER);
        this.setTimeRange(_tB, _tE);
        affiliated(equip);
    }

    /**
     * 求解得到的工单
     *
     * @param former 前序订单 / 设备本身
     */
    public RMWorkOrder(int w, RMEquipment equip, RMCusOrder order, RMEntity former) {
        super(RMType.WORK_ORDER);
        affiliated(equip);
        workSequenceIndex = w;
        cusOrder = order;
        previous = former;
        scheduleCost = calScheduleCost();
    }

    public RMWorkOrder getCopy() {
        RMWorkOrder copy = new RMWorkOrder();
        copy.affiliated(affiliation);
        copy.workSequenceIndex = workSequenceIndex;
        copy.cusOrder = cusOrder;
        copy.previous = previous;
        copy.scheduleCost = scheduleCost;
        copy.setTimeRange(tB, tE);
        copy.maxNX = maxNX;
        return copy;
    }

    public RMWorkOrder getCopySimple() {
        RMWorkOrder copy = new RMWorkOrder();
        copy.cusOrder = cusOrder;
        copy.setTimeRange(tB, tE);
        copy.maxNX = maxNX;
        return copy;
    }

    public double getFixedCost() {
        if (cusOrder.setupCost > 0) return cusOrder.setupCost;
        if (tB > cusOrder.tB) return cusOrder.switchCost;
        return 0;
    }

    private double calScheduleCost() {
        return previous.equals(cusOrder) ? 0 : (previous.location.calDist(cusOrder.location) + BASE_SCHEDULE_COST);
    }

    public void resetPrevious(RMEntity former) {
        previous = former;
        scheduleCost = calScheduleCost();
    }

    public void resetSequence(int w) {
        workSequenceIndex = w;
    }

    public void calCoveredTime(int timeB, int timeE) {
        coveredTime = Math.max(0, Math.min(timeE, tE) - Math.max(timeB, tB));
    }

    public String toString() {
        return String.format("WORK I %d w %d J %d %s", affiliation == null ? RM_INVALID_ID : affiliation.id, workSequenceIndex, cusOrder.id, timeWindowToString());
    }
}
