package problem;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RMSolutionW {

    double obj;
    double[][] Y_jt;
    double[][] X_jw;
    double[][] S_jj;
    double[] HB_w;
    double[] HE_w;

    public RMSolutionW() {
    }

    public RMSolutionW(RMProblem problem, int equipIndex) {
        RMPara para = problem.para;
        int nJ = para.nCusOrder;
        int mI = problem.mIs[equipIndex];
        Y_jt = new double[nJ][para.getNTimePeriod()];
        S_jj = new double[nJ][nJ];
        X_jw = new double[nJ][mI];
        HB_w = new double[mI];
        HE_w = new double[mI];
    }

    public void setY(int j, double[] src) {
        System.arraycopy(src, 0, Y_jt[j], 0, src.length);
    }

    public void setX(int j, double[] src) {
        System.arraycopy(src, 0, X_jw[j], 0, src.length);
    }

    public void setS(int j, double[] src) {
        System.arraycopy(src, 0, S_jj[j], 0, src.length);
    }

}
