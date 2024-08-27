package broker.work;

import broker.LeaseTermDP;
import broker.Tools;
import problem.*;

import java.util.Comparator;
import java.util.List;

public class CGLRMPSolverRepair {

    private final RMSolution ipSolution;

    private int[][][] integerY;
    private int[][][] integerX;

    public CGLRMPSolverRepair(RMSolution lpSolution) {
        ipSolution = lpSolution.getCopy();
    }

    public RMSolution solve(RMProblem problem) {
        // Z, Y取整
        double revenue = repairIntegerYZ(problem);
        // 校验可行性
        boolean feasibleY = problem.checkFeasibilityY(integerY);
        if (!feasibleY) {
            return null;
        }
        int[] integerZ = Tools.copyToInt(ipSolution.getZ_p());
        boolean feasibleZ = problem.checkFeasibilityZ(integerZ, integerY);
        if (!feasibleZ) {
            return null;
        }
        // 获得X, S, H
        double scheduleCost = repairIntegerXH(problem);
        boolean feasibleX = problem.checkFeasibilityX(integerX);
        if (!feasibleX) {
            return null;
        }
        // 更新V, T
        double rentCost = repairIntegerVT(problem, ipSolution);

        double obj = revenue - scheduleCost - rentCost;
        ipSolution.setObj(obj);

        return ipSolution;
    }

    /**
     * 根据H更新V, T和Gamma的值
     */
    private double repairIntegerVT(RMProblem problem, RMSolution solution) {
        double totalRent = 0;
        double[][] TB_ik = solution.getTB_ik();
        double[][] TE_ik = solution.getTE_ik();
        double[][][] TD_ikn = solution.getTD_ikn();
        double[][][] Gamma_ikn = solution.getGamma_ikn();
        double[][][] V_ikw = solution.getV_ikw();
        double[][][] X_ijw = solution.getX_ijw();

        // @LXY 这里就要使用一些喜闻乐见的dominance经验了

        for (int i = 0; i < X_ijw.length; i++) {
            RMEquipment equip = problem.getEquipments().get(i);
            LeaseTermDP dp = new LeaseTermDP(equip, (int) Math.round(Tools.getArraySum(solution.getX_ijw()[i])),
                    Tools.copyToInt(solution.getHB_iw()[i]), Tools.copyToInt(solution.getHE_iw()[i]));
            RMSolutionL solutionL = new RMSolutionL(equip.getMaxNLeaseTerm(), equip.getRentFunction().getNSegment(), problem.getPara().getNTimePeriod());
            dp.enumerate(solutionL);
            V_ikw[i] = Tools.copy(dp.getV());
            TB_ik[i] = Tools.copy(solutionL.getTB_k());
            TE_ik[i] = Tools.copy(solutionL.getTE_k());
            TD_ikn[i] = Tools.copy(solutionL.getTD_kn());
            Gamma_ikn[i] = Tools.copy(solutionL.getGamma_kn());
            totalRent += solutionL.getObj();
        }

        return totalRent;
    }

    /**
     * 根据Y确定S, X和H的值
     */
    private double repairIntegerXH(RMProblem problem) {
        double scheduleCost = 0;
        double[][] HB_iw = ipSolution.getHB_iw();
        double[][] HE_iw = ipSolution.getHE_iw();
        double[][][] X_ijw = ipSolution.getX_ijw();
        double[][][] S_ijj = ipSolution.getS_ijj();
        integerX = new int[X_ijw.length][][];
        for (int i = 0; i < X_ijw.length; i++) {
            RMEquipment equip = problem.getEquipments().get(i);
            CGSchemeWorkOrders scheme = new CGSchemeWorkOrders(equip, integerY[i]);
            scheduleCost += scheme.totalSchedulingCost;
            HB_iw[i] = Tools.copyToDouble(scheme.getHB_w());
            HE_iw[i] = Tools.copyToDouble(scheme.getHE_w());
            X_ijw[i] = Tools.copyToDouble(scheme.getX_jw());
            S_ijj[i] = Tools.copyToDouble(scheme.getS_jj());
            integerX[i] = Tools.copy(scheme.getX_jw());
        }
        return scheduleCost;
    }

    private double repairIntegerYZ(RMProblem problem) {
        double revenue = 0;
        double[] Z_p = ipSolution.getZ_p();
        double[][][] Y_ijt = ipSolution.getY_ijt();
        for (int p = 0; p < Z_p.length; p++) {
            RMEntity customer = problem.getCustomers().get(p);
            for (RMEntity order : customer.getComponents()) {
                int j = order.getIndex();
                for (double[][] y_jt : Y_ijt) {
                    Tools.roundToInteger(y_jt[j], Z_p[p]); // round by fraction satisfied
                }
            }
        }
        integerY = Tools.copyToInt(Y_ijt);
        // 处理10...01的场景
        // collect p in descending order by revenue
        List<RMCustomer> customers = problem.getCustomers().stream()
                .sorted(Comparator.comparingDouble(RMCustomer::getAverageRevenue).reversed())
                .toList();
        // check satisfied customers, each time updating conflicting Ys
        for (RMCustomer customer : customers) {
            int[][][] tempY = Tools.copy(integerY);
            // 一个p有多个j，exclude每个j后剩余j'不一定能被完整服务！要不断更新
            boolean feasible = customer.assureFullServiceExclusively(tempY, problem);
            if (feasible) {
                integerY = Tools.copy(tempY);
                Z_p[customer.getIndex()] = 1;
                revenue += customer.getServiceRevenue();
            } else {
                Z_p[customer.getIndex()] = 0;
                // deal with customers not served in a simple way:
                customer.clearYt(integerY);
            }
        }
        ipSolution.setY_ijt(Tools.copyToDouble(integerY));
        return revenue;
    }

}
