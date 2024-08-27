package problem;

import broker.CGSchemeLeaseTerms;
import broker.Tools;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

@Getter
@Setter
public class RMSolutionL {

    double obj;
    double[] TB_k;
    double[] TE_k;
    double[][] TD_kn;
    double[][] Gamma_kn;

    public RMSolutionL(int q_i, int c_i) {
        TB_k = new double[q_i];
        TE_k = new double[q_i];
        TD_kn = new double[q_i][c_i];
        Gamma_kn = new double[q_i][c_i];
    }

    public RMSolutionL(int q_i, int c_i, int T) {
        TB_k = new double[q_i];
        TE_k = new double[q_i];
        TD_kn = new double[q_i][c_i];
        Gamma_kn = new double[q_i][c_i];
        Arrays.fill(TB_k, T);
        Arrays.fill(TE_k, T);
    }

    public void setGamma(int k, double[] src) {
        System.arraycopy(src, 0, Gamma_kn[k], 0, src.length);
    }

    public void setTD(int k, double[] src) {
        System.arraycopy(src, 0, TD_kn[k], 0, src.length);
    }

    public void readSchemeL(CGSchemeLeaseTerms scheme) {
        obj = -scheme.getSplObj();
        TB_k = Tools.copyToDouble(scheme.getTB_k());
        TE_k = Tools.copyToDouble(scheme.getTE_k());
        TD_kn = Tools.copyToDouble(scheme.getTD_kn());
        Gamma_kn = Tools.copyToDouble(scheme.getGamma_kn());
    }

}
