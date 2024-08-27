package problem;

import broker.Tools;
import lombok.Getter;
import lombok.Setter;

import static broker.Tools.INVALID_INDEX;

@Getter
@Setter
public class RMSegmentFunction {
    int id;
    /**
     * 定价供应商
     */
    RMSupplier supplier;
    /**
     * 设备类型
     */
    int equipmentType;
    /**
     * 分段线性函数段数
     */
    int NSegment;
    /**
     * 分段斜率 alpha
     */
    double[] slopes;
    /**
     * 分段截距 beta
     */
    double[] intercepts;
    /**
     * 分段区间右值
     */
    double[] breakPoints;
    /**
     * 不包含0的初始段区间左值
     */
    static final double BREAKPOINT0 = 0.5;

    public RMSegmentFunction(int _id, int n, int type) {
        id = _id;
        NSegment = n;
        equipmentType = type;
        slopes = new double[n];
        intercepts = new double[n];
        breakPoints = new double[n];
    }

    public void setN(int n) {
        NSegment = n;
        slopes = new double[n];
        intercepts = new double[n];
        breakPoints = new double[n];
    }

    public void setSlops(double[] _slopes) {
        System.arraycopy(_slopes, 0, slopes, 0, NSegment);
    }

    public void setIntercepts(double[] _intercepts) {
        System.arraycopy(_intercepts, 0, intercepts, 0, NSegment);
    }

    public void setBreakPoints(double[] _breakPoints) {
        System.arraycopy(_breakPoints, 0, breakPoints, 0, NSegment);
    }

    public double getBreakPoint(int n) {
        return n < 0 ? BREAKPOINT0 : breakPoints[n];
    }

    public double getSlope(int n) {
        return slopes[Math.min(n, NSegment - 1)];
    }

    public double getIntercept(int n) {
        return intercepts[Math.min(n, NSegment - 1)];
    }

    public int getSegment(int tD) {
        for (int n = 0; n < NSegment; n++) {
            if (getBreakPoint(n - 1) <= tD && tD <= getBreakPoint(n)) {
                return n;
            }
        }
        return INVALID_INDEX;
    }

    public double getRentCost(int n, int tD) {
        return getSlope(n) * tD + getIntercept(n);
    }

    public double getRent(int tD) {
        int n = getSegment(tD);
        return n > INVALID_INDEX ? getRentCost(n, tD) : 0;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FUNCTION ").append(id);
        sb.append(" AFF ").append(supplier.id);
        sb.append(" TYPE ").append(equipmentType);
        sb.append(" N ").append(NSegment);
        for (int n = 0; n < NSegment; n++) {
            sb.append(String.format("[%s, %s, %s]",
                    Tools.df1.format(slopes[n]), Tools.df1.format(intercepts[n]), Tools.df1.format(breakPoints[n])));
        }
        return sb.toString();
    }
}
