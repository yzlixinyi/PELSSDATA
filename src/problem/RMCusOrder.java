package problem;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class RMCusOrder extends RMEntity {

    /**
     * 设备类型
     */
    int equipmentType = RM_INVALID_ID;

    public RMCusOrder(int _id) {
        super(_id, RMType.CUSTOMER_ORDER);
    }

    @Override
    public int getSubType() {
        return equipmentType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RMCusOrder that = (RMCusOrder) o;
        return equipmentType == that.equipmentType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), equipmentType);
    }

    public String toString() {
        return String.format("ORDER %d AFF %d %s TYPE %d %s", id,
                affiliation.id, timeWindowToString(), equipmentType, location.toString());
    }
}
