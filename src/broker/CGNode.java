package broker;

import broker.work.CGSchemeWorkLeases;
import broker.work.CGSchemeWorkOrders;
import lombok.Getter;
import lombok.Setter;
import problem.*;

import java.util.*;
import java.util.stream.IntStream;

import static broker.Tools.*;

@Getter
@Setter
public class CGNode {
    int id;
    /**
     * 父节点
     */
    CGNode predecessor;
    /**
     * 分支左节点
     */
    CGNode successorL;
    /**
     * 分支右节点
     */
    CGNode successorR;
    /**
     * 分支约束
     */
    CGConstraint constraint;

    int depth;

    /**
     * 是否根节点
     */
    boolean isRoot = false;

    /**
     * 是否终止搜索（剪枝）
     */
    boolean stop;

    /**
     * 因无可行解终止搜索
     */
    boolean noFeasibleSolution;
    /**
     * 因得到整数解终止搜索
     */
    boolean isIntegerSolution;
    /**
     * 因解被dominated终止搜索：lower than LB (a MAX problem)
     */
    boolean unpromising;

    /**
     * 该点最优上界
     */
    double bestUpperBound;

    /**
     * 求解租赁子问题的值（不含常数项）
     */
    double[] pricingLeaseTermsObj;
    /**
     * 求解排班子问题的值（不含常数项）
     */
    double[] pricingWorkOrdersObj;
    /**
     * 求解排班租赁子问题的值（不含常数项）
     */
    double[] pricingWorkLeasesObj;

    /**
     * 解中分数设备索引
     */
    int indexFractionEquip;
    int jIndex;
    int tIndex;
    int wIndex;
    int kIndex;
    int nIndex;
    boolean fractionalB;
    int lIndex;
    int pIndex;
    double rhs;

    boolean[] branchZ0;
    boolean[] branchZ1;
    int[] branchUBSplit;
    int[] branchLBSplit;
    boolean[][] branchService0;
    boolean[][] branchService1;

    /**
     * Create root Node
     */
    public CGNode(int _id) {
        id = _id;
    }

    /**
     * Create from father Node
     */
    public CGNode(int _id, CGNode fatherNode) {
        id = _id;
        depth = fatherNode.depth + 1;
        bestUpperBound = fatherNode.bestUpperBound;
        predecessor = fatherNode;
        branchZ0 = Tools.copy(fatherNode.branchZ0);
        branchZ1 = Tools.copy(fatherNode.branchZ1);
        branchUBSplit = Tools.copy(fatherNode.branchUBSplit);
        branchLBSplit = Tools.copy(fatherNode.branchLBSplit);
        branchService0 = Tools.copy(fatherNode.branchService0);
        branchService1 = Tools.copy(fatherNode.branchService1);
        equipWorkLeaseSchemeMap = cloneSchemeMap(fatherNode.equipWorkLeaseSchemeMap);
        equipLeaseTermSchemeMap = cloneSchemeMap(fatherNode.equipLeaseTermSchemeMap);
        equipWorkOrderSchemeMap = cloneSchemeMap(fatherNode.equipWorkOrderSchemeMap);
        equipTypeSPSRawLabelMap = cloneTypeMap(fatherNode.equipTypeSPSRawLabelMap);
        equipTypeCandidateSchemes = cloneTypeMap(fatherNode.equipTypeCandidateSchemes);
    }

    public void clean() {
        equipWorkLeaseSchemeMap = null;
        equipLeaseTermSchemeMap = null;
        equipWorkOrderSchemeMap = null;
        equipTypeSPSRawLabelMap = null;
        equipTypeCandidateSchemes = null;
    }

    /**
     * 设备-租赁策略图
     */
    Map<RMEquipment, List<CGScheme>> equipLeaseTermSchemeMap;
    /**
     * 设备-排班策略图
     */
    Map<RMEquipment, List<CGScheme>> equipWorkOrderSchemeMap;
    /**
     * 设备-排班租赁策略图
     */
    Map<RMEquipment, List<CGScheme>> equipWorkLeaseSchemeMap;

    Map<Integer, List<CGSchemeWorkLeases>> equipTypeSPSRawLabelMap;

    Map<Integer, List<CGSchemeWorkOrders>> equipTypeCandidateSchemes;

