package problem;

import lombok.Getter;
import lombok.Setter;

import java.text.DecimalFormat;

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

    public void setSlops(double[] _slopes) {
        System.arraycopy(_slopes, 0, slopes, 0, NSegment);
    }

    public void setIntercepts(double[] _intercepts) {
        System.arraycopy(_intercepts, 0, intercepts, 0, NSegment);
    }

    public void setBreakPoints(double[] _breakPoints) {
        System.arraycopy(_breakPoints, 0, breakPoints, 0, NSegment);
    }

    public void setSupplier(RMSupplier supplier) {
        this.supplier = supplier;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FUNCTION ").append(id);
        sb.append(" AFF ").append(supplier.id);
        sb.append(" TYPE ").append(equipmentType);
        sb.append(" N ").append(NSegment);
        DecimalFormat df1 = new DecimalFormat("0.0");
        for (int n = 0; n < NSegment; n++) {
            sb.append(String.format("[%s, %s, %s]",
                    df1.format(slopes[n]), df1.format(intercepts[n]), df1.format(breakPoints[n])));
        }
        return sb.toString();
    }
}
