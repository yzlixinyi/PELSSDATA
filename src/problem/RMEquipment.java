package problem;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public class RMEquipment extends RMEntity {

    /**
     * 分段线性租金成本函数
     */
    RMSegmentFunction rentFunction;

    /**
     * 设备类型匹配的订单集合
     */
    List<RMCusOrder> typeMatchOrders = new ArrayList<>();

    /**
     * 时刻t是否已租赁
     */
    boolean[] tLeased;

    double avgNTimeBorder;
    /**
     * 最大工单数，m_i
     */
    int maxNWorkOrder;
    /**
     * 最大租约数，q_i
     */
    int maxNLeaseTerm;
    /**
     * 输出工单
     */
    List<RMWorkOrder> workOrders;
    /**
     * 输出租约
     */
    List<RMLeaseTerm> leaseTerms;

    double fixedCost;

    double scheduleCost;

    double rentCost;

    public RMEquipment(int _id) {
        super(_id, RMType.EQUIPMENT);
    }

    @Override
    public int getSubType() {
        return rentFunction.equipmentType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RMEquipment equipment = (RMEquipment) o;
        return maxNWorkOrder == equipment.maxNWorkOrder
                && Objects.equals(rentFunction, equipment.rentFunction)
                && Objects.equals(typeMatchOrders, equipment.typeMatchOrders)
                && Objects.equals(workOrders, equipment.workOrders)
                && Objects.equals(leaseTerms, equipment.leaseTerms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rentFunction, typeMatchOrders, maxNWorkOrder, maxNLeaseTerm);
    }

    public void calScheduleCost() {
        scheduleCost = workOrders.stream().mapToDouble(RMWorkOrder::getScheduleCost).sum();
        fixedCost = workOrders.stream().mapToDouble(RMWorkOrder::getFixedCost).sum();
    }

    public void calRentCost() {
        rentCost = leaseTerms.stream().mapToDouble(RMLeaseTerm::getRentCost).sum();
    }

    public String toString() {
        return String.format("EQUIPMENT %d AFF %d FUNCTION %d TB %d M %d Q %d %s", id,
                affiliation.id, rentFunction.id, tB, maxNWorkOrder, maxNLeaseTerm, location.toString());
    }

    /**
     * 事先处理好t排序、在可用时间内
     */
    public List<RMWorkOrder> parseWorkOrder(List<Integer> times) {
        List<RMWorkOrder> parsedWorks = new ArrayList<>();
        int tB = times.get(0) - 1;
        for (int i = 1; i < times.size(); i++) {
            if (times.get(i) - times.get(i - 1) > 1) {
                parsedWorks.add(new RMWorkOrder(tB, times.get(i - 1), this));
                tB = times.get(i) - 1;
            }
        }
        parsedWorks.add(new RMWorkOrder(tB, times.get(times.size() - 1), this));
        return parsedWorks;
    }

    public List<RMWorkOrder> parseWorkOrder(int[] ys, RMCusOrder order) {
        List<RMWorkOrder> parseWorks = new ArrayList<>();
        int tB = order.tB;
        for (int t = order.tB; t < order.tE - 1; t++) {
            if (ys[t] == 0 && ys[t + 1] == 1) {
                tB = t + 1;
            } else if (ys[t] == 1 && ys[t + 1] == 0) {
                parseWorks.add(new RMWorkOrder(tB, t + 1, order)); // t is an index
            }
        }
        if (ys[order.tE - 1] == 1) {
            parseWorks.add(new RMWorkOrder(tB, order.tE, order));
        }
        return parseWorks;
    }
}
