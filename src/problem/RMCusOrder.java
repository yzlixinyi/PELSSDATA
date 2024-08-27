package problem;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

import static broker.Tools.INVALID_INDEX;

@Getter
@Setter
public class RMCusOrder extends RMEntity {

    double switchCost = 0;
    double setupCost = 0;
    /**
     * 设备类型
     */
    int equipmentType = RM_INVALID_ID;
    /**
     * relate raw request with flexible time frame
     */
    RMFlexible flexible;

    /**
     * 服务工单
     */
    List<RMWorkOrder> workOrders = new ArrayList<>();

    /**
     * used for label by time or enumerate
     */
    List<RMWorkOrder> candidateWorkOrders;

    public RMCusOrder(int _id) {
        super(_id, RMType.CUSTOMER_ORDER);
    }

    @Override
    public int getSubType() {
        return equipmentType;
    }

    /**
     * 添加服务工单
     */
    public void addService(RMWorkOrder work) {
        workOrders.add(work);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RMCusOrder that = (RMCusOrder) o;
        return equipmentType == that.equipmentType && Objects.equals(workOrders, that.workOrders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), equipmentType);
    }

    /**
     * Y服务的完备性唯一性，确保同一个设备服务同一个订单是连续的，如不过不允许revisit，则每个设备只能有一个工单服务同一个订单
     */
    public boolean orderServedExclusively(int[][][] y_ijt, RMProblem problem) {
        Map<RMEquipment, List<Integer>> equipTimeMap = new LinkedHashMap<>();
        List<RMEquipment> compatibleEquips = problem.getEquipments().stream().filter(e -> e.getSubType() == this.getSubType()).toList();
        for (int t = tB + 1; t <= tE; t++) {
            List<Integer> iServing = new ArrayList<>();
            for (RMEquipment equip : compatibleEquips) {
                int i = equip.getIndex();
                if (y_ijt[i][index][t - 1] > 0) {
                    iServing.add(i);
                    RMEquipment facility = problem.getEquipments().get(i);
                    List<Integer> times = equipTimeMap.containsKey(facility) ? equipTimeMap.get(facility) : new ArrayList<>();
                    times.add(t);
                    equipTimeMap.put(facility, times);
                }
            }
            if (iServing.isEmpty()) {
                return false;
            }
        }
        workOrders = new ArrayList<>();
        for (Map.Entry<RMEquipment, List<Integer>> entry : equipTimeMap.entrySet()) {
            List<RMWorkOrder> parsedWorks = entry.getKey().parseWorkOrder(entry.getValue());
            if (!problem.para.isAllowReVisit() && parsedWorks.size() > 1) { // revisit exists
                workOrders.clear();
                return false;
            }
            workOrders.addAll(parsedWorks);
        }
        if (workOrders.size() > 1) { // split serviced
            excludeYt(y_ijt);
        }
        exclusiveServe(y_ijt);
        return true;
    }

    /**
     * 服务订单的最小化工单列表
     */
    private void excludeYt(int[][][] intY) {
        // 从较长的工单开始拼出完整服务
        List<RMWorkOrder> finalWorks = new ArrayList<>();
        TimeWindow win0 = new TimeWindow(tB, tE);
        List<TimeWindow> windowsToCover = new ArrayList<>();
        windowsToCover.add(win0);
        // 迭代搜索直到tD间隙补足
        while (!windowsToCover.isEmpty()) {
            windowsToCover.sort(Comparator.comparingInt(TimeWindow::getWidth).reversed());
            TimeWindow win = windowsToCover.remove(0);
            workOrders.forEach(work -> work.calCoveredTime(win.timeB, win.timeE));
            workOrders.sort(Comparator.comparing(RMWorkOrder::getCoveredTime).reversed());
            RMWorkOrder work = workOrders.remove(0);
            finalWorks.add(work);
            List<TimeWindow> winRemain = win.subtract(work);
            windowsToCover.addAll(winRemain);
        }
        // 废弃工单
        for (RMWorkOrder work : workOrders) {
            int i = work.affiliation.getIndex();
            Arrays.fill(intY[i][index], 0);
        }
        workOrders.clear();
        workOrders.addAll(finalWorks);
    }

    /**
     * 服务工单，每个时段服务该订单的设备不可服务其他订单
     */
    private void exclusiveServe(int[][][] intY) {
        workOrders.forEach(work -> Arrays.fill(intY[work.affiliation.index][index], 0));
        for (RMWorkOrder work : workOrders) {
            int i = work.affiliation.getIndex();
            for (int t = work.tB + 1; t <= work.tE; t++) {
                for (int j = 0; j < intY[i].length; j++) {
                    intY[i][j][t - 1] = 0; // 该时段服务该订单的设备不可服务其他订单
                }
                intY[i][index][t - 1] = 1;
            }
        }
    }

    /**
     * 查找可插入订单的位置，默认已排序
     */
    public int insertPlace(List<RMWorkOrder> workAssigned) {
        for (int j = 0; j < workAssigned.size(); j++) {
            RMWorkOrder currentOrder = workAssigned.get(j);
            if (tB < currentOrder.getTB()) {
                if (tE <= currentOrder.getTB()) {
                    return j;
                } else {
                    return INVALID_INDEX;
                }
            } else if (tB < currentOrder.getTE()) {
                return INVALID_INDEX;
            }
        }
        return workAssigned.size();
    }

    protected void collectSingleWorkOrder() {
        candidateWorkOrders = new ArrayList<>();
        RMWorkOrder candidate = new RMWorkOrder(tB, tE, this);
        candidate.maxNX = 1;
        candidateWorkOrders.add(candidate);
    }

    /**
     * @param timeSplits sorted possible split points (tB <= t <= tE)
     * @return all possible work orders
     */
    protected List<RMWorkOrder> splitWorkOrders(List<Integer> timeSplits) {
        List<RMWorkOrder> works = new ArrayList<>();
        int maxNX = timeSplits.size() - 1;
        for (int nx = 1; nx <= maxNX; nx++) {
            int interSec = timeSplits.size() - nx;
            for (int s = 0; s < nx; s++) {
                int tB = timeSplits.get(s);
                int tE = timeSplits.get(s + interSec);
                RMWorkOrder candidate = new RMWorkOrder(tB, tE, this);
                candidate.maxNX = nx;
                works.add(candidate);
            }
        }
        return works;
    }

    /**
     * 仅用于比较，不加BASE_SCHEDULE_COST
     */
    public double getMaxDistTo(List<RMCusOrder> orders) {
        double maxDist = 0;
        for (RMCusOrder order : orders) {
            double dist = location.calDist(order.location);
            if (dist > maxDist) {
                maxDist = dist;
            }
        }
        return maxDist;
    }

    static class TimeWindow {
        int timeB;
        int timeE;

        TimeWindow(int b, int e) {
            timeB = b;
            timeE = e;
        }

        int getWidth() {
            return timeE - timeB;
        }

        public List<TimeWindow> subtract(RMWorkOrder work) {
            List<TimeWindow> remains = new ArrayList<>();
            if (work.tB > timeB) {
                remains.add(new TimeWindow(timeB, work.tB));
            } else {
                work.setTB(timeB);
            }
            if (work.tE < timeE) {
                remains.add(new TimeWindow(work.tE, timeE));
            } else {
                work.setTE(timeE);
            }
            return remains;
        }
    }

    public String toString() {
        return String.format("ORDER %d AFF %d %s TYPE %d %s", id,
                affiliation.id, timeWindowToString(), equipmentType, location.toString());
    }
}
