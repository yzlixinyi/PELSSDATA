package problem;

import broker.Tools;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RMSolution {

    double obj;
    double[] Z_p;
    double[][][] Y_ijt;
    double[][][] X_ijw;
    double[][][] S_ijj;
    double[][][] V_ikw;
    double[][] HB_iw;
    double[][] HE_iw;
    double[][] TB_ik;
    double[][] TE_ik;
    double[][][] TD_ikn;
    double[][][] Gamma_ikn;

    double[][] U_is;
    double[][] L_is;
    double[] dualMu;
    double[] dualPi;
    double[][][] dualEta;
    double[][] dualPhi;
    double[][] dualZeta;
    double[] dualDelta;
    double[][][] dualXi;
    double[][][] dualLambdaB;
    double[][][] dualLambdaE;
    double[][] dualPIw;
    double[] dualSplitL;
    double[] dualSplitR;
    double[] dualSumTH;

    public RMSolution() {
    }

    public RMSolution(RMProblem problem) {
        RMPara para = problem.para;
        int nI = para.nEquipment;
        int nJ = para.nCusOrder;
        Z_p = new double[para.nCustomer];
        Y_ijt = new double[nI][nJ][para.nTimePeriod];
        X_ijw = new double[nI][nJ][];
        S_ijj = new double[nI][nJ][nJ];
        V_ikw = new double[nI][][];
        HB_iw = new double[nI][];
        HE_iw = new double[nI][];
        TB_ik = new double[nI][];
        TE_ik = new double[nI][];
        TD_ikn = new double[nI][][];
        Gamma_ikn = new double[nI][][];

        for (int i = 0; i < nI; i++) {
            int mI = problem.mIs[i];
            int qI = problem.qIs[i];
            int cI = problem.cIs[i];
            for (int j = 0; j < nJ; j++) {
                X_ijw[i][j] = new double[mI];
            }
            HB_iw[i] = new double[mI];
            HE_iw[i] = new double[mI];
            TB_ik[i] = new double[qI];
            TE_ik[i] = new double[qI];
            V_ikw[i] = new double[qI][mI];
            TD_ikn[i] = new double[qI][cI];
            Gamma_ikn[i] = new double[qI][cI];
        }
    }

    public void setY(int i, int j, double[] src) {
        System.arraycopy(src, 0, Y_ijt[i][j], 0, src.length);
    }

    public void setX(int i, int j, double[] src) {
        System.arraycopy(src, 0, X_ijw[i][j], 0, src.length);
    }

    public void setS(int i, int j, double[] src) {
        System.arraycopy(src, 0, S_ijj[i][j], 0, src.length);
    }

    public void setV(int i, int k, double[] src) {
        System.arraycopy(src, 0, V_ikw[i][k], 0, src.length);
    }

    public void setGamma(int i, int k, double[] src) {
        System.arraycopy(src, 0, Gamma_ikn[i][k], 0, src.length);
    }

    public void setTD(int i, int k, double[] src) {
        System.arraycopy(src, 0, TD_ikn[i][k], 0, src.length);
    }

    public void setHB(int i, double[] src) {
        System.arraycopy(src, 0, HB_iw[i], 0, src.length);
    }

    public void setHE(int i, double[] src) {
        System.arraycopy(src, 0, HE_iw[i], 0, src.length);
    }

    public void setTB(int i, double[] src) {
        System.arraycopy(src, 0, TB_ik[i], 0, src.length);
    }

    public void setTE(int i, double[] src) {
        System.arraycopy(src, 0, TE_ik[i], 0, src.length);
    }

    public RMSolution getCopy() {
        RMSolution copy = new RMSolution();
        copy.obj = this.obj;
        copy.Z_p = Tools.copy(this.Z_p);
        copy.Y_ijt = Tools.copy(this.Y_ijt);
        copy.X_ijw = Tools.copy(this.X_ijw);
        copy.S_ijj = Tools.copy(this.S_ijj);
        copy.V_ikw = Tools.copy(this.V_ikw);
        copy.HB_iw = Tools.copy(this.HB_iw);
        copy.HE_iw = Tools.copy(this.HE_iw);
        copy.TB_ik = Tools.copy(this.TB_ik);
        copy.TE_ik = Tools.copy(this.TE_ik);
        copy.TD_ikn = Tools.copy(this.TD_ikn);
        copy.Gamma_ikn = Tools.copy(this.Gamma_ikn);
        return copy;
    }

    public RMSolution getCopyDual() {
        RMSolution copy = new RMSolution();
        copy.dualMu = Tools.copy(this.dualMu);
        copy.dualPi = Tools.copy(this.dualPi);
        copy.dualEta = Tools.copy(this.dualEta);
        copy.dualZeta = Tools.copy(this.dualZeta);
        copy.dualDelta = Tools.copy(this.dualDelta);
        copy.dualSumTH = Tools.copy(this.dualSumTH);
        copy.dualXi = Tools.copy(this.dualXi);
        copy.dualLambdaB = Tools.copy(this.dualLambdaB);
        copy.dualLambdaE = Tools.copy(this.dualLambdaE);
        copy.dualSplitL = Tools.copy(this.dualSplitL);
        copy.dualSplitR = Tools.copy(this.dualSplitR);
        copy.dualPIw = Tools.copy(this.dualPIw);
        return copy;
    }

    public RMSolution getCopyDualS() {
        RMSolution copy = new RMSolution();
        copy.dualMu = Tools.copy(this.dualMu);
        copy.dualPhi = Tools.copy(this.dualPhi);
        copy.dualEta = Tools.copy(this.dualEta);
        copy.dualSplitL = Tools.copy(this.dualSplitL);
        copy.dualSplitR = Tools.copy(this.dualSplitR);
        return copy;
    }
}
