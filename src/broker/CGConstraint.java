package broker;

import lombok.Getter;
import problem.RMEquipment;

@Getter
public class CGConstraint {

    public RMEquipment equipment;

    /**
     * Y_ijt变量分支约束
     */
    public boolean branchVarY;
    /**
     * X_ijw变量分支约束
     */
    public boolean branchVarX;
    /**
     * HB_iw变量分支约束
     */
    public boolean branchVarHB;
    /**
     * HE_iw变量分支约束
     */
    public boolean branchVarHE;

    /**
     * TB变量分支约束
     */
    public boolean branchVarTB;
    /**
     * TE变量分支约束
     */
    public boolean branchVarTE;
    /**
     * Gamma_kn变量分支约束
     */
    public boolean branchVarGamma;
    /**
     * V_ikw变量分支约束（主问题）
     */
    boolean branchVarV;
    /**
     * sum_i_w{X_ijw}分支约束（主问题）
     */
    public boolean branchSplits;
    /**
     * sum_w{X_ijw}分支约束（子问题）
     */
    public boolean branchService;
    /**
     * Z_p变量分支约束（主问题）
     */
    public boolean branchVarZ;

    public int lIndex;
    public int jIndex;
    public int tIndex;
    public int wIndex;
    public int kIndex;
    public int nIndex;
    public int pIndex;

    /**
     * <=
     */
    public boolean noMoreThan;
    public int rightHandSide;

    public CGConstraint(RMEquipment equip) {
        equipment = equip;
    }

    public CGConstraint(boolean isLeft, int j, int rhs) {
        branchSplits = true;
        noMoreThan = isLeft;
        jIndex = j;
        rightHandSide = rhs;
    }

    public CGConstraint(boolean isLeft, int p) {
        branchVarZ = true;
        noMoreThan = isLeft;
        pIndex = p;
    }

    public void setBranchVarY(boolean isLeft, int j, int t) {
        branchVarY = true;
        noMoreThan = isLeft;
        jIndex = j;
        tIndex = t;
    }

    public void setBranchVarX(boolean isLeft, int j, int w) {
        branchVarX = true;
        noMoreThan = isLeft;
        jIndex = j;
        wIndex = w;
    }

    public void setBranchVarHB(boolean isLeft, int w, int rhs) {
        branchVarHB = true;
        noMoreThan = isLeft;
        wIndex = w;
        rightHandSide = rhs;
    }

    public void setBranchVarHE(boolean isLeft, int w, int rhs) {
        branchVarHE = true;
        noMoreThan = isLeft;
        wIndex = w;
        rightHandSide = rhs;
    }

    public void setBranchVarTB(boolean isLeft, int k, int rhs) {
        branchVarTB = true;
        kIndex = k;
        noMoreThan = isLeft;
        rightHandSide = rhs;
    }

    public void setBranchVarTE(boolean isLeft, int k, int rhs) {
        branchVarTE = true;
        kIndex = k;
        noMoreThan = isLeft;
        rightHandSide = rhs;
    }

    public void setBranchVarGamma(boolean isLeft, int k, int n) {
        branchVarGamma = true;
        noMoreThan = isLeft;
        kIndex = k;
        nIndex = n;
    }

    public void setBranchVarV(boolean isLeft, int k, int w) {
        branchVarV = true;
        noMoreThan = isLeft;
        kIndex = k;
        wIndex = w;
    }

    public void setBranchService(boolean isLeft, int j) {
        branchService = true;
        noMoreThan = isLeft;
        jIndex = j;
    }

    public String toString() {
        if (branchVarZ) {
            return String.format("Z[%d]=%d", pIndex, getBranchBinary());
        } else if (branchSplits) {
            return String.format("NJ[%d]%s%d", jIndex, getBranchRelation(), rightHandSide);
        } else if (branchService) {
            return String.format("Service[%d][%d]=%d", equipment.getIndex(), Math.max(jIndex, lIndex), getBranchBinary());
        } else if (branchVarX) {
            return String.format("X[%d][%d](%d)=%d", equipment.getIndex(), jIndex, wIndex, getBranchBinary());
        } else if (branchVarHB) {
            return String.format("HB[%d][%d]%s%d", equipment.getIndex(), wIndex, getBranchRelation(), rightHandSide);
        } else if (branchVarHE) {
            return String.format("HE[%d][%d]%s%d", equipment.getIndex(), wIndex, getBranchRelation(), rightHandSide);
        } else if (branchVarY) {
            return String.format("Y[%d][%d][%d]=%d", equipment.getIndex(), jIndex, tIndex, getBranchBinary());
        } else if (branchVarGamma) {
            return String.format("Gamma[%d][%d](%d)=%d", equipment.getIndex(), kIndex, nIndex, getBranchBinary());
        } else if (branchVarTB) {
            return String.format("TB[%d][%d]%s%d", equipment.getIndex(), kIndex, getBranchRelation(), rightHandSide);
        } else if (branchVarTE) {
            return String.format("TE[%d][%d]%s%d", equipment.getIndex(), kIndex, getBranchRelation(), rightHandSide);
        } else if (branchVarV) {
            return String.format("V[%d][%d][%d]=%d", equipment.getIndex(), kIndex, wIndex, getBranchBinary());
        }
        return "";
    }

    private String getBranchRelation() {
        return noMoreThan ? "<=" : ">=";
    }

    private int getBranchBinary() {
        return noMoreThan ? 0 : 1;
    }
}