    private <E> Map<RMEquipment, List<E>> cloneSchemeMap(Map<RMEquipment, List<E>> map) {
        Map<RMEquipment, List<E>> mapClone = null;
        if (map != null) {
            mapClone = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                List<E> schemes = new ArrayList<>(entry.getValue());
                mapClone.put(entry.getKey(), schemes);
            }
        }
        return mapClone;
    }

    private <E> Map<Integer, List<E>> cloneTypeMap(Map<Integer, List<E>> map) {
        Map<Integer, List<E>> mapClone = null;
        if (map != null) {
            mapClone = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                List<E> schemes = new ArrayList<>(entry.getValue());
                mapClone.put(entry.getKey(), schemes);
            }
        }
        return mapClone;
    }

    private boolean findFractionT(double[][] T_ik, boolean[] hasFraction) {
        double minDiffHalf = HALF;
        for (int i = 0; i < hasFraction.length; i++) {
            if (!hasFraction[i]) {
                continue;
            }
            for (int k = 0; k < T_ik[i].length; k++) {
                double diffHalf = Tools.diffHalf(T_ik[i][k]);
                if (Tools.nonNegative(minDiffHalf - diffHalf)) {
                    indexFractionEquip = i;
                    kIndex = k;
                    rhs = T_ik[i][k];
                    if (diffHalf < PRECISION) {
                        return true;
                    }
                    minDiffHalf = diffHalf;
                }
            }
        }
        return kIndex > INVALID_INDEX;
    }

    private boolean findFractionGamma(double[][][] Gamma_ikn, boolean[] hasFraction) {
        double minDiffHalf = HALF;
        for (int i = 0; i < hasFraction.length; i++) {
            if (!hasFraction[i]) {
                continue;
            }
            double[][] Gamma = Gamma_ikn[i];
            for (int k = 0; k < Gamma.length; k++) {
                for (int n = 0; n < Gamma[k].length; n++) {
                    double diffHalf = Math.abs(HALF - Gamma[k][n]);
                    if (Tools.nonNegative(minDiffHalf - diffHalf)) {
                        indexFractionEquip = i;
                        kIndex = k;
                        nIndex = n;
                        if (diffHalf < PRECISION) {
                            return true;
                        }
                        minDiffHalf = diffHalf;
                    }
                }
            }
        }
        return nIndex > INVALID_INDEX;
    }

    private boolean findFractionH(double[][] H_iw, boolean[] hasFraction) {
        double minDiffHalf = HALF;
        for (int i = 0; i < hasFraction.length; i++) {
            if (!hasFraction[i]) {
                continue;
            }
            for (int w = 0; w < H_iw[i].length; w++) {
                double diffHalf = Tools.diffHalf(H_iw[i][w]);
                if (Tools.nonNegative(minDiffHalf - diffHalf)) {
                    indexFractionEquip = i;
                    wIndex = w;
                    rhs = H_iw[i][w];
                    if (diffHalf < PRECISION) {
                        return true;
                    }
                    minDiffHalf = diffHalf;
                }
            }
        }
        return wIndex > INVALID_INDEX;
    }

    private boolean findFractionY(double[][][] Y_ijt, boolean[] hasFraction) {
        double minDiffHalf = HALF;
        for (int i = 0; i < hasFraction.length; i++) {
            if (!hasFraction[i]) {
                continue;
            }
            double[][] Y = Y_ijt[i];
            for (int j = 0; j < Y.length; j++) {
                for (int t = 0; t < Y[j].length; t++) {
                    double diffHalf = Math.abs(HALF - Y[j][t]);
                    if (Tools.nonNegative(minDiffHalf - diffHalf)) {
                        indexFractionEquip = i;
                        jIndex = j;
                        tIndex = t;
                        if (diffHalf < PRECISION) {
                            return true;
                        }
                        minDiffHalf = diffHalf;
                    }
                }
            }
        }
        return tIndex > INVALID_INDEX;
    }

    private boolean findFractionX(double[][][] X_ijw, boolean[] hasFraction) {
        double minDiffHalf = HALF;
        for (int i = 0; i < hasFraction.length; i++) {
            if (!hasFraction[i]) {
                continue;
            }
            double[][] X = X_ijw[i];
            for (int j = 0; j < X.length; j++) {
                for (int w = 0; w < X[j].length; w++) {
                    double diffHalf = Math.abs(HALF - X[j][w]);
                    if (Tools.nonNegative(minDiffHalf - diffHalf)) {
                        indexFractionEquip = i;
                        jIndex = j;
                        wIndex = w;
                        if (diffHalf < PRECISION) {
                            return true;
                        }
                        minDiffHalf = diffHalf;
                    }
                }
            }
        }
        return jIndex > INVALID_INDEX;
    }

    public boolean findFractionV(RMSolution solution) {
        double[][][] V = solution.getV_ikw();
        double[][] HB = solution.getHB_iw();
        double[][] HE = solution.getHE_iw();
        double minDiffHalf = HALF;
        for (int i = 0; i < V.length; i++) {
            int w = 0;
            while (w < HB[i].length && HB[i][w] <= HE[i][w]) {
                boolean[] fractionK = new boolean[w + 1];
                boolean find = findFractionW(V[i], fractionK, w);
                if (find && !recovered(solution, i, w)) {
                    for (int k = 0; k < fractionK.length; k++) {
                        double diffHalf = Math.abs(HALF - V[i][k][w]);
                        if (fractionK[k] && Tools.nonNegative(minDiffHalf - diffHalf)) {
                            indexFractionEquip = i;
                            kIndex = k;
                            wIndex = w;
                            if (diffHalf < PRECISION) {
                                return true;
                            }
                            minDiffHalf = diffHalf;
                        }
                    }
                }
                w++;
            }
        }
        return wIndex > INVALID_INDEX;
    }

    private boolean findFractionW(double[][] Vik, boolean[] indicator, int w) {
        boolean temp = false;
        for (int k = 0; k < indicator.length; k++) {
            if (Tools.isNotInteger(Vik[k][w])) {
                indicator[k] = true;
                temp = temp || indicator[k];
            }
        }
        return temp;
    }

    private boolean recovered(RMSolution solution, int i, int w) {
        double[][] V = solution.getV_ikw()[i];
        double[] TB = solution.getTB_ik()[i];
        double[] TE = solution.getTE_ik()[i];
        double hB = solution.getHB_iw()[i][w];
        double hE = solution.getHE_iw()[i][w];
        for (int k = 0; k <= w; k++) {
            if (TB[k] == TE[k]) {
                break;
            }
            if (TB[k] <= hB && TE[k] >= hE) {
                Arrays.stream(V).forEach(Vk -> Vk[w] = 0);
                V[k][w] = 1;
                return true;
            }
        }
        return false;
    }

    public void removeInfeasibleColumnGammaFixed(Map<RMEquipment, List<CGScheme>> schemeMap) {
        int binary = constraint.noMoreThan ? 0 : 1;
        schemeMap.get(constraint.equipment).removeIf(scheme -> {
            int gamma_kn = 0;
            for (int n = 0; n <= constraint.nIndex; n++) {
                gamma_kn += scheme.getGamma_kn()[constraint.kIndex][n];
            }
            return gamma_kn != binary;
        });
    }

    public void removeInfeasibleColumnTB(Map<RMEquipment, List<CGScheme>> schemeMap) {
        if (constraint.noMoreThan) {
            schemeMap.get(constraint.equipment).removeIf(scheme ->
                    scheme.getTB_k()[constraint.kIndex] >= (constraint.rightHandSide + 1));
        } else {
            schemeMap.get(constraint.equipment).removeIf(scheme ->
                    scheme.getTB_k()[constraint.kIndex] <= (constraint.rightHandSide - 1));
        }
    }

    public void removeInfeasibleColumnTE(Map<RMEquipment, List<CGScheme>> schemeMap) {
        if (constraint.noMoreThan) {
            schemeMap.get(constraint.equipment).removeIf(scheme ->
                    scheme.getTE_k()[constraint.kIndex] >= (constraint.rightHandSide + 1));
        } else {
            schemeMap.get(constraint.equipment).removeIf(scheme ->
                    scheme.getTE_k()[constraint.kIndex] <= (constraint.rightHandSide - 1));
        }
    }

    public void removeInfeasibleColumnY(Map<RMEquipment, List<CGScheme>> schemeMap) {
        if (constraint.noMoreThan) {
            schemeMap.get(constraint.equipment).removeIf(scheme ->
                    scheme.getY_jt()[constraint.jIndex][constraint.tIndex] == 1);
        } else {
            schemeMap.get(constraint.equipment).removeIf(scheme ->
                    scheme.getY_jt()[constraint.jIndex][constraint.tIndex] == 0);
        }
    }

    public void removeInfeasibleColumnXFixed(Map<RMEquipment, List<CGScheme>> schemeMap) {
        schemeMap.get(constraint.equipment).removeIf(scheme -> scheme.serveCusOrderOrNot(constraint.jIndex, constraint.wIndex) == constraint.noMoreThan);
    }

    public void removeInfeasibleColumnHB(Map<RMEquipment, List<CGScheme>> schemeMap) {
        if (constraint.noMoreThan) {
            schemeMap.get(constraint.equipment).removeIf(scheme ->
                    scheme.getHB_w()[constraint.wIndex] >= (constraint.rightHandSide + 1));
        } else {
            schemeMap.get(constraint.equipment).removeIf(scheme ->
                    scheme.getHB_w()[constraint.wIndex] <= (constraint.rightHandSide - 1));
        }
    }

    public void removeInfeasibleColumnHE(Map<RMEquipment, List<CGScheme>> schemeMap) {
        if (constraint.noMoreThan) {
            schemeMap.get(constraint.equipment).removeIf(scheme ->
                    scheme.getHE_w()[constraint.wIndex] >= (constraint.rightHandSide + 1));
        } else {
            schemeMap.get(constraint.equipment).removeIf(scheme ->
                    scheme.getHE_w()[constraint.wIndex] <= (constraint.rightHandSide - 1));
        }
    }

    public void initWorkLease(RMProblem problem) {
        equipWorkLeaseSchemeMap = new LinkedHashMap<>();
        if (problem.getPara().isInitNonSplit()) {
            enumerateNonSplitWorkLease(problem);
            return;
        }
        RMPara para = problem.getPara();
        Map<RMEquipment, List<RMWorkOrder>> workAssigned = problem.greedyService();
        for (RMEquipment equip : problem.getEquipments()) {
            List<CGScheme> workRents = new ArrayList<>();
            // 空工单租约
            CGSchemeWorkLeases nullLeaseTerm = new CGSchemeWorkLeases(equip, para);
            workRents.add(nullLeaseTerm);

            List<RMWorkOrder> assigned = workAssigned.get(equip);
            if (assigned != null) {
                CGSchemeWorkLeases completeScheme = new CGSchemeWorkLeases(equip, para);
                completeScheme.completeSchemeWork(assigned);
                completeScheme.completeSchemeRent();
                workRents.add(completeScheme);
            }
            equipWorkLeaseSchemeMap.put(equip, workRents);
        }
    }

    /**
     * 按规则生成初始列（租约和工单）
     */
    public void initRentAndWork(RMProblem problem) {
        equipLeaseTermSchemeMap = new LinkedHashMap<>();
        equipWorkOrderSchemeMap = new LinkedHashMap<>();
        if (problem.getPara().isInitNonSplit()) {
            enumerateNonSplitWork(problem);
            return;
        }
        RMPara para = problem.getPara();
        Map<RMEquipment, List<RMWorkOrder>> workAssigned = problem.greedyService();
        // 每个设备添加空工单，有排班的再生成工单
        for (RMEquipment equip : problem.getEquipments()) {
            CGSchemeLeaseTerms nullLeaseTerm = new CGSchemeLeaseTerms(equip, para);
            nullLeaseTerm.initNullRent(); // 空租约
            CGSchemeLeaseTerms fullLeaseTerm = new CGSchemeLeaseTerms(equip, para);
            fullLeaseTerm.initFullRent(); // 满租约
            List<CGScheme> leaseTerms = new ArrayList<>();
            leaseTerms.add(nullLeaseTerm);
            leaseTerms.add(fullLeaseTerm);

            List<CGScheme> workOrders = new ArrayList<>();
            CGSchemeWorkOrders nullWorkOrder = new CGSchemeWorkOrders(equip, para);
            workOrders.add(nullWorkOrder);

            CGSchemeWorkOrders greedyWorkOrder = new CGSchemeWorkOrders(equip, para);
            List<RMWorkOrder> assigned = workAssigned.get(equip);
            if (assigned != null) {
                // greedy scheme of work orders
                greedyWorkOrder.completeScheme(assigned);
                workOrders.add(greedyWorkOrder);

                // best scheme of lease terms for greedy work orders
                LeaseTermDP dp = new LeaseTermDP(equip, assigned.size(), greedyWorkOrder.getHB_w(), greedyWorkOrder.getHE_w());
                List<RMLeaseTerm> terms = dp.enumerate();
                CGSchemeLeaseTerms bestLeaseTerm = new CGSchemeLeaseTerms(equip, para, terms);
                CGSchemeLeaseTerms existedLeaseTerm = bestLeaseTerm.existed(leaseTerms);
                if (existedLeaseTerm == null) {
                    leaseTerms.add(bestLeaseTerm);
                } else {
                    bestLeaseTerm = existedLeaseTerm;
                }
                greedyWorkOrder.setBestCoverScheme(bestLeaseTerm);
                bestLeaseTerm.bestCoveredSchemes.add(greedyWorkOrder);
            }
            equipLeaseTermSchemeMap.put(equip, leaseTerms);
            equipWorkOrderSchemeMap.put(equip, workOrders);
        }
    }

    public boolean findFractionalWork(RMSolution solution) {
        // 优先分支排班相关变量
        double[][] L_is = solution.getL_is();
        boolean[] hasFraction = new boolean[L_is.length];
        boolean found = findFraction(L_is, hasFraction);
        if (found) {
            // 对设备i对订单j是否服务分支：sum_w(X_ijw) \in {0, 1}
            if (findFractionServiceX(solution.getX_ijw(), hasFraction)) {
                return true;
            }
            // 依次查找分数值 X Y HB or HE
            if (findFractionX(solution.getX_ijw(), hasFraction)) {
                return true;
            }
            if (findFractionH(solution.getHB_iw(), hasFraction)) {
                fractionalB = true;
                return true;
            }
            if (findFractionH(solution.getHE_iw(), hasFraction)) {
                return true;
            }
            return findFractionY(solution.getY_ijt(), hasFraction);
        }
        return false;
    }

    public boolean findFractionalRent(RMSolution solution) {
        double[][] U_is = solution.getU_is();
        boolean[] hasFractionU = new boolean[U_is.length];
        boolean foundU = findFraction(U_is, hasFractionU);
        if (foundU) {
            // 依次查找分数值Gamma TB or TE
            if (findFractionGamma(solution.getGamma_ikn(), hasFractionU)) {
                return true;
            }
            if (findFractionT(solution.getTB_ik(), hasFractionU)) {
                fractionalB = true;
                return true;
            }
            return findFractionT(solution.getTE_ik(), hasFractionU);
        }
        return false;
    }

    public boolean findFractionalCustomerOrOrder(RMSolution solution) {
        // 初始化
        indexFractionEquip = INVALID_INDEX;
        jIndex = INVALID_INDEX;
        tIndex = INVALID_INDEX;
        wIndex = INVALID_INDEX;
        kIndex = INVALID_INDEX;
        nIndex = INVALID_INDEX;
        pIndex = INVALID_INDEX;
        fractionalB = false;
        if (findFractionZ(solution.getZ_p())) {
            return true;
        }
        // 对订单j的服务次数分支：sum_iw(X_ijw) \in Z
        return findFractionXSplit(solution.getX_ijw());
    }

    private boolean findFraction(double[][] array, boolean[] indicator) {
        boolean temp = false;
        for (int i = 0; i < indicator.length; i++) {
            indicator[i] = Arrays.stream(array[i]).anyMatch(Tools::isNotInteger);
            temp = temp || indicator[i];
        }
        return temp;
    }

    private boolean findFractionZ(double[] Z) {
        double minDiffHalf = HALF;
        for (int p = 0; p < Z.length; p++) {
            double diffHalf = Math.abs(HALF - Z[p]);
            if (Tools.nonNegative(minDiffHalf - diffHalf)) {
                pIndex = p;
                if (diffHalf < PRECISION) {
                    return true;
                }
                minDiffHalf = diffHalf;
            }
        }
        return pIndex > INVALID_INDEX;
    }

    private boolean findFractionServiceX(double[][][] X_ijw, boolean[] hasFraction) {
        double minDiffHalf = HALF;
        for (int i = 0; i < hasFraction.length; i++) {
            if (!hasFraction[i]) {
                continue;
            }
            for (int j = 0; j < X_ijw[i].length; j++) {
                double diffHalf = Math.abs(HALF - Tools.getArraySum(X_ijw[i][j]));
                if (Tools.nonNegative(minDiffHalf - diffHalf)) {
                    indexFractionEquip = i;
                    jIndex = j;
                    if (diffHalf < PRECISION) {
                        return true;
                    }
                    minDiffHalf = diffHalf;
                }
            }
        }
        return jIndex > INVALID_INDEX;
    }

    private boolean findFractionXSplit(double[][][] X_ijw) {
        int nJ = X_ijw[0].length;
        double minDiffHalf = HALF;
        for (int j = 0; j < nJ; j++) {
            double NX = 0;
            for (double[][] Xi : X_ijw) {
                NX += Tools.getArraySum(Xi[j]);
            }
            double diffHalf = Tools.diffHalf(NX);
            if (Tools.nonNegative(minDiffHalf - diffHalf)) { // minDiffHalf > diffHalf + 1e-3
                jIndex = j;
                rhs = NX;
                if (diffHalf < PRECISION) {
                    return true;
                }
                minDiffHalf = diffHalf;
            }
        }
        return jIndex > INVALID_INDEX;
    }

    private void removeInfeasibleColumnSplitsCandidate(RMCusOrder cusOrder) {
        if (equipTypeCandidateSchemes == null) {
            return;
        }
        if (constraint.noMoreThan) {
            if (constraint.rightHandSide == 0) { // zero-service
                equipTypeCandidateSchemes.get(cusOrder.getSubType()).removeIf(scheme -> scheme.serveCusOrderOrNot(constraint.jIndex));
            } else if (constraint.rightHandSide == 1) { // either zero-service or full-service
                equipTypeCandidateSchemes.get(cusOrder.getSubType()).removeIf(scheme -> {
                    RMWorkOrder work = scheme.getWorkServingCusOrder(constraint.jIndex);
                    if (work == null) {
                        return false;
                    }
                    return work.getTD() < cusOrder.getTD();
                });
            } else if (constraint.rightHandSide == 2) {
                equipTypeCandidateSchemes.get(cusOrder.getSubType()).removeIf(scheme -> {
                    RMWorkOrder work = scheme.getWorkServingCusOrder(constraint.jIndex);
                    if (work == null) {
                        return false;
                    }
                    return work.getMaxNX() > constraint.rightHandSide && work.getTB() != cusOrder.getTB() && work.getTE() != cusOrder.getTE();
                });
            }
        } else {
            equipTypeCandidateSchemes.get(cusOrder.getSubType()).removeIf(scheme -> {
                RMWorkOrder work = scheme.getWorkServingCusOrder(constraint.jIndex);
                if (work == null) {
                    return false;
                }
                return work.getMaxNX() > 0 && work.getMaxNX() < constraint.rightHandSide; // valid but have inadequate splits
            });
        }
    }

    public void removeInfeasibleColumnFlexExclusive(RMProblem problem, Map<RMEquipment, List<CGScheme>> schemeMap) {
        if (constraint.noMoreThan || !problem.getPara().isFlexibleTimeWindow()) {
            return;
        }
        if (constraint.branchSplits || constraint.branchService || constraint.branchVarX || constraint.branchVarY) {
            RMCusOrder order = problem.getCusOrders().get(constraint.jIndex);
            List<RMCusOrder> others = new ArrayList<>(problem.getFlexFixOrder().get(order.getFlexible()));
            others.remove(order);
            for (List<CGScheme> schemes : schemeMap.values()) {
                schemes.removeIf(scheme -> scheme.workOrders.stream().anyMatch(work -> others.contains(work.getCusOrder())));
            }
        }
    }

    public void removeInfeasibleColumnXSplits(RMCusOrder cusOrder, Map<RMEquipment, List<CGScheme>> schemeMap) {
        if (constraint.noMoreThan) {
            if (constraint.rightHandSide == 0) { // zero-service
                schemeMap.forEach((key, schemes) -> schemes.removeIf(scheme -> scheme.serveCustomerOrNot(constraint.jIndex)));
            } else if (constraint.rightHandSide == 1) { // either zero-service or full-service
                schemeMap.forEach((key, schemes) -> schemes.removeIf(scheme -> {
                    int hD = Arrays.stream(scheme.getY_jt()[constraint.jIndex]).sum();
                    return hD > 0 && hD < cusOrder.getTD();
                }));
            } else if (constraint.rightHandSide == 2) {
                schemeMap.forEach((key, schemes) -> schemes.removeIf(scheme -> scheme.workOrders.stream().anyMatch(work ->
                        work.getCusOrder().getIndex() == constraint.jIndex && work.getTB() != cusOrder.getTB() && work.getTE() != cusOrder.getTE())));
            }
        } else {
            schemeMap.forEach((key, schemes) -> schemes.removeIf(scheme -> scheme.workOrders.stream().anyMatch(work ->
                    work.getCusOrder().getIndex() == constraint.jIndex && work.getMaxNX() < constraint.rightHandSide)));
        }
        removeInfeasibleColumnSplitsCandidate(cusOrder);
    }

    public void initBranchCons(RMProblem problem) {
        RMPara para = problem.getPara();
        int nP = para.getNCustomer();
        int nI = para.getNEquipment();
        int nJ = para.getNCusOrder();
        int nT = para.getNTimePeriod();

        branchZ0 = new boolean[nP];
        branchZ1 = new boolean[nP];
        branchUBSplit = new int[nJ];
        branchLBSplit = new int[nJ];
        branchService0 = new boolean[nI][nJ];
        branchService1 = new boolean[nI][nJ];
        Arrays.fill(branchUBSplit, para.isForbidSplits() ? 1 : nT);
        for (RMCustomer customer : problem.getCustomers()) {
            List<Integer> typeList = customer.getComponents().stream().map(RMEntity::getSubType).toList();
            if (!problem.getTypeMatchEquips().keySet().containsAll(typeList)) {
                branchZ0[customer.getIndex()] = true;
            }
        }
    }

    public void removeInfeasibleColumnServiceX(Map<RMEquipment, List<CGScheme>> schemeMap) {
        schemeMap.get(constraint.equipment).removeIf(scheme -> scheme.serveCusOrderOrNot(constraint.jIndex) == constraint.noMoreThan);
    }

    public void removeInfeasibleColumnZ(Map<RMEquipment, List<CGScheme>> workSchemeMap) {
        if (constraint.noMoreThan) {
            workSchemeMap.forEach((key, schemes) -> schemes.removeIf(scheme -> scheme.serveCustomerOrNot(constraint.pIndex)));
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(id).append("] ");
        if (constraint != null) {
            sb.append(constraint);
        }
        return sb.toString();
    }

    /**
     * branch on Order (number of splits)
     */
    public void branchOnCusOrder(CGNode childNodeL, CGNode childNodeR, RMProblem problem) {
        problem.countBranch(1);
        RMCusOrder order = problem.getCusOrders().get(jIndex);
        int ceil = (int) Math.ceil(rhs);
        CGConstraint consL = new CGConstraint(true, jIndex, ceil - 1);
        CGConstraint consR = new CGConstraint(false, jIndex, ceil);
        System.out.printf("NJ[%d] %d _ %d%n", jIndex, ceil - 1, ceil);
        childNodeL.constraint = consL;
        childNodeR.constraint = consR;
        childNodeL.updateBranchSplit();
        childNodeR.updateBranchSplit();
        childNodeR.branchZ1[order.getAffiliation().getIndex()] = true;
        successorL = childNodeL;
        successorR = childNodeR;
    }

    public void branchOnCustomer(CGNode childNodeL, CGNode childNodeR, RMProblem problem) {
        problem.countBranch(0);
        RMCustomer customer = problem.getCustomers().get(pIndex);
        CGConstraint consL = new CGConstraint(true, pIndex);
        CGConstraint consR = new CGConstraint(false, pIndex);
        System.out.printf("Z[%d]%n", pIndex);
        childNodeL.constraint = consL;
        childNodeR.constraint = consR;
        childNodeL.updateBranchZ0(customer);
        childNodeR.updateBranchZ1(customer, problem);
        successorL = childNodeL;
        successorR = childNodeR;
    }

    private void updateBranchSplit() {
        int j = constraint.jIndex;
        if (constraint.noMoreThan) {
            branchUBSplit[j] = Math.min(branchUBSplit[j], constraint.rightHandSide);
        } else {
            branchLBSplit[j] = Math.max(branchLBSplit[j], constraint.rightHandSide);
        }
    }

    private void updateBranchZ1(RMCustomer customer, RMProblem problem) {
        branchZ1[constraint.pIndex] = true;
        List<RMEntity> ordersToServe = new ArrayList<>(customer.getComponents());
        if (problem.getPara().isFlexibleTimeWindow()) {
            ordersToServe.removeIf(o -> problem.getFlexFixOrder().get(((RMCusOrder) o).getFlexible()).size() > 1);
        }
        ordersToServe.forEach(order -> branchLBSplit[order.getIndex()] = Math.max(1, branchLBSplit[order.getIndex()]));
    }

    private void updateBranchZ0(RMCustomer customer) {
        branchZ0[constraint.pIndex] = true;
        customer.getComponents().forEach(order -> {
            branchUBSplit[order.getIndex()] = 0;
            IntStream.range(0, branchService0.length).forEachOrdered(i -> branchService0[i][order.getIndex()] = true);
        });
    }

    public void branchWithEquip(CGNode childNodeL, CGNode childNodeR, RMProblem problem) {
        RMEquipment equip = problem.getEquipments().get(indexFractionEquip);
        CGConstraint consL = new CGConstraint(equip);
        CGConstraint consR = new CGConstraint(equip);

        if (tIndex > INVALID_INDEX) { // Y_ijt
            problem.countBranch(6);
            consL.setBranchVarY(true, jIndex, tIndex);
            consR.setBranchVarY(false, jIndex, tIndex);
            System.out.printf("Y[%d][%d][%d]%n", indexFractionEquip, jIndex, tIndex);
        } else if (nIndex > INVALID_INDEX) { // Gamma_ikn
            problem.countBranch(7);
            consL.setBranchVarGamma(true, kIndex, nIndex);
            consR.setBranchVarGamma(false, kIndex, nIndex);
            System.out.printf("Gamma[%d][%d][%d]%n", indexFractionEquip, kIndex, nIndex);
        } else if (jIndex > INVALID_INDEX) { // X_ijw
            if (wIndex > INVALID_INDEX) {
                problem.countBranch(3);
                consL.setBranchVarX(true, jIndex, wIndex);
                consR.setBranchVarX(false, jIndex, wIndex);
                System.out.printf("X[%d][%d][%d]%n", indexFractionEquip, jIndex, wIndex);
            } else { // X_ij service
                problem.countBranch(2);
                consL.setBranchService(true, jIndex);
                consR.setBranchService(false, jIndex);
                System.out.printf("X[%d][%d] service%n", indexFractionEquip, jIndex);
            }
        } else if (kIndex > INVALID_INDEX) {
            if (wIndex > INVALID_INDEX) { // V_ikw
                problem.countBranch(10);
                consL.setBranchVarV(true, kIndex, wIndex);
                consR.setBranchVarV(false, kIndex, wIndex);
                System.out.printf("V[%d][%d][%d]%n", indexFractionEquip, kIndex, wIndex);
            } else if (fractionalB) {  // TB_ik
                problem.countBranch(8);
                int ceil = (int) Math.ceil(rhs);
                consL.setBranchVarTB(true, kIndex, ceil - 1);
                consR.setBranchVarTB(false, kIndex, ceil);
                System.out.printf("TB[%d][%d] >= %d%n", indexFractionEquip, kIndex, ceil);
            } else { // TE_ik
                problem.countBranch(9);
                int floor = (int) Math.floor(rhs);
                consL.setBranchVarTE(true, kIndex, floor);
                consR.setBranchVarTE(false, kIndex, floor + 1);
                System.out.printf("TE[%d][%d] <= %d%n", indexFractionEquip, kIndex, floor);
            }
        } else if (wIndex > INVALID_INDEX) {
            if (fractionalB) { // HB_iw
                problem.countBranch(4);
                int ceil = (int) Math.ceil(rhs);
                consL.setBranchVarHB(true, wIndex, ceil - 1);
                consR.setBranchVarHB(false, wIndex, ceil);
                System.out.printf("HB[%d][%d] >= %d%n", indexFractionEquip, wIndex, ceil);
            } else { // HE_iw
                problem.countBranch(5);
                int floor = (int) Math.floor(rhs);
                consL.setBranchVarHE(true, wIndex, floor);
                consR.setBranchVarHE(false, wIndex, floor + 1);
                System.out.printf("HE[%d][%d] <= %d%n", indexFractionEquip, wIndex, floor);
            }
        }

        childNodeL.constraint = consL;
        childNodeR.constraint = consR;
        childNodeL.updateBranchService(problem);
        childNodeR.updateBranchService(problem);
        successorL = childNodeL;
        successorR = childNodeR;
    }

    private void updateBranchService(RMProblem problem) {
        if (constraint.branchService) {
            int i = constraint.equipment.getIndex();
            int j = constraint.jIndex;
            if (constraint.noMoreThan) {
                branchService0[i][j] = true;
            } else {
                branchService1[i][j] = true;
                RMCusOrder order = problem.getCusOrders().get(j);
                branchZ1[order.getAffiliation().getIndex()] = true;
                branchLBSplit[j] = Math.max(1, branchLBSplit[j]);
            }
        }
    }

    public void updateBranchExclusive(RMProblem problem) {
        if (constraint.noMoreThan || !problem.getPara().isFlexibleTimeWindow()) {
            return;
        }
        if (constraint.branchSplits || constraint.branchService || constraint.branchVarX || constraint.branchVarY) {
            RMCusOrder order = problem.getCusOrders().get(constraint.jIndex);
            List<RMCusOrder> others = new ArrayList<>(problem.getFlexFixOrder().get(order.getFlexible()));
            others.remove(order);
            for (RMCusOrder other : others) {
                int j = other.getIndex();
                branchUBSplit[j] = 0;
                IntStream.range(0, branchService0.length).forEach(i -> branchService0[i][j] = true);
            }
        }
    }

    private void enumerateNonSplitWork(RMProblem problem) {
        RMPara para = problem.getPara();
        for (var entry : equipTypeCandidateSchemes.entrySet()) {
            List<RMEquipment> candidateEquip = problem.getTypeMatchEquips().get(entry.getKey());
            List<CGSchemeWorkOrders> candidateRawSchemes = entry.getValue();
            for (RMEquipment equip : candidateEquip) {
                CGSchemeLeaseTerms nullLeaseTerm = new CGSchemeLeaseTerms(equip, para);
                nullLeaseTerm.initNullRent(); // 空租约
                CGSchemeLeaseTerms fullLeaseTerm = new CGSchemeLeaseTerms(equip, para);
                fullLeaseTerm.initFullRent(); // 满租约
                List<CGScheme> leaseTerms = new ArrayList<>();
                leaseTerms.add(nullLeaseTerm);
                leaseTerms.add(fullLeaseTerm);

                List<CGScheme> workOrders = new ArrayList<>();
                CGSchemeWorkOrders nullWorkOrder = new CGSchemeWorkOrders(equip, para);
                workOrders.add(nullWorkOrder);

                // enumerate candidate workOrderSchemes and generate best leaseTermScheme
                for (CGSchemeWorkOrders scheme : candidateRawSchemes) {
                    if (scheme.getWorkOrders().stream().anyMatch(work -> work.getMaxNX() > 1)) {
                        continue;
                    }
                    CGSchemeWorkOrders completeScheme = new CGSchemeWorkOrders(equip, para);
                    completeScheme.completeScheme(scheme.getWorkOrders());
                    workOrders.add(completeScheme);

                    LeaseTermDP dp = new LeaseTermDP(equip, scheme.getWorkOrders().size(), completeScheme.getHB_w(), completeScheme.getHE_w());
                    List<RMLeaseTerm> terms = dp.enumerate();
                    CGSchemeLeaseTerms bestLeaseTerm = new CGSchemeLeaseTerms(equip, para, terms);
                    CGSchemeLeaseTerms existedLeaseTerm = bestLeaseTerm.existed(leaseTerms);
                    if (existedLeaseTerm == null) {
                        leaseTerms.add(bestLeaseTerm);
                    } else {
                        bestLeaseTerm = existedLeaseTerm;
                    }
                    scheme.setBestCoverScheme(bestLeaseTerm);
                    bestLeaseTerm.bestCoveredSchemes.add(scheme);
                }
                equipLeaseTermSchemeMap.put(equip, leaseTerms);
                equipWorkOrderSchemeMap.put(equip, workOrders);
            }
        }
        if (para.getSpwSolver() != RMPara.SPWMethod.ENUMERATE) {
            equipTypeCandidateSchemes = null;
        }
    }

    private void enumerateNonSplitWorkLease(RMProblem problem) {
        RMPara para = problem.getPara();
        for (var entry : equipTypeCandidateSchemes.entrySet()) {
            List<RMEquipment> candidateEquip = problem.getTypeMatchEquips().get(entry.getKey());
            List<CGSchemeWorkOrders> candidateRawSchemes = entry.getValue();
            for (RMEquipment equip : candidateEquip) {
                List<CGScheme> workRents = new ArrayList<>();
                // 空工单租约
                CGSchemeWorkLeases nullLeaseTerm = new CGSchemeWorkLeases(equip, para);
                workRents.add(nullLeaseTerm);

                // enumerate candidate workOrderSchemes and generate best leaseTermScheme
                for (CGSchemeWorkOrders scheme : candidateRawSchemes) {
                    if (scheme.getWorkOrders().stream().anyMatch(work -> work.getMaxNX() > 1)) {
                        continue;
                    }
                    CGSchemeWorkLeases completeScheme = new CGSchemeWorkLeases(equip, para);
                    completeScheme.completeSchemeWork(scheme.getWorkOrders());
                    completeScheme.completeSchemeRent();
                    workRents.add(completeScheme);
                }
                equipWorkLeaseSchemeMap.put(equip, workRents);
            }
        }
        if (para.getSpwSolver() != RMPara.SPWMethod.ENUMERATE) {
            equipTypeCandidateSchemes = null;
        }
    }

    public boolean columnFeasible(CGSchemeLeaseTerms scheme) {
        CGNode curNode = this;
        CGConstraint curCons;
        while (curNode != null && !curNode.isRoot) {
            curCons = curNode.constraint;
            if (curCons.equipment != null && curCons.equipment.equals(scheme.equipment) &&
                    (curCons.branchVarGamma && columnInfeasibleGamma(scheme.gamma_kn[curCons.kIndex], curCons)) ||
                    curCons.branchVarTE && columnInfeasibleT(curCons, scheme.tE_k) ||
                    curCons.branchVarTB && columnInfeasibleT(curCons, scheme.tB_k)) {
                return false;
            }
            curNode = curNode.predecessor;
        }
        return true;
    }

    private boolean columnInfeasibleGamma(int[] gamma_k, CGConstraint curCons) {
        int binary = curCons.noMoreThan ? 0 : 1;
        int gamma = 0;
        for (int n = 0; n <= curCons.nIndex; n++) {
            gamma += gamma_k[n];
        }
        return gamma != binary;
    }

    private boolean columnInfeasibleT(CGConstraint curCons, int[] t_k) {
        if (curCons.noMoreThan) {
            return t_k[curCons.kIndex] > curCons.rightHandSide;
        } else {
            return t_k[curCons.kIndex] < curCons.rightHandSide;
        }
    }
}
