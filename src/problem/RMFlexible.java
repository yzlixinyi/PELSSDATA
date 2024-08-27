package problem;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class RMFlexible extends RMCusOrder {

    /**
     * earliest start time (exclusive)
     */
    int eB;
    /**
     * latest finish time (inclusive)
     */
    int lE;
    /**
     * requested duration, tD <= lE - eB
     */
    int mD;

    public RMFlexible(int _id) {
        super(_id);
    }

    public RMCusOrder getFixedOrder(int b, int e) {
        RMCusOrder copy = new RMCusOrder(RM_INVALID_ID);
        copy.setTimeRange(b, e);
        copy.id = id;
        copy.equipmentType = equipmentType;
        copy.location = location;
        copy.affiliation = affiliation;
        copy.flexible = this;
        return copy;
    }

    @Override
    public String toString() {
        if (mD == 0) return super.toString();
        return String.format("FLEX %d AFF %d (%d, %d] %d TYPE %d %s", id,
                affiliation.id, eB, lE, mD, equipmentType, location.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), eB, lE, mD);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RMFlexible that = (RMFlexible) o;
        return eB == that.eB && lE == that.lE && mD == that.mD;
    }
}
