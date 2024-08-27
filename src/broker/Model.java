package broker;

import broker.work.CGSchemeWorkLeases;
import broker.work.CGSchemeWorkOrders;
import ilog.concert.*;
import ilog.cplex.IloCplex;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import problem.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class Model {

    private final IloCplex cplex;
    private final IloLinearNumExpr objFunction;
    private IloObjective obj;
    private IloNumVar[] Z_p;
    private IloNumVar[][][] Y_ijt;
    private IloNumVar[][][] X_ijw;
    private IloNumVar[][][] S_ijj;
    private IloNumVar[][][] V_ikw;
    private IloNumVar[][] HB_iw;
    private IloNumVar[][] HE_iw;
    private IloNumVar[][] TB_ik;
    private IloNumVar[][] TE_ik;
    private IloNumVar[][][] TD_ikn;
    private IloNumVar[][][] Gamma_ikn;
    private IloNumVar[][][][] L_yw;
    private IloNumVar[][] P_fj;

    /**
     * 是否采用设备i的租赁方式omega_i
     */
    private IloNumVar[][] U_is;
    /**
     * dual: mu_i
     */
    private IloRange[] consU_i;
    /**
     * 是否采用设备i的排班方式psi_i
     */
    private IloNumVar[][] L_is;
    /**
     * dual: pi_i
     */
    private IloRange[] consL_i;
    /**
     * dual: phi_pf
     */
    private IloRange[][] consYSumZ_pf;
    /**
     * dual: eta_pjt
     */
    private IloRange[][][] consYSatisfy_pjt;
    /**
     * dual: zeta_iw
     */
    private IloRange[][] consVXSum_iw;
    /**
     * dual: delta_i
     */
    private IloRange[] consVXSum0_i;
    /**
     * dual: xi_ikw
     */
    private IloRange[][][] consVXSeq_ikw;
    /**
     * dual: lambdaB_ikw (rhoB_ijk)
     */
    private IloRange[][][] consTBLe;
    /**
     * dual: lambdaE_ikw (rhoE_ijk)
     */
    private IloRange[][][] consTEGe;

    private IloRange[][][] consVL_iww;

    private IloRange[] consSumTH;

    private Map<Integer, IloRange> consSplitLe;

    private Map<Integer, IloRange> consSplitGe;

    private IloRange[][] consUL;

    /**
     * 规划时间段数
     */
    int T;
    /**
     * 设备总数
     */
    int nI;
    /**
     * 客户数
     */
    int nP;
    /**
     * 订单总数
     */
    int nJ;
    /**
     * 每个设备最大工单数 m_i
     */
    int[] m_i;
    /**
     * 每个设备最大租约数 q_i
     */
    int[] q_i;

    /**
     * CG模型子问题设备索引
     */
    int pricingEquipIndex;

    private final RMProblem problem;
    private final RMPara para;

    public Model(RMProblem rmp) throws IloException {
        problem = rmp;
        para = problem.getPara();

        T = para.getNTimePeriod();
        nI = para.getNEquipment();
        nP = para.getNCustomer();
        nJ = para.getNCusOrder();
        m_i = problem.getMIs();
        q_i = problem.getQIs();

        cplex = new IloCplex();
        cplex.setParam(IloCplex.Param.Parallel, 1); // deterministic
        objFunction = cplex.linearNumExpr();
    }

    private String varName(String head, int idx1, int idx2, int idx3) {
        return "%s_%d_%d_%d".formatted(head, idx1, idx2, idx3);
    }

    /**
     * Z_p (收益相关)
     */
    public void defineNumZ() throws IloException {
        Z_p = new IloNumVar[nP];
        for (int p = 0; p < nP; p++) {
            Z_p[p] = cplex.numVar(0, 1, "z" + p);
            objFunction.addTerm(Z_p[p], problem.getCustomers().get(p).getServiceRevenue());
        }
    }

    /**
     * Z_p (收益相关)
     */
    public void defineIntZ() throws IloException {
        Z_p = new IloIntVar[nP];
        for (int p = 0; p < nP; p++) {
            Z_p[p] = cplex.intVar(0, 1, "z" + p);
            objFunction.addTerm(Z_p[p], problem.getCustomers().get(p).getServiceRevenue());
        }
    }

    /**
     * Y_ijt
     */
    public void defineIntY() throws IloException {
        Y_ijt = new IloIntVar[nI][nJ][T];
        for (int i = 0; i < nI; i++) {
            for (int j = 0; j < nJ; j++) {
                for (int t = 0; t < T; t++) {
                    Y_ijt[i][j][t] = cplex.intVar(0, 1, varName("y", i, j, t));
                }
            }
        }
    }

    /**
     * Y_ijt, X_ijw
     */
    public void defineNumXYS() throws IloException {
        X_ijw = new IloNumVar[nI][nJ][];
        Y_ijt = new IloNumVar[nI][nJ][T];
        S_ijj = new IloIntVar[nI][nJ][nJ];
        for (int i = 0; i < nI; i++) {
            int mI = m_i[i];
            for (int j = 0; j < nJ; j++) {
                X_ijw[i][j] = new IloNumVar[mI];
                for (int w = 0; w < mI; w++) {
                    X_ijw[i][j][w] = cplex.numVar(0, 1, varName("x", i, j, w));
                }
                objFunction.addTerm(X_ijw[i][j][0], -problem.getDistIJ()[i][j]);
                for (int t = 0; t < T; t++) {
                    Y_ijt[i][j][t] = cplex.numVar(0, 1, varName("y", i, j, t));
                }
                for (int k = 0; k < nJ; k++) {
                    S_ijj[i][j][k] = cplex.numVar(0, 1, varName("s", i, j, k));
                    objFunction.addTerm(S_ijj[i][j][k], -problem.getDistJJ()[j][k]);
                }
            }
        }
    }

    /**
     * Y_ijt, X_ijw
     */
    public void defineIntXY() throws IloException {
        defineIntY();
        X_ijw = new IloIntVar[nI][nJ][];
        for (int i = 0; i < nI; i++) {
            int mI = m_i[i];
            if (mI == 0) continue;
            for (int j = 0; j < nJ; j++) {
                X_ijw[i][j] = new IloIntVar[mI];
                for (int w = 0; w < mI; w++) {
                    X_ijw[i][j][w] = cplex.intVar(0, 1, varName("x", i, j, w));
                    objFunction.addTerm(X_ijw[i][j][w], -problem.getCusOrders().get(j).getSetupCost());
                }
                objFunction.addTerm(X_ijw[i][j][0], -problem.getDistIJ()[i][j]);
            }
        }
    }

    /**
     * L_yw(L=XY:i,j,t,w)
     */
    public void defineIntL() throws IloException {
        L_yw = new IloIntVar[nI][nJ][T][];
        for (int i = 0; i < nI; i++) {
            if (m_i[i] == 0) continue;
            for (int j = 0; j < nJ; j++) {
                for (int t = 0; t < T; t++) {
                    L_yw[i][j][t] = new IloIntVar[m_i[i]];
                    for (int w = 0; w < L_yw[i][j][t].length; w++) {
                        L_yw[i][j][t][w] = cplex.intVar(0, 1, "L_%d_%d_%d_%d".formatted(i, j, t, w));
                    }
                    cplex.addEq(cplex.sum(L_yw[i][j][t]), Y_ijt[i][j][t], "consLY%d_%d_%d".formatted(i, j, t));
                }
            }
        }
    }

    private void defineEtaY(double[][][] eta) throws IloException {
        Y_ijt = new IloIntVar[1][nJ][T];
        for (int j = 0; j < nJ; j++) {
            for (int t = 0; t < T; t++) {
                double sumEta = 0;
                for (int p = 0; p < nP; p++) {
                    sumEta += eta[p][j][t];
                }
                Y_ijt[0][j][t] = cplex.intVar(0, 1, "y_%d_%d".formatted(j + 1, t + 1));
                objFunction.addTerm(-sumEta, Y_ijt[0][j][t]);
            }
        }
    }

    /**
     * X_jw, S_jj', Y_ijt, HB_iw, HE_iw，排班相关
     */
    public void definePricingProblemIntWorkOrder(RMSolution masterSolution) throws IloException {
        RMEquipment equip = problem.getEquipments().get(pricingEquipIndex);
        int mI = equip.getMaxNWorkOrder();
        int iAt = equip.getTB();
        X_ijw = new IloIntVar[nI][nJ][mI];
        S_ijj = new IloIntVar[nI][nJ][nJ];
        HB_iw = new IloIntVar[nI][mI];
        HE_iw = new IloIntVar[nI][mI];
        double[][] lambdaB = masterSolution.getDualLambdaB()[pricingEquipIndex];
        double[][] lambdaE = masterSolution.getDualLambdaE()[pricingEquipIndex];
        double[] pIw = masterSolution.getDualPIw()[pricingEquipIndex];
        double dualTH = masterSolution.getDualSumTH()[pricingEquipIndex];
        for (int w = 0; w < mI; w++) {
            double sumLambdaB = -dualTH;
            double sumLambdaE = dualTH;
            for (int k = 0; k < q_i[pricingEquipIndex]; k++) {
                sumLambdaB += lambdaB[k][w];
                sumLambdaE += lambdaE[k][w];
            }
            sumLambdaB += w > 0 ? pIw[w - 1] : 0;
            sumLambdaE -= w < pIw.length ? pIw[w] : 0;
            HB_iw[0][w] = cplex.intVar(iAt, T, "tB_%d".formatted(w + 1));
            HE_iw[0][w] = cplex.intVar(iAt, T, "tE_%d".formatted(w + 1));
            objFunction.addTerm(sumLambdaB, HB_iw[0][w]);
            objFunction.addTerm(sumLambdaE, HE_iw[0][w]);
        }
        double delta = masterSolution.getDualDelta()[pricingEquipIndex];
        double[][] xi = masterSolution.getDualXi()[pricingEquipIndex];
        double[] zeta = masterSolution.getDualZeta()[pricingEquipIndex];
        double[] dualSplitL = masterSolution.getDualSplitL();
        double[] dualSplitR = masterSolution.getDualSplitR();
        double[][] d_jj = problem.getDistJJ();
        double[] d_ij = problem.getDistIJ()[pricingEquipIndex];
        defineEtaY(masterSolution.getDualEta());
        for (int j = 0; j < nJ; j++) {
            double dualSplit = -dualSplitL[j] - dualSplitR[j];
            for (int g = 0; g < nJ; g++) {
                S_ijj[0][j][g] = cplex.intVar(0, 1, "s_%d_%d".formatted(j + 1, g + 1));
                objFunction.addTerm(-d_jj[j][g], S_ijj[0][j][g]);
            }
            for (int w = 0; w < mI; w++) {
                X_ijw[0][j][w] = cplex.intVar(0, 1, "x_%d_%d".formatted(j + 1, w + 1));
                objFunction.addTerm(dualSplit, X_ijw[0][j][w]);
            }
            objFunction.addTerm(delta - d_ij[j], X_ijw[0][j][0]);
            for (int w = 0; w < mI - 1; w++) {
                double sumXi = 0;
                for (double[] xi_k : xi) {
                    sumXi += xi_k[w];
                }
                objFunction.addTerm(sumXi, X_ijw[0][j][w + 1]);
            }
            objFunction.addTerms(zeta, X_ijw[0][j]);
        }
    }

    /**
     * S_ijj'
     */
    public void defineIntS() throws IloException {
        S_ijj = new IloIntVar[nI][nJ][nJ];
        for (int i = 0; i < nI; i++) {
            for (int j = 0; j < nJ; j++) {
                for (int k = 0; k < nJ; k++) {
                    S_ijj[i][j][k] = cplex.intVar(0, 1, varName("S", i, j, k));
                    objFunction.addTerm(S_ijj[i][j][k], -problem.getDistJJ()[j][k]);
                }
            }
        }
    }

    /**
     * V_ikw (工单和租约关系)
     */
    public void defineIntV() throws IloException {
        V_ikw = new IloIntVar[nI][][];
        for (int i = 0; i < nI; i++) {
            int mI = m_i[i];
            int qI = q_i[i];
            V_ikw[i] = new IloIntVar[qI][mI];
            for (int k = 0; k < qI; k++) {
                for (int w = 0; w < mI; w++) {
                    V_ikw[i][k][w] = cplex.intVar(0, 1, varName("v", i, k, w));
                }
            }
        }
    }

    /**
     * V_ikw (工单和租约关系)
     */
    public void defineNumV() throws IloException {
        V_ikw = new IloNumVar[nI][][];
        for (int i = 0; i < nI; i++) {
            int mI = m_i[i];
            int qI = q_i[i];
            V_ikw[i] = new IloNumVar[qI][mI];
            for (int k = 0; k < qI; k++) {
                for (int w = 0; w < mI; w++) {
                    V_ikw[i][k][w] = cplex.numVar(0, 1, varName("v", i, k, w));
                }
            }
        }
    }

    public void defineNumWorkOrder() throws IloException {
        HB_iw = new IloNumVar[nI][];
        HE_iw = new IloNumVar[nI][];
        for (int i = 0; i < nI; i++) {
            int mI = m_i[i];
            int iAt = problem.getEquipments().get(i).getTB();
            HB_iw[i] = new IloNumVar[mI];
            HE_iw[i] = new IloNumVar[mI];
            for (int w = 0; w < mI; w++) {
                HB_iw[i][w] = cplex.numVar(iAt, T, "hB_%d_%d".formatted(i, w));
                HE_iw[i][w] = cplex.numVar(iAt, T, "hE_%d_%d".formatted(i, w));
                cplex.addLe(HB_iw[i][w], HE_iw[i][w], "consH%d_%d".formatted(i, w));
            }
        }
    }

    /**
     * tauB_iw, tauE_iw, v_ikw (工单相关)
     */
    public void defineIntWorkOrder() throws IloException {
        HB_iw = new IloIntVar[nI][];
        HE_iw = new IloIntVar[nI][];
        for (int i = 0; i < nI; i++) {
            int mI = m_i[i];
            int iAt = problem.getEquipments().get(i).getTB();
            HB_iw[i] = new IloIntVar[mI];
            HE_iw[i] = new IloIntVar[mI];
            for (int w = 0; w < mI; w++) {
                HB_iw[i][w] = cplex.intVar(iAt, T, "hB_%d_%d".formatted(i, w));
                HE_iw[i][w] = cplex.intVar(iAt, T, "hE_%d_%d".formatted(i, w));
                cplex.addLe(HB_iw[i][w], HE_iw[i][w], "consH%d_%d".formatted(i, w));
            }
        }
    }

    /**
     * 单个设备的 tB_ik, tE_ik, tD_ikn, gamma_ikn (租约相关)
     */
    public void definePricingProblemIntLeaseTerm(double[] cTBk, double[] cTEk) throws IloException {
        RMEquipment equip = problem.getEquipments().get(pricingEquipIndex);
        int qI = equip.getMaxNLeaseTerm();
        int iAt = equip.getTB();
        RMSegmentFunction rentFunction = equip.getRentFunction();
        int N = rentFunction.getNSegment();

        TB_ik = new IloIntVar[1][qI];
        TE_ik = new IloIntVar[1][qI];
        TD_ikn = new IloIntVar[1][qI][N];
        Gamma_ikn = new IloIntVar[1][qI][N];
        for (int k = 0; k < qI; k++) {
            for (int n = 0; n < N; n++) {
                Gamma_ikn[0][k][n] = cplex.intVar(0, 1, "gm_%d_%d".formatted(k + 1, n + 1));
                TD_ikn[0][k][n] = cplex.intVar(0, T, "tD_%d_%d".formatted(k + 1, n + 1));
                objFunction.addTerm(TD_ikn[0][k][n], -rentFunction.getSlope(n));
                objFunction.addTerm(Gamma_ikn[0][k][n], -rentFunction.getIntercept(n));
            }
            TB_ik[0][k] = cplex.intVar(iAt, T, "tBk_%d".formatted(k + 1));
            TE_ik[0][k] = cplex.intVar(iAt, T, "tEk_%d".formatted(k + 1));
            objFunction.addTerm(TB_ik[0][k], -cTBk[k]);
            objFunction.addTerm(TE_ik[0][k], -cTEk[k]);
        }
    }

    public void defineNumLeaseTerm() throws IloException {
        TB_ik = new IloNumVar[nI][];
        TE_ik = new IloNumVar[nI][];
        TD_ikn = new IloNumVar[nI][][];
        Gamma_ikn = new IloNumVar[nI][][];
        for (int i = 0; i < nI; i++) {
            RMEquipment equip = problem.getEquipments().get(i);
            int iAt = equip.getTB();
            RMSegmentFunction rentFunction = equip.getRentFunction();
            int N = rentFunction.getNSegment();
            int qI = q_i[i];
            TB_ik[i] = new IloNumVar[qI];
            TE_ik[i] = new IloNumVar[qI];
            TD_ikn[i] = new IloNumVar[qI][N];
            Gamma_ikn[i] = new IloNumVar[qI][N];
            for (int k = 0; k < qI; k++) {
                for (int n = 0; n < N; n++) {
                    Gamma_ikn[i][k][n] = cplex.numVar(0, 1, varName("gm", i, k, n));
                    TD_ikn[i][k][n] = cplex.numVar(0, T, varName("tD", i, k, n));
                    objFunction.addTerm(TD_ikn[i][k][n], -rentFunction.getSlope(n));
                    objFunction.addTerm(Gamma_ikn[i][k][n], -rentFunction.getIntercept(n));
                }
                TB_ik[i][k] = cplex.numVar(iAt, T, "tB_%d_%d".formatted(i, k));
                TE_ik[i][k] = cplex.numVar(iAt, T, "tE_%d_%d".formatted(i, k));
                cplex.addLe(TB_ik[i][k], TE_ik[i][k], "consT%d_%d".formatted(i, k));
            }
        }
    }

    /**
     * tB_ik, tE_ik, tD_ikn, gamma_ikn (租约相关)
     */
    public void defineIntLeaseTerm() throws IloException {
        TB_ik = new IloIntVar[nI][];
        TE_ik = new IloIntVar[nI][];
        TD_ikn = new IloIntVar[nI][][];
        Gamma_ikn = new IloIntVar[nI][][];
        for (int i = 0; i < nI; i++) {
            RMEquipment equip = problem.getEquipments().get(i);
            int iAt = equip.getTB();
            RMSegmentFunction rentFunction = equip.getRentFunction();
            int N = rentFunction.getNSegment();
            int qI = q_i[i];
            TB_ik[i] = new IloIntVar[qI];
            TE_ik[i] = new IloIntVar[qI];
            TD_ikn[i] = new IloIntVar[qI][N];
            Gamma_ikn[i] = new IloIntVar[qI][N];
            for (int k = 0; k < qI; k++) {
                for (int n = 0; n < N; n++) {
                    Gamma_ikn[i][k][n] = cplex.intVar(0, 1, varName("gm", i, k, n));
                    TD_ikn[i][k][n] = cplex.intVar(0, T, varName("tD", i, k, n));
                    objFunction.addTerm(TD_ikn[i][k][n], -rentFunction.getSlope(n));
                    objFunction.addTerm(Gamma_ikn[i][k][n], -rentFunction.getIntercept(n));
                }
                TB_ik[i][k] = cplex.intVar(iAt, T, "tB_%d_%d".formatted(i, k));
                TE_ik[i][k] = cplex.intVar(iAt, T, "tE_%d_%d".formatted(i, k));
                cplex.addLe(TB_ik[i][k], TE_ik[i][k], "consT%d_%d".formatted(i, k));
            }
        }
    }

    /**
     * sum_jw(L_yw)<=1
     */
    public void addConsLUnify() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int t = 0; t < T; t++) {
                IloNumExpr lij = cplex.linearNumExpr();
                for (int j = 0; j < nJ; j++) {
                    lij = cplex.sum(lij, cplex.sum(L_yw[i][j][t]));
                }
                cplex.addLe(lij, 1, "consL%d_%d".formatted(i, t));
            }
        }
        for (int j = 0; j < nJ; j++) {
            RMEntity order = problem.getCusOrders().get(j);
            for (int t = 0; t < order.getTB(); t++) {
                for (int i = 0; i < nI; i++) {
                    cplex.addEq(cplex.sum(L_yw[i][j][t]), 0);
                }
            }
            for (int t = order.getTE(); t < T; t++) {
                for (int i = 0; i < nI; i++) {
                    cplex.addEq(cplex.sum(L_yw[i][j][t]), 0);
                }
            }
        }
    }

    /**
     * sum_i(y_jt)=z_p
     */
    public void addConsYZFixed() throws IloException {
        for (int j = 0; j < nJ; j++) {
            RMCusOrder order = problem.getCusOrders().get(j);
            int p = order.getAffiliation().getIndex();
            for (int t = order.getTB(); t < order.getTE(); t++) {
                cplex.addEq(getFstSum(Y_ijt, j, t), Z_p[p], "consYZ%d_%d_%d".formatted(p, j, t));
            }
        }
    }

    /**
     * sum_j(y_ijt)<=1
     */
    public void addConsYUnify() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int t = 0; t < T; t++) {
                cplex.addLe(getMidSum(Y_ijt, i, t), 1, "consYit%d_%d".formatted(i, t));
            }
        }
        for (int j = 0; j < nJ; j++) {
            RMEntity order = problem.getCusOrders().get(j);
            for (int t = 0; t < order.getTB(); t++) {
                for (int i = 0; i < nI; i++) {
                    cplex.addEq(Y_ijt[i][j][t], 0);
                }
            }
            for (int t = order.getTE(); t < T; t++) {
                for (int i = 0; i < nI; i++) {
                    cplex.addEq(Y_ijt[i][j][t], 0);
                }
            }
        }
    }

    public void addConsFulfilment() throws IloException {
        if (para.isFlexibleTimeWindow()) {
            addConsYPZ();
        } else {
            addConsYZFixed();
        }
    }

    /**
     * <p> sum_j(P_fj)=z_p </p>
     * <p> sum_i(y_ijt)=P_fj </p>
     */
    private void addConsYPZ() throws IloException {
        P_fj = new IloIntVar[para.getNFlexible()][nJ];
        for (int f = 0; f < P_fj.length; f++) {
            for (int j = 0; j < nJ; j++) {
                P_fj[f][j] = cplex.boolVar("P%d_%d".formatted(f, j));
            }
        }
        addConsPZ();
        addConsYP();
    }

    private void addConsPZ() throws IloException {
        for (var entry : problem.getCustomerFlex().entrySet()) {
            int p = entry.getKey().getIndex();
            for (RMFlexible flex : entry.getValue()) {
                int f = flex.getIndex();
                IloLinearNumExpr cons = cplex.linearNumExpr();
                for (RMCusOrder order : problem.getFlexFixOrder().get(flex)) {
                    cons.addTerm(1, P_fj[f][order.getIndex()]);
                }
                cplex.addEq(cons, Z_p[p], "consPZ%d_%d".formatted(p, f));
            }
        }
    }

    private void addConsYP() throws IloException {
        for (var entry : problem.getFlexFixOrder().entrySet()) {
            int f = entry.getKey().getIndex();
            for (RMCusOrder order : entry.getValue()) {
                int j = order.getIndex();
                for (int t = order.getTB(); t < order.getTE(); t++) {
                    cplex.addEq(getFstSum(Y_ijt, j, t), P_fj[f][j], "consYP%d_%d_%d".formatted(f, j, t));
                }
            }
        }
    }

    /**
     * X_ijw>=L_yw
     */
    public void addConsXSatisfyL() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i]; w++) {
                for (int j = 0; j < nJ; j++) {
                    for (int t = 0; t < T; t++) {
                        cplex.addGe(X_ijw[i][j][w], L_yw[i][j][t][w], "consXL%d_%d_%d_%d".formatted(i, j, t, w));
                    }
                }
            }
        }
    }


    /**
     * sum_w(X_ijw)>=Y_(ijt)
     */
    public void addConsXSatisfyY() throws IloException {
        for (int i = 0; i < nI; i++) {
            if (m_i[i] == 0) continue;
            for (int j = 0; j < nJ; j++) {
                for (int t = 0; t < T; t++) {
                    cplex.addGe(cplex.sum(X_ijw[i][j]), Y_ijt[i][j][t], "consXY%d_%d_%d".formatted(i, j, t));
                }
            }
        }
    }

    /**
     * <p>sum_j(x_{i,j,w+1})<=sum_j(x_ijw); </p>
     * x_ijw+x_{ij',w+1}<=s_ijj'+1
     */
    public void addConsXSeq() throws IloException {
        IloLinearNumExpr consXConSeqR;
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i] - 1; w++) {
                cplex.addLe(getMidSum(X_ijw, i, w + 1), getMidSum(X_ijw, i, w), "consXSeq%d_%d".formatted(i, w));
                for (int j = 0; j < nJ; j++) {
                    for (int k = 0; k < nJ; k++) {
                        consXConSeqR = cplex.linearNumExpr();
                        consXConSeqR.addTerm(1, X_ijw[i][j][w]);
                        consXConSeqR.addTerm(1, X_ijw[i][k][w + 1]);
                        consXConSeqR.addTerm(-1, S_ijj[i][j][k]);
                        cplex.addLe(consXConSeqR, 1, "consXS%d_%d_%d_%d".formatted(i, j, k, w));
                    }
                }
            }
        }
    }

    /**
     * sum_j(x_ijw)<=1;
     * sum_w(x_ijw)<=1;
     */
    public void addConsXSum() throws IloException {
        addConsXSumJ();
        for (int i = 0; i < nI; i++) {
            for (int j = 0; j < nJ; j++) {
                cplex.addLe(cplex.sum(X_ijw[i][j]), 1, "consXj%d_%d".formatted(i, j));
            }
        }
    }

    /**
     * sum_j(x_ijw)<=1;
     */
    public void addConsXSumJ() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i]; w++) {
                cplex.addLe(getMidSum(X_ijw, i, w), 1, "consXw%d_%d".formatted(i, w));
            }
        }
    }

    /**
     * HB_{i,w+1}>=HE_iw+(X_ijw+X_{ij,w+1}-1)
     */
    public void addConsXSeqH() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i] - 1; w++) {
                for (int j = 0; j < nJ; j++) {
                    cplex.addGe(cplex.diff(HB_iw[i][w + 1], HE_iw[i][w]),
                            cplex.diff(cplex.sum(X_ijw[i][j][w], X_ijw[i][j][w + 1]), 1), "consXSeqH%d_%d_%d".formatted(i, j, w));
                }
            }
        }
    }

    /**
     * HE_{k}<=HB_{k+1}
     */
    public void addConsHBE() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i] - 1; w++) {
                cplex.addLe(HE_iw[i][w], HB_iw[i][w + 1], "consHBE%d_%d".formatted(i, w));
            }
        }
    }

    public void addConsInterH() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 1; w < m_i[i]; w++) {
                cplex.addGe(HB_iw[i][w], cplex.diff(HE_iw[i][w - 1], cplex.prod(T, cplex.diff(1, getMidSum(X_ijw, i, w)))));
            }
        }
    }

    /**
     * <p> HB+1<=t+|T|(1-sum_jL_yw) </p>
     * <p> HE>=t(sum_jL_yw) </p>
     */
    public void addConsHBEL() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i]; w++) {
                for (int t = 0; t < T; t++) {
                    IloNumExpr sumL = cplex.linearNumExpr();
                    for (int j = 0; j < nJ; j++) {
                        sumL = cplex.sum(sumL, L_yw[i][j][t][w]);
                    }
                    cplex.addLe(HB_iw[i][w], cplex.sum(t, cplex.prod(T, cplex.diff(1, sumL))), "consHBL%d_%d_%d".formatted(i, t, w));
                    cplex.addGe(HE_iw[i][w], cplex.prod(t + 1.0, sumL), "consHEL%d_%d_%d".formatted(i, t, w));
                }
            }
        }
    }

    /**
     * <p> HB+1<=t+2|T|(2-X-Y) </p>
     * <p> HE>=t-|T|(2-X-Y) </p>
     */
    public void addConsHBEXY() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i]; w++) {
                for (int j = 0; j < nJ; j++) {
                    for (int t = 0; t < T; t++) { // t is an index
                        IloNumExpr XY = cplex.diff(2, cplex.sum(X_ijw[i][j][w], Y_ijt[i][j][t]));
                        cplex.addLe(HB_iw[i][w], cplex.sum(t, cplex.prod(T, XY)), "consHBXY%d_%d_%d_%d".formatted(i, j, t, w)); // (t + 1) - 1
                        cplex.addGe(HE_iw[i][w], cplex.diff(t + 1.0, cplex.prod(T, XY)), "consHEXY%d_%d_%d_%d".formatted(i, j, t, w));
                    }
                }
            }
        }
    }

    public void addConsHEnd() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i]; w++) {
                cplex.addGe(HB_iw[i][w], cplex.prod(T, cplex.diff(1, getMidSum(X_ijw, i, w))), "consEndH%d_%d".formatted(i, w));
            }
        }
    }

    /**
     * HE_iw-HB_iw\le \sum_jt{L_yw}
     */
    public void addConsHL() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i]; w++) {
                IloLinearNumExpr cons = cplex.linearNumExpr();
                for (int j = 0; j < nJ; j++) {
                    for (int t = 0; t < T; t++) {
                        cons.addTerm(1, L_yw[i][j][t][w]);
                    }
                }
                cplex.addLe(cplex.diff(HE_iw[i][w], HB_iw[i][w]), cons, "consHBEL%d_%d".formatted(i, w));
            }
        }
    }

    /**
     * HE_iw-HB_iw\le \sum_t{Y_ijt}+|T|(1-X_ijw)
     */
    public void addConsHXY() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i]; w++) {
                for (int j = 0; j < nJ; j++) {
                    cplex.addLe(cplex.diff(HE_iw[i][w], HB_iw[i][w]), cplex.sum(cplex.sum(Y_ijt[i][j]),
                            cplex.prod(T, cplex.diff(1, X_ijw[i][j][w]))), "consHBEXY%d_%d_%d".formatted(i, j, w));
                }
            }
        }
    }

    /**
     * sum_j{X}<=HE-HB
     */
    public void addConsHBEX() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i]; w++) {
                cplex.addGe(cplex.diff(HE_iw[i][w], HB_iw[i][w]), getMidSum(X_ijw, i, w), "consHBEX%d_%d".formatted(i, w));
            }
        }
    }

    /**
     * sum_k(V_ikw)=sum_j(X_ijw)
     */
    public void addConsVXSum() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int w = 0; w < m_i[i]; w++) {
                cplex.addEq(getMidSum(V_ikw, i, w), getMidSum(X_ijw, i, w), "consVX%d_%d".formatted(i, w));
            }
        }
    }

    /**
     * <p>V_{ik,w+1}+V_{i,k+1,w+1}≥V_ikw+sum_j(X_{ij,w+1})-1</p>
     * <p>V_{i,1,1}=sum_j(X_{ij,1})</p>
     */
    public void addConsVXSeq() throws IloException {
        for (int i = 0; i < nI; i++) {
            if (m_i[i] == 0) continue;
            cplex.addEq(V_ikw[i][0][0], getMidSum(X_ijw, i, 0), "consVX0_" + i);
            for (int w = 1; w < m_i[i]; w++) {
                for (int k = 1; k < q_i[i]; k++) {
                    cplex.addGe(cplex.diff(cplex.sum(V_ikw[i][k - 1][w], V_ikw[i][k][w]), V_ikw[i][k - 1][w - 1]),
                            cplex.diff(getMidSum(X_ijw, i, w), 1), "consVX%d_%d_%d".formatted(i, k, w));
                }
            }
        }
    }

    private IloLinearNumExpr getFstSum(IloNumVar[][][] vars, int d2, int d3) throws IloException {
        IloLinearNumExpr sum = cplex.linearNumExpr();
        for (IloNumVar[][] var1 : vars) {
            sum.addTerm(1, var1[d2][d3]);
        }
        return sum;
    }

    private IloLinearNumExpr getMidSum(IloNumVar[][][] vars, int d1, int d3) throws IloException {
        IloLinearNumExpr sum = cplex.linearNumExpr();
        for (int d2 = 0; d2 < vars[d1].length; d2++) {
            sum.addTerm(1, vars[d1][d2][d3]);
        }
        return sum;
    }

    /**
     * TE_{k}<=TB_{k+1}
     */
    public void addConsTBE() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int k = 0; k < q_i[i] - 1; k++) {
                cplex.addLe(TE_ik[i][k], TB_ik[i][k + 1], "consTBE%d_%d".formatted(i, k));
            }
        }
    }

    public void addConsInterT() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int k = 1; k < q_i[i]; k++) {
                cplex.addGe(TB_ik[i][k], cplex.diff(TE_ik[i][k - 1], cplex.prod(T, cplex.diff(1, cplex.sum(Gamma_ikn[i][k])))));
            }
        }
    }

    /**
     * sum_n{Gamma_(i,k+1,n)}<=sum_n{Gamma_(ikn)}
     */
    public void addConsGammaSeq() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int k = 0; k < Gamma_ikn[i].length - 1; k++) {
                cplex.addLe(cplex.sum(Gamma_ikn[i][k + 1]), cplex.sum(Gamma_ikn[i][k]), "consGmSeq%d_%d".formatted(i, k));
            }
        }
    }

    public void addConsTEnd() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int k = 0; k < q_i[i]; k++) {
                cplex.addGe(TB_ik[i][k], cplex.prod(T, cplex.diff(1, cplex.sum(Gamma_ikn[i][k]))), "consEndT%d_%d".formatted(i, k));
            }
        }
    }

    /**
     * TB<=HB+|T|(1-V);
     * TE>=HE-|T|(1-V)
     */
    public void addConsTVH() throws IloException {
        IloLinearNumExpr consHTB;
        IloLinearNumExpr consHTE;
        consTBLe = new IloRange[nI][][];
        consTEGe = new IloRange[nI][][];
        for (int i = 0; i < nI; i++) {
            int qI = q_i[i];
            int mI = m_i[i];
            consTBLe[i] = new IloRange[qI][mI];
            consTEGe[i] = new IloRange[qI][mI];
            for (int k = 0; k < qI; k++) {
                for (int w = 0; w < mI; w++) {
                    consHTB = cplex.linearNumExpr();
                    consHTB.addTerm(1, TB_ik[i][k]);
                    consHTB.addTerm(-1, HB_iw[i][w]);
                    consHTB.addTerm(T, V_ikw[i][k][w]);
                    consTBLe[i][k][w] = cplex.addLe(consHTB, T, "consTVHB%d_%d_%d".formatted(i, k, w));
                    consHTE = cplex.linearNumExpr();
                    consHTE.addTerm(1, TE_ik[i][k]);
                    consHTE.addTerm(-1, HE_iw[i][w]);
                    consHTE.addTerm(-T, V_ikw[i][k][w]);
                    consTEGe[i][k][w] = cplex.addGe(consHTE, -T, "consTVHE%d_%d_%d".formatted(i, k, w));
                }
            }
        }
    }

    /**
     * TE-TB=sum_n{TD}
     */
    public void addConsTDBE() throws IloException {
        for (int i = 0; i < nI; i++) {
            for (int k = 0; k < TE_ik[i].length; k++) {
                cplex.addEq(cplex.diff(TE_ik[i][k], TB_ik[i][k]), cplex.sum(TD_ikn[i][k]), "consTDBE%d_%d".formatted(i, k));
            }
        }
    }

    public void addConsGammaSubProblem() throws IloException {
        IloLinearNumExpr consGm;
        RMSegmentFunction func = problem.getEquipments().get(pricingEquipIndex).getRentFunction();
        for (int k = 0; k < TD_ikn[0].length; k++) {
            consGm = cplex.linearNumExpr();
            for (int n = 0; n < TD_ikn[0][k].length; n++) {
                cplex.addGe(TD_ikn[0][k][n], cplex.prod(func.getBreakPoint(n - 1), Gamma_ikn[0][k][n]));
                cplex.addLe(TD_ikn[0][k][n], cplex.prod(func.getBreakPoint(n), Gamma_ikn[0][k][n]));
                consGm.addTerm(1, Gamma_ikn[0][k][n]);
            }
            cplex.addLe(consGm, 1);
        }
    }

    /**
     * <p>b_i^{n-1}·Gamma_{ikn} <= TD_{ikn} <= b_i^n·Gamma_{ikn}; </p>
     * <p>sum_n(Gamma_{ikn}) <= 1</p>
     */
    public void addConsGamma() throws IloException {
        for (int i = 0; i < nI; i++) {
            RMSegmentFunction func = problem.getEquipments().get(i).getRentFunction();
            for (int k = 0; k < TD_ikn[i].length; k++) {
                for (int n = 0; n < TD_ikn[i][k].length; n++) {
                    cplex.addGe(TD_ikn[i][k][n], cplex.prod(func.getBreakPoint(n - 1), Gamma_ikn[i][k][n]), "consTDl%d_%d_%d".formatted(i, k, n));
                    cplex.addLe(TD_ikn[i][k][n], cplex.prod(func.getBreakPoint(n), Gamma_ikn[i][k][n]), "consTDr%d_%d_%d".formatted(i, k, n));
                }
                cplex.addLe(cplex.sum(Gamma_ikn[i][k]), 1, "consGm%d_%d".formatted(i, k));
            }
        }
    }

    public void addConsFixedJSubProblem() throws IloException {
        RMEquipment equip = problem.getEquipments().get(pricingEquipIndex);
        for (int j = 0; j < nJ; j++) {
            RMCusOrder order = problem.getCusOrders().get(j);
            if (!equip.getTypeMatchOrders().contains(order)) {
                for (IloNumVar y : Y_ijt[0][j]) {
                    cplex.addEq(y, 0);
                }
                for (IloNumVar x : X_ijw[0][j]) {
                    cplex.addEq(x, 0);
                }
                for (int g = 0; g < nJ; g++) {
                    cplex.addEq(S_ijj[0][j][g], 0);
                    cplex.addEq(S_ijj[0][g][j], 0);
                }
            } else {
                cplex.addEq(S_ijj[0][j][j], 0);
            }
        }
    }

    public void addConsFixedJL() throws IloException {
        for (int i = 0; i < nI; i++) {
            RMEquipment equip = problem.getEquipments().get(i);
            for (int j = 0; j < nJ; j++) {
                RMCusOrder order = problem.getCusOrders().get(j);
                if (!equip.getTypeMatchOrders().contains(order)) {
                    for (int w = 0; w < m_i[i]; w++) {
                        cplex.addEq(X_ijw[i][j][w], 0);
                        for (int t = 0; t < T; t++) {
                            cplex.addEq(L_yw[i][j][t][w], 0);
                        }
                    }
                    for (int g = 0; g < nJ; g++) {
                        cplex.addEq(S_ijj[i][j][g], 0);
                        cplex.addEq(S_ijj[i][g][j], 0);
                    }
                }
            }
        }
    }

    /**
     * if j, j' \in {J \ J_i},
     * <p>Y_{ijt}=0, X_{ijw}=0, S_{ijj'} = 0</p>
     */
    public void addConsFixedJ() throws IloException {
        for (int i = 0; i < nI; i++) {
            RMEquipment equip = problem.getEquipments().get(i);
            for (int j = 0; j < nJ; j++) {
                RMCusOrder order = problem.getCusOrders().get(j);
                if (!equip.getTypeMatchOrders().contains(order)) {
                    cplex.addEq(cplex.sum(Y_ijt[i][j]), 0);
                    for (int w = 0; w < m_i[i]; w++) {
                        cplex.addEq(X_ijw[i][j][w], 0);
                    }
                    for (int g = 0; g < nJ; g++) {
                        cplex.addEq(S_ijj[i][j][g], 0);
                        cplex.addEq(S_ijj[i][g][j], 0);
                    }
                } else {
                    cplex.addEq(S_ijj[i][j][j], 0);
                }
            }
        }
    }

    public void addConsBranchXFixed(CGConstraint constraint) throws IloException {
        IloLinearNumExpr cons = cplex.linearNumExpr();
        for (int w = 0; w <= constraint.wIndex; w++) {
            cons.addTerm(1, X_ijw[0][constraint.jIndex][w]);
        }
        cplex.addEq(cons, constraint.noMoreThan ? 0 : 1);
    }

    public Analysis solveByColumn() throws IloException {
        cplex.setOut(null);
        cplex.solve();
        return new Analysis(cplex, false);
    }

    public Analysis solveDefault() throws IloException {
        cplex.setOut(null);
        cplex.addMaximize(objFunction);
        cplex.solve();
        return new Analysis(cplex, false);
    }

    public String getVarConsNumber() {
        return "%d\t%d".formatted(cplex.getNintVars(), cplex.getNrows());
    }

    /**
     * @return (solution status, cplex status, mip_gap and bounds)
     */
    public Analysis solveWithCallback(String path, boolean saveFeasible) throws IloException {
        cplex.setOut(null);
        cplex.addMaximize(objFunction);
        String modelFileName = para.getExactModelName();
        if (modelFileName != null) {
            if (!modelFileName.endsWith(".sav")) {
                modelFileName += ".sav";
            }
            cplex.exportModel(modelFileName);
        }

        TimeLimitCallback callback = new TimeLimitCallback(cplex, false, cplex.getCplexTime(),
                para.getTIME_LIMIT(), para.getMIP_GAP(), path, problem.getName());
        if (saveFeasible) {
            callback.model = this;
        }
        cplex.use(callback);
        cplex.solve();

        Analysis analysis = new Analysis(cplex, true);
        analysis.T_I = callback.bestObjTime;
        return analysis;
    }

    public RMSolutionL getSolutionSPL() throws IloException {
        RMSolutionL solution = new RMSolutionL(TD_ikn[0].length, TD_ikn[0][0].length);
        solution.setObj(cplex.getObjValue());
        for (int k = 0; k < TD_ikn[0].length; k++) {
            solution.setGamma(k, cplex.getValues(Gamma_ikn[0][k]));
            solution.setTD(k, cplex.getValues(TD_ikn[0][k]));
        }
        solution.setTB_k(cplex.getValues(TB_ik[0]));
        solution.setTE_k(cplex.getValues(TE_ik[0]));
        return solution;
    }

    public RMSolutionW getSolutionSPW() throws IloException {
        RMSolutionW solution = new RMSolutionW(problem, pricingEquipIndex);
        solution.setObj(cplex.getObjValue());
        for (int j = 0; j < nJ; j++) {
            solution.setY(j, cplex.getValues(Y_ijt[0][j]));
            solution.setX(j, cplex.getValues(X_ijw[0][j]));
            solution.setS(j, cplex.getValues(S_ijj[0][j]));
        }
        solution.setHB_w(cplex.getValues(HB_iw[0]));
        solution.setHE_w(cplex.getValues(HE_iw[0]));
        return solution;
    }

    public RMSolution getMasterProblemSolutionS() throws IloException {
        RMSolution solution = new RMSolution(problem);
        // Z, L, objective value
        double[][] varU = new double[nI][];
        solution.setObj(cplex.getObjValue());
        solution.setZ_p(Tools.copySmooth(cplex.getValues(Z_p)));
        for (int i = 0; i < nI; i++) {
            varU[i] = Tools.copyCPLEXSmooth(cplex.getValues(U_is[i]));
        }
        solution.setL_is(varU);
        // duals
        solution.setDualPhi(getDualPhi());
        solution.setDualMu(Tools.copyCPLEXSmooth(cplex.getDuals(consU_i)));
        solution.setDualEta(getDualEta());
        solution.setDualSplitL(getDualSplits(consSplitLe));
        solution.setDualSplitR(getDualSplits(consSplitGe));
        return solution;
    }

    public RMSolution getMasterProblemSolution() throws IloException {
        RMSolution solution = new RMSolution(problem);
        // Z, V, U, L, objective value
        double[][] varU = new double[nI][];
        double[][] varL = new double[nI][];
        double[][] zeta = new double[nI][];
        double[][][] dualLambdaB = new double[nI][][];
        double[][][] dualLambdaE = new double[nI][][];
        double[][] dualPIw = new double[nI][];
        solution.setObj(cplex.getObjValue());
        solution.setZ_p(Tools.copySmooth(cplex.getValues(Z_p)));
        for (int i = 0; i < nI; i++) {
            varU[i] = Tools.copyCPLEXSmooth(cplex.getValues(U_is[i]));
            varL[i] = Tools.copyCPLEXSmooth(cplex.getValues(L_is[i]));
            zeta[i] = Tools.copyCPLEXSmooth(cplex.getDuals(consVXSum_iw[i]));
            dualLambdaB[i] = new double[q_i[i]][];
            dualLambdaE[i] = new double[q_i[i]][];
            dualPIw[i] = new double[m_i[i] - 1];
            for (int k = 0; k < q_i[i]; k++) {
                solution.setV(i, k, Tools.copySmooth(cplex.getValues(V_ikw[i][k])));
                dualLambdaB[i][k] = Tools.copyCPLEXSmooth(cplex.getDuals(consTBLe[i][k]));
                dualLambdaE[i][k] = Tools.copyCPLEXSmooth(cplex.getDuals(consTEGe[i][k]));
            }
        }
        solution.setU_is(varU);
        solution.setL_is(varL);
        // duals
        solution.setDualPi(Tools.copyCPLEXSmooth(cplex.getDuals(consL_i)));
        solution.setDualPIw(dualPIw);
        solution.setDualMu(Tools.copyCPLEXSmooth(cplex.getDuals(consU_i)));
        solution.setDualEta(getDualEta());
        solution.setDualZeta(zeta);
        solution.setDualDelta(Tools.copyCPLEXSmooth(cplex.getDuals(consVXSum0_i)));
        solution.setDualSumTH(Tools.copyCPLEXSmooth(cplex.getDuals(consSumTH)));
        solution.setDualXi(getDualXi());
        solution.setDualLambdaB(dualLambdaB);
        solution.setDualLambdaE(dualLambdaE);
        solution.setDualSplitL(getDualSplits(consSplitLe));
        solution.setDualSplitR(getDualSplits(consSplitGe));
        return solution;
    }

    private double[] getDualSplits(Map<Integer, IloRange> consSplits) throws IloException {
        double[] dualSplitL = new double[nJ];
        for (var entry : consSplits.entrySet()) {
            int j = entry.getKey();
            dualSplitL[j] = Tools.copyCPLEXSmooth(cplex.getDual(entry.getValue()));
        }
        return dualSplitL;
    }

    private double[][][] getDualXi() throws IloException {
        double[][][] xi = new double[nI][][];
        for (int i = 0; i < nI; i++) {
            xi[i] = new double[q_i[i] - 1][];
            for (int k = 0; k < xi[i].length; k++) {
                xi[i][k] = Tools.copyCPLEXSmooth(cplex.getDuals(consVXSeq_ikw[i][k]));
            }
        }
        return xi;
    }

    private double[][] getDualPhi() throws IloException {
        if (!para.isFlexibleTimeWindow()) {
            return null;
        }
        double[][] phi = new double[nP][para.getNFlexible()];
        for (RMFlexible flex : problem.getFlexibleOrders()) {
            int f = flex.getIndex();
            int p = flex.getAffiliation().getIndex();
            phi[p][f] = Tools.copyCPLEXSmooth(cplex.getDual(consYSumZ_pf[p][f]));
        }
        return phi;
    }

    private double[][][] getDualEta() throws IloException {
        double[][][] eta = new double[nP][nJ][T];
        for (int p = 0; p < nP; p++) {
            RMCustomer customer = problem.getCustomers().get(p);
            for (RMEntity order : customer.getComponents()) {
                for (int t = order.getTB() + 1; t <= order.getTE(); t++) {
                    eta[p][order.getIndex()][t - 1] = Tools.copyCPLEXSmooth(cplex.getDual(consYSatisfy_pjt[p][order.getIndex()][t - 1]));
                }
            }
        }
        return eta;
    }

    public RMSolution getSolution() throws IloException {
        RMSolution solution = new RMSolution(problem);

        solution.setObj(cplex.getObjValue());
        solution.setZ_p(cplex.getValues(Z_p));
        for (int i = 0; i < nI; i++) {
            if (m_i[i] == 0) continue;
            for (int j = 0; j < nJ; j++) {
                solution.setY(i, j, cplex.getValues(Y_ijt[i][j]));
                solution.setX(i, j, cplex.getValues(X_ijw[i][j]));
                solution.setS(i, j, cplex.getValues(S_ijj[i][j]));
            }
            for (int k = 0; k < q_i[i]; k++) {
                solution.setV(i, k, cplex.getValues(V_ikw[i][k]));
                solution.setGamma(i, k, cplex.getValues(Gamma_ikn[i][k]));
                solution.setTD(i, k, cplex.getValues(TD_ikn[i][k]));

            }
            solution.setHB(i, cplex.getValues(HB_iw[i]));
            solution.setHE(i, cplex.getValues(HE_iw[i]));
            solution.setTB(i, cplex.getValues(TB_ik[i]));
            solution.setTE(i, cplex.getValues(TE_ik[i]));
        }
        return solution;
    }

    /**
     * 子问题只求解一个设备
     *
     * @param equipmentIndex 设备索引
     */
    public void reduceDimensionI(int equipmentIndex) {
        nI = 1;
        pricingEquipIndex = equipmentIndex;
        m_i = new int[]{m_i[equipmentIndex]};
        q_i = new int[]{q_i[equipmentIndex]};
    }

    private void addConsBranchGammaFixed(CGConstraint constraint) throws IloException {
        IloLinearNumExpr cons = cplex.linearNumExpr();
        for (int n = 0; n <= constraint.nIndex; n++) {
            cons.addTerm(1, Gamma_ikn[0][constraint.kIndex][n]);
        }
        cplex.addEq(cons, constraint.noMoreThan ? 0 : 1);
    }

    public void addConsBranchWorkOrder(CGNode node) throws IloException {
        CGNode consNode = node;
        CGConstraint constraint;
        while (consNode != null && !consNode.isRoot) {
            constraint = consNode.getConstraint();
            if (constraint.branchVarZ) {
                addConsBranchZ(constraint);
            } else if (constraint.branchSplits) {
                addConsBranchXSplit(constraint);
            } else if (constraint.equipment.getIndex() == pricingEquipIndex) {
                if (constraint.branchService) {
                    addConsBranchServiceX(constraint);
                } else if (constraint.branchVarX) {
                    addConsBranchXFixed(constraint);
                } else if (constraint.branchVarHB) {
                    addConsBranchHB(constraint);
                } else if (constraint.branchVarHE) {
                    addConsBranchHE(constraint);
                } else if (constraint.branchVarY) {
                    cplex.addEq(Y_ijt[0][constraint.jIndex][constraint.tIndex], constraint.noMoreThan ? 0 : 1);
                }
            }
            consNode = consNode.getPredecessor();
        }
    }

    private void addConsBranchZ(CGConstraint constraint) throws IloException {
        if (constraint.noMoreThan) {
            for (RMEntity order : problem.getCustomers().get(constraint.pIndex).getComponents()) {
                for (IloNumVar Xj_w : X_ijw[0][order.getIndex()]) {
                    cplex.addEq(Xj_w, 0);
                }
            }
        }
    }

    private void addConsBranchServiceX(CGConstraint constraint) throws IloException {
        IloLinearNumExpr sumX = cplex.linearNumExpr();
        for (IloNumVar Xj_w : X_ijw[0][constraint.jIndex]) {
            sumX.addTerm(1, Xj_w);
        }
        cplex.addEq(sumX, constraint.noMoreThan ? 0 : 1);
    }

    private void addConsBranchXSplit(CGConstraint constraint) throws IloException {
        if (constraint.noMoreThan) {
            if (constraint.rightHandSide == 0) { // zero-service
                for (IloNumVar Xj_w : X_ijw[0][constraint.jIndex]) {
                    cplex.addEq(Xj_w, 0);
                }
            } else if (constraint.rightHandSide == 1) { // either zero-service or full-service
                int j = constraint.jIndex;
                IloLinearNumExpr sumT = cplex.linearNumExpr();
                IloLinearNumExpr sumW = cplex.linearNumExpr();
                for (IloNumVar Yj_t : Y_ijt[0][j]) {
                    sumT.addTerm(1, Yj_t);
                }
                for (IloNumVar Xj_w : X_ijw[0][j]) {
                    sumW.addTerm(1, Xj_w);
                }
                cplex.addGe(sumT, cplex.prod(problem.getCusOrders().get(j).getTD(), sumW));
            }
        }
    }

    private void addConsBranchHE(CGConstraint constraint) throws IloException {
        if (constraint.noMoreThan) {
            cplex.addLe(HE_iw[0][constraint.wIndex], constraint.rightHandSide);
        } else {
            cplex.addGe(HE_iw[0][constraint.wIndex], constraint.rightHandSide);
        }
    }

    private void addConsBranchHB(CGConstraint constraint) throws IloException {
        if (constraint.noMoreThan) {
            cplex.addLe(HB_iw[0][constraint.wIndex], constraint.rightHandSide);
        } else {
            cplex.addGe(HB_iw[0][constraint.wIndex], constraint.rightHandSide);
        }
    }

    public void addConsBranchLeaseTerm(CGNode node) throws IloException {
        CGNode consNode = node;
        CGConstraint constraint;
        while (consNode != null && !consNode.isRoot) {
            constraint = consNode.getConstraint();
            if (constraint.equipment != null && constraint.equipment.getIndex() == pricingEquipIndex) {
                if (constraint.branchVarGamma) {
                    addConsBranchGammaFixed(constraint);
                } else if (constraint.branchVarTB) {
                    if (constraint.noMoreThan) {
                        cplex.addLe(TB_ik[0][constraint.kIndex], constraint.rightHandSide);
                    } else {
                        cplex.addGe(TB_ik[0][constraint.kIndex], constraint.rightHandSide);
                    }
                } else if (constraint.branchVarTE) { // branchVarTE
                    if (constraint.noMoreThan) {
                        cplex.addLe(TE_ik[0][constraint.kIndex], constraint.rightHandSide);
                    } else {
                        cplex.addGe(TE_ik[0][constraint.kIndex], constraint.rightHandSide);
                    }
                }
            }
            consNode = consNode.getPredecessor();
        }
    }

    public void addConsBranchMPZ(CGNode node) throws IloException {
        CGNode consNode = node;
        CGConstraint constraint;
        while (consNode != null && !consNode.isRoot) {
            constraint = consNode.getConstraint();
            if (constraint.branchVarZ) {
                cplex.addEq(Z_p[constraint.pIndex], constraint.noMoreThan ? 0 : 1);
            }
            consNode = consNode.getPredecessor();
        }
    }

    public void addConsBranchMPVZ(CGNode node) throws IloException {
        CGNode consNode = node;
        CGConstraint constraint;
        while (consNode != null && !consNode.isRoot) {
            constraint = consNode.getConstraint();
            if (constraint.branchVarZ) {
                cplex.addEq(Z_p[constraint.pIndex], constraint.noMoreThan ? 0 : 1);
            } else if (constraint.branchVarV) {
                cplex.addEq(V_ikw[constraint.equipment.getIndex()][constraint.kIndex][constraint.wIndex],
                        constraint.noMoreThan ? 0 : 1);
            }
            consNode = consNode.getPredecessor();
        }
    }

    public void exportModel(String s) throws IloException {
        LogManager.getLogger(Model.class).warn("{} {} Status {} CplexStatus {}", s, problem.getName(), cplex.getStatus(), cplex.getCplexStatus());
        cplex.exportModel("%s_%s_%s.sav".formatted(s, problem.getName(), Tools.fileTimeStamp()));
    }

    public void end() {
        cplex.end();
    }

    public void addConsForbidSplit() throws IloException {
        for (int j = 0; j < nJ; j++) {
            IloLinearNumExpr cons = cplex.linearNumExpr();
            for (int i = 0; i < nI; i++) {
                for (int w = 0; w < m_i[i]; w++) {
                    cons.addTerm(1, X_ijw[i][j][w]);
                }
            }
            cplex.addLe(cons, 1, "consNoSplit" + j);
        }
    }

    public void initMaxObj() throws IloException {
        obj = cplex.addMaximize();
    }

    public void initConvexityU() throws IloException {
        consU_i = new IloRange[nI];
        U_is = new IloNumVar[nI][];
        for (int i = 0; i < consU_i.length; i++) {
            consU_i[i] = cplex.addRange(1, 1);
        }
    }

    public void initConvexity() throws IloException {
        consU_i = new IloRange[nI];
        consL_i = new IloRange[nI];
        U_is = new IloNumVar[nI][];
        L_is = new IloNumVar[nI][];
        consUL = new IloRange[nI][];
        for (int i = 0; i < consU_i.length; i++) {
            consU_i[i] = cplex.addRange(1, 1);
            consL_i[i] = cplex.addRange(1, 1);
        }
    }

    private void initConsSumYFulfillZ() throws IloException {
        consYSumZ_pf = new IloRange[nP][para.getNFlexible()];
        consYSatisfy_pjt = new IloRange[nP][nJ][T];
        Z_p = new IloNumVar[nP];
        for (var entry : problem.getCustomerFlex().entrySet()) {
            int p = entry.getKey().getIndex();
            IloColumn col = cplex.column(obj, entry.getKey().getServiceRevenue());
            for (RMFlexible flex : entry.getValue()) {
                int f = flex.getIndex();
                consYSumZ_pf[p][f] = cplex.addRange(0, 0, "consPF%d_%d".formatted(p, f));
                col = col.and(cplex.column(consYSumZ_pf[p][f], -flex.getMD()));
                for (RMCusOrder order : problem.getFlexFixOrder().get(flex)) {
                    int j = order.getIndex();
                    for (int t = order.getTB(); t < order.getTE(); t++) {
                        consYSatisfy_pjt[p][j][t] = cplex.addRange(0, 0, "consPJT%d_%d_%d".formatted(p, j, t));
                    }
                }
            }
            Z_p[p] = cplex.numVar(col, 0, 1, "z%d".formatted(p + 1));
        }
    }

    public void initConsYSatisfyNumZ() throws IloException {
        if (para.isFlexibleTimeWindow()) {
            initConsSumYFulfillZ();
            return;
        }
        consYSatisfy_pjt = new IloRange[nP][nJ][T];
        Z_p = new IloNumVar[nP];
        for (int p = 0; p < nP; p++) {
            RMCustomer customer = problem.getCustomers().get(p);
            IloColumn col = cplex.column(obj, customer.getServiceRevenue());
            for (RMEntity order : customer.getComponents()) {
                for (int t = order.getTB(); t < order.getTE(); t++) {
                    consYSatisfy_pjt[p][order.getIndex()][t] = cplex.addRange(0, 0);
                    col = col.and(cplex.column(consYSatisfy_pjt[p][order.getIndex()][t], -1));
                }
            }
            Z_p[p] = cplex.numVar(col, 0, 1, "z%d".formatted(p + 1));
        }
    }

    public void initConsVX() throws IloException {
        consVXSum_iw = new IloRange[nI][];
        consVXSum0_i = new IloRange[nI];
        consVXSeq_ikw = new IloRange[nI][][];
        for (int i = 0; i < nI; i++) {
            consVXSum_iw[i] = new IloRange[m_i[i]];
            for (int w = 0; w < consVXSum_iw[i].length; w++) {
                consVXSum_iw[i][w] = cplex.addRange(0, 0);
            }
            consVXSum0_i[i] = cplex.addRange(0, 0);
            consVXSeq_ikw[i] = new IloRange[q_i[i] - 1][m_i[i] - 1];
            for (int k = 0; k < consVXSeq_ikw[i].length; k++) {
                for (int w = 0; w < consVXSeq_ikw[i][k].length; w++) {
                    consVXSeq_ikw[i][k][w] = cplex.addRange(-1, Double.MAX_VALUE);
                }
            }
        }
    }

    public void initConsTVH() throws IloException {
        consSumTH = new IloRange[nI];
        consTBLe = new IloRange[nI][][];
        consTEGe = new IloRange[nI][][];
        for (int i = 0; i < nI; i++) {
            int qI = q_i[i];
            int mI = m_i[i];
            consTBLe[i] = new IloRange[qI][mI];
            consTEGe[i] = new IloRange[qI][mI];
            for (int k = 0; k < qI; k++) {
                for (int w = 0; w < mI; w++) {
                    consTBLe[i][k][w] = cplex.addRange(-Double.MAX_VALUE, T);
                    consTEGe[i][k][w] = cplex.addRange(-T, Double.MAX_VALUE);
                }
            }
            consSumTH[i] = cplex.addRange(0, Double.MAX_VALUE);
        }
    }

    public void initNumV(boolean consMerge) throws IloException {
        if (consMerge) {
            consVL_iww = new IloRange[nI][][];
        }
        V_ikw = new IloNumVar[nI][][];
        for (int i = 0; i < nI; i++) {
            int mI = m_i[i];
            int qI = q_i[i];
            V_ikw[i] = new IloNumVar[qI][mI];
            IloColumn[][] col_ik = new IloColumn[qI][mI];
            for (int w = 0; w < mI; w++) {
                for (int k = 0; k < qI; k++) {
                    col_ik[k][w] = cplex.column(consVXSum_iw[i][w], 1)
                            .and(cplex.column(consTBLe[i][k][w], T))
                            .and(cplex.column(consTEGe[i][k][w], -T));
                }
            }
            col_ik[0][0] = col_ik[0][0].and(cplex.column(consVXSum0_i[i], 1));
            for (int k = 0; k < qI - 1; k++) {
                for (int w = 0; w < mI - 1; w++) {
                    col_ik[k][w + 1] = col_ik[k][w + 1].and(cplex.column(consVXSeq_ikw[i][k][w], 1));
                    col_ik[k + 1][w + 1] = col_ik[k + 1][w + 1].and(cplex.column(consVXSeq_ikw[i][k][w], 1));
                    col_ik[k][w] = col_ik[k][w].and(cplex.column(consVXSeq_ikw[i][k][w], -1));
                }
            }
            if (consMerge) {
                initConsMergeColumnVi(i, col_ik);
            }
            for (int k = 0; k < qI; k++) {
                for (int w = 0; w < mI; w++) {
                    V_ikw[i][k][w] = cplex.numVar(col_ik[k][w], 0, 1, "v[%d_%d_%d]".formatted(i, k, w));
                }
            }
        }
    }

    private void initConsMergeColumnVi(int i, IloColumn[][] col_ik) throws IloException {
        int mI = m_i[i];
        consVL_iww[i] = new IloRange[mI][mI];
        for (int w1 = 0; w1 < mI - 1; w1++) {
            for (int w2 = w1 + 1; w2 < mI; w2++) {
                consVL_iww[i][w1][w2] = cplex.addRange(-Double.MAX_VALUE, 0);
                for (int k = 0; k < q_i[i]; k++) {
                    col_ik[k][w2] = col_ik[k][w2].and(cplex.column(consVL_iww[i][w1][w2], k));
                    col_ik[k][w1] = col_ik[k][w1].and(cplex.column(consVL_iww[i][w1][w2], -k));
                }
            }
        }
    }

    public void initUConsUL(int i, int nScheme) {
        if (U_is[i] == null) {
            U_is[i] = new IloNumVar[nScheme];
            consUL[i] = new IloRange[nScheme];
        } else if (U_is[i].length < nScheme) {
            IloNumVar[] copy = new IloNumVar[nScheme];
            System.arraycopy(U_is[i], 0, copy, 0, U_is[i].length);
            U_is[i] = copy;
            IloRange[] copyR = new IloRange[nScheme];
            System.arraycopy(consUL[i], 0, copyR, 0, consUL[i].length);
            consUL[i] = copyR;
        }
    }

    public void initU(int i, int nScheme) {
        if (U_is[i] == null) {
            U_is[i] = new IloNumVar[nScheme];
        } else if (U_is[i].length < nScheme) {
            IloNumVar[] copy = new IloNumVar[nScheme];
            System.arraycopy(U_is[i], 0, copy, 0, U_is[i].length);
            U_is[i] = copy;
        }
    }

    public void addColumnLeaseTermsU(CGSchemeLeaseTerms scheme, int i, int s) throws IloException {
        scheme.setModelIdx(s);
        consUL[i][s] = cplex.addRange(-Double.MAX_VALUE, 0);
        IloColumn col = cplex.column(obj, -scheme.totalLeasingCost).and(cplex.column(consU_i[i], 1)).and(cplex.column(consUL[i][s], -1));
        double tD = 0;
        for (int k = 0; k < q_i[i]; k++) {
            int tB = scheme.tB_k[k];
            int tE = scheme.tE_k[k];
            tD += tE - tB;
            for (int w = 0; w < m_i[i]; w++) {
                col = col.and(cplex.column(consTBLe[i][k][w], tB)).and(cplex.column(consTEGe[i][k][w], tE));
            }
        }
        col = col.and(cplex.column(consSumTH[i], tD));
        U_is[i][s] = cplex.numVar(col, 0, 1, "%s_%d_%d".formatted("U", i + 1, s + 1));
    }

    public void initL(int i, int nScheme) {
        if (L_is[i] == null) {
            L_is[i] = new IloNumVar[nScheme];
        } else if (L_is[i].length < nScheme) {
            IloNumVar[] copy = new IloNumVar[nScheme];
            System.arraycopy(L_is[i], 0, copy, 0, L_is[i].length);
            L_is[i] = copy;
        }
    }

    public void addColumnWorkLeases(CGSchemeWorkLeases scheme, int i, int s) throws IloException {
        IloColumn col = cplex.column(obj, -scheme.getTotalCost()).and(cplex.column(consU_i[i], 1));
        if (para.isFlexibleTimeWindow()) {
            for (RMWorkOrder work : scheme.getWorkOrders()) {
                RMCusOrder order = work.getCusOrder();
                int j = order.getIndex();
                int p = order.getAffiliation().getIndex();
                int tD = order.getTD();
                double hD = work.getTD();
                col = col.and(cplex.column(consYSumZ_pf[p][order.getFlexible().getIndex()], hD));
                for (int t = order.getTB(); t < order.getTE(); t++) {
                    col = col.and(cplex.column(consYSatisfy_pjt[p][j][t], tD * scheme.y_jt[j][t] - hD));
                }
                col = modColumnConsSplit(col, j);
            }
        } else {
            for (RMWorkOrder work : scheme.getWorkOrders()) {
                int j = work.getCusOrder().getIndex();
                int p = work.getCusOrder().getAffiliation().getIndex();
                for (int t = work.getTB(); t < work.getTE(); t++) {
                    col = col.and(cplex.column(consYSatisfy_pjt[p][j][t], 1));
                }
                col = modColumnConsSplit(col, j);
            }
        }
        U_is[i][s] = cplex.numVar(col, 0, 1, "L[%d_%d]".formatted(i + 1, s + 1));
    }

    public void addColumnWorkOrdersL(List<CGScheme> leaseTermsList, CGSchemeWorkOrders scheme, int i, int s, boolean consMerge) throws IloException {
        IloColumn col = cplex.column(obj, -scheme.getTotalSchedulingCost()).and(cplex.column(consL_i[i], 1));
        if (leaseTermsList.contains(scheme.getBestCoverScheme())) {
            int omegaIdx = scheme.getBestCoverScheme().modelIdx;
            col = col.and(cplex.column(consUL[i][omegaIdx], 1));
        }
        for (int w = 0; w < m_i[i]; w++) {
            int hB = scheme.getHB_w()[w];
            int hE = scheme.getHE_w()[w];
            for (int k = 0; k < q_i[i]; k++) {
                col = col.and(cplex.column(consTBLe[i][k][w], -hB)).and(cplex.column(consTEGe[i][k][w], -hE));
            }
        }
        double hD = 0;
        for (RMWorkOrder work : scheme.getWorkOrders()) {
            int j = work.getCusOrder().getIndex();
            int p = work.getCusOrder().getAffiliation().getIndex();
            hD += work.getTD();
            for (int t = work.getTB(); t < work.getTE(); t++) {
                col = col.and(cplex.column(consYSatisfy_pjt[p][j][t], 1));
            }
            int w = work.getWorkSequenceIndex();
            col = col.and(cplex.column(consVXSum_iw[i][w], -1));
            if (w == 0) {
                col = col.and(cplex.column(consVXSum0_i[i], -1));
            } else {
                for (int k = 0; k < consVXSeq_ikw[i].length; k++) {
                    col = col.and(cplex.column(consVXSeq_ikw[i][k][w - 1], -1));
                }
            }
            col = modColumnConsSplit(col, j);
        }
        col = col.and(cplex.column(consSumTH[i], -hD));
        if (consMerge) {
            col = modColumnConsMerge(i, col, scheme);
        }
        L_is[i][s] = cplex.numVar(col, 0, 1, "L[%d_%d]".formatted(i + 1, s + 1));
    }

    private IloColumn modColumnConsMerge(int i, IloColumn col, CGSchemeWorkOrders scheme) throws IloException {
        int nW = scheme.getWorkOrders().size();
        for (int w1 = 0; w1 < nW - 1; w1++) {
            for (int w2 = w1 + 1; w2 < nW; w2++) {
                int dK = (scheme.getHB_w()[w2] - scheme.getHE_w()[w1] + 1) / 2;
                col = col.and(cplex.column(consVL_iww[i][w1][w2], -dK));
            }
        }
        return col;
    }

    private IloColumn modColumnConsSplit(IloColumn col, int j) throws IloException {
        if (consSplitLe.containsKey(j)) {
            col = col.and(cplex.column(consSplitLe.get(j), 1));
        }
        if (consSplitGe.containsKey(j)) {
            col = col.and(cplex.column(consSplitGe.get(j), 1));
        }
        return col;
    }

    public void initBranchNSplits(CGNode node) throws IloException {
        consSplitLe = new LinkedHashMap<>();
        consSplitGe = new LinkedHashMap<>();
        for (int j = 0; j < nJ; j++) {
            if (node.branchUBSplit[j] < T) {
                consSplitLe.put(j, cplex.addRange(-Double.MAX_VALUE, node.branchUBSplit[j]));
            }
            if (node.branchLBSplit[j] > 0) {
                consSplitGe.put(j, cplex.addRange(node.branchLBSplit[j], Double.MAX_VALUE));
            }
        }
    }

}
