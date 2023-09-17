package problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
     * 最大工单数，m_i
     */
    int maxNWorkOrder;
    /**
     * 最大租约数，q_i
     */
    int maxNLeaseTerm;

    public RMEquipment(int _id) {
        super(_id, RMType.EQUIPMENT);
    }

    public void setRentFunction(RMSegmentFunction rentFunction) {
        this.rentFunction = rentFunction;
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
                && Objects.equals(typeMatchOrders, equipment.typeMatchOrders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rentFunction, typeMatchOrders, maxNWorkOrder, maxNLeaseTerm);
    }

    public String toString() {
        return String.format("EQUIPMENT %d AFF %d FUNCTION %d TB %d M %d Q %d %s", id,
                affiliation.id, rentFunction.id, tB, maxNWorkOrder, maxNLeaseTerm, location.toString());
    }
}
