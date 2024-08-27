package problem;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public class RMLeaseTerm extends RMEntity {
    /**
     * 租赁时长
     */
    int tD;
    /**
     * 租赁时长处在分段函数中的段数索引
     */
    int segmentIndex = RM_INVALID_INDEX;
    /**
     * 租赁成本
     */
    double rentCost = 0;

    int k;
    int tL;
    int tR;
    /**
     * SPL(k, tl, tr, gamma)
     */
    double splObj;

    /**
     * dpF(k, t)
     */
    double sumObj;
    RMLeaseTerm next;

    public RMLeaseTerm(RMEquipment equip) {
        super(RMType.LEASE_TERM);
        affiliated(equip);
    }

    /**
     * 根据(tB, tE]和Gamma_kn确定租约
     */
    public RMLeaseTerm(RMEquipment equip, int tBk, int tEk, int[] gamma) {
        super(RMType.LEASE_TERM);
        affiliated(equip);
        tB = tBk;
        tE = tEk;
        tD = tE - tB;
        RMSegmentFunction function = equip.getRentFunction();
        for (int n = 0; n < gamma.length; n++) {
            if (gamma[n] == 1) {
                segmentIndex = n;
                rentCost = function.getSlope(n) * tD + function.getIntercept(n);
                break;
            }
        }
    }

    public RMLeaseTerm(RMEquipment equip, int _tB, int _tD, int segIndex) {
        super(RMType.LEASE_TERM);
        affiliated(equip);
        tB = _tB;
        tE = _tB + _tD;
        tD = _tD;
        segmentIndex = segIndex;
        if (segIndex > RM_INVALID_INDEX) {
            rentCost = equip.getRentFunction().getRentCost(segIndex, tD);
        }
    }

    public RMLeaseTerm(RMEquipment equip, int _tB, int _tE) {
        super(RMType.LEASE_TERM);
        affiliated(equip);
        tB = _tB;
        tE = _tE;
        tD = _tE - _tB;
        RMSegmentFunction function = equip.getRentFunction();
        segmentIndex = function.getSegment(tD);
        if (segmentIndex > RM_INVALID_INDEX) {
            rentCost = function.getRentCost(segmentIndex, tD);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RMLeaseTerm that = (RMLeaseTerm) o;
        return tD == that.tD && segmentIndex == that.segmentIndex && Double.compare(that.rentCost, rentCost) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tD, segmentIndex, rentCost);
    }

    public String toString() {
        return String.format("RENT I %d n %d %s", affiliation == null ? RM_INVALID_ID : affiliation.id, segmentIndex, timeWindowToString());
    }

    public RMLeaseTerm getCopy() {
        RMLeaseTerm copy = new RMLeaseTerm((RMEquipment) affiliation);
        copy.tB = tB;
        copy.tE = tE;
        copy.tD = tD;
        copy.segmentIndex = segmentIndex;
        copy.rentCost = rentCost;
        return copy;
    }

    public void resetTE(int _tE) {
        tE = _tE;
        tD = tE - tB;
        RMSegmentFunction function = ((RMEquipment) affiliation).getRentFunction();
        segmentIndex = function.getSegment(tD);
        if (segmentIndex > RM_INVALID_INDEX) {
            rentCost = function.getRentCost(segmentIndex, tD);
        }
    }

    public List<RMLeaseTerm> coverWork(RMWorkOrder work) {
        List<RMLeaseTerm> leasesCoverWork = new ArrayList<>();
        RMLeaseTerm extendLease = this.getCopy();
        extendLease.resetTE(work.tE);
        if (work.tB == tE) { // gFunction satisfying sub-additive conditions
            leasesCoverWork.add(extendLease);
            return leasesCoverWork;
        } else {
            RMLeaseTerm newLease = new RMLeaseTerm((RMEquipment) affiliation, work.tB, work.tE);
            leasesCoverWork.add(this);
            leasesCoverWork.add(newLease);
        }
        return leasesCoverWork;
    }
}
