package problem;

import broker.Tools;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public class RMSolutionInt {

    int obj;

    int[] Z;
    int[][][] Y;
    int[][][] S;

    int[][] TB;
    int[][] TE;
    int[][][] TD;
    int[][][] Gamma;

    int[][][] X;
    int[][] HB;
    int[][] HE;

    RMSolutionInt(RMSolution solution) {
        obj = (int) Math.round(solution.obj);

        Z = Tools.copyToInt(solution.getZ_p());
        Y = Tools.copyToInt(solution.getY_ijt());
        S = Tools.copyToInt(solution.getS_ijj());

        TB = Tools.copyToInt(solution.getTB_ik());
        TE = Tools.copyToInt(solution.getTE_ik());
        TD = Tools.copyToInt(solution.getTD_ikn());
        Gamma = Tools.copyToInt(solution.getGamma_ikn());

        X = Tools.copyToInt(solution.getX_ijw());
        HB = Tools.copyToInt(solution.getHB_iw());
        HE = Tools.copyToInt(solution.getHE_iw());
    }

    public void parseWorkOrders(RMProblem problem) {
        problem.cusOrders.forEach(order -> order.workOrders.clear());
        for (int i = 0; i < problem.para.nEquipment; i++) {
            RMEquipment equip = problem.equipments.get(i);
            List<RMWorkOrder> works = new ArrayList<>();
            RMEntity previous = equip;
            for (int w = 0; w < equip.getMaxNWorkOrder(); w++) {
                if (HE[i][w] == HB[i][w]) {
                    break;
                }
                for (RMCusOrder order : equip.getTypeMatchOrders()) {
                    if (X[i][order.getIndex()][w] == 1) {
                        RMWorkOrder work = new RMWorkOrder(w, equip, order, previous);
                        work.setTimeRange(HB[i][w], HE[i][w]);
                        previous = order;
                        works.add(work);
                        order.addService(work);
                        break;
                    }
                }
            }
            equip.setWorkOrders(works);
            equip.calScheduleCost();
        }
        problem.cusOrders.forEach(order -> order.workOrders.sort(Comparator.comparingInt(RMWorkOrder::getTB)));
    }

    public void parseLeaseTerms(RMProblem problem) {
        for (int i = 0; i < problem.para.nEquipment; i++) {
            RMEquipment equip = problem.equipments.get(i);
            List<RMLeaseTerm> rents = new ArrayList<>();
            for (int k = 0; k < equip.getMaxNLeaseTerm(); k++) {
                if (TE[i][k] == TB[i][k]) {
                    break;
                }
                RMLeaseTerm rent = new RMLeaseTerm(equip, TB[i][k], TE[i][k], Gamma[i][k]);
                rents.add(rent);
            }
            equip.setLeaseTerms(rents);
            equip.calRentCost();
        }
    }

}
