package problem;

import broker.CGNode;
import broker.ErrorCode;
import broker.Tools;
import broker.work.CGSchemeWorkOrders;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Getter
@Setter
public class RMProblem {

    private Logger logger = LogManager.getLogger(RMProblem.class);

    String name;

    RMPara para;
    /**
     * requests with flexible time frames
     */
    List<RMFlexible> flexibleOrders;
    /**
     * 客户集合
     */
    List<RMCustomer> customers = new ArrayList<>();
    /**
     * 所有客户订单集合
     */
    List<RMCusOrder> cusOrders = new ArrayList<>();
    /**
     * 供应商集合
     */
    List<RMSupplier> suppliers = new ArrayList<>();
    /**
     * 所有供应商设备集合
     */
    List<RMEquipment> equipments = new ArrayList<>();
    /**
     * 从设备初始位置到订单的距离
     */
    double[][] distIJ;
    /**
     * 从订单到订单的距离
     */
    double[][] distJJ;
    /**
     * 每个设备最大租约数 q_i
     */
    int[] qIs;
    /**
     * 每个设备最大工单数 m_i
     */
    int[] mIs;
    /**
     * 每个设备租赁成本函数段数
     */
    int[] cIs;
    /**
     * 设备类型-设备集合
     */
    Map<Integer, List<RMEquipment>> typeMatchEquips;
    Map<Integer, List<Integer>> typeTimeBorders;
    Map<RMFlexible, List<RMCusOrder>> flexFixOrder;
    Map<RMCustomer, List<RMFlexible>> customerFlex;

    ErrorCode intSolFeasible;
    RMSolutionInt solutionInt;
    List<String> solutionInfo;

    int[] nBranch = new int[11];
    int nSplitOrder;
    int nSplits;
    int nSplit1;
    int nSplit2;

    int nZ1;
    int nR1;
    double revenue;
    double rentCost;
    double scheduleCost;
    double fixedCost;
    /**
     * netProfit = revenue - rentCost - scheduleCost - setupCost
     */
    double netProfit;

    static int BASE_SCHEDULE_COST = 2;

    public void addCustomer(RMCustomer customer) {
        customers.add(customer);
    }

    public void addSupplier(RMSupplier supplier) {
        suppliers.add(supplier);
    }

    private void calSetupCost(int[][][] X) {
        fixedCost = 0;
        for (RMCusOrder order : cusOrders) {
            if (!typeMatchEquips.containsKey(order.getEquipmentType())) {
                continue;
            }
            int j = order.index;
            int NX = 0;
            for (RMEquipment equip : typeMatchEquips.get(order.getEquipmentType())) {
                NX += Arrays.stream(X[equip.index][j]).sum();
            }
            if (order.setupCost > 0) {
                fixedCost += order.setupCost * NX;
            } else if (order.switchCost > 0 && NX > 1) {
                fixedCost += order.switchCost * (NX - 1);
            }
        }
    }

    public void calScheduleCostByXS(int[][][] X, int[][][] S) {
        scheduleCost = 0;
        for (int i = 0; i < S.length; i++) {
            if (mIs[i] == 0) continue;
            for (int j = 0; j < S[i].length; j++) {
                if (X[i][j][0] == 1) {
                    scheduleCost += distIJ[i][j];
                }
                for (int l = 0; l < S[i][j].length; l++) {
                    if (S[i][j][l] == 1) {
                        scheduleCost += distJJ[j][l];
                    }
                }
            }
        }
    }

    private void calRentCost(int[][][] Gamma, int[][][] TD) {
        rentCost = 0;
        for (int i = 0; i < TD.length; i++) {
            RMSegmentFunction rentFunction = (equipments.get(i)).getRentFunction();
            for (int k = 0; k < TD[i].length; k++) {
                for (int n = 0; n < TD[i][k].length; n++) {
                    if (Gamma[i][k][n] == 1) {
                        rentCost += rentFunction.getIntercept(n);
                        rentCost += rentFunction.getSlope(n) * TD[i][k][n];
                    }
                }
            }
        }
    }

    public void calRevenue(int[] Z) {
        nZ1 = 0;
        revenue = 0;
        for (int p = 0; p < Z.length; p++) {
            if (Z[p] == 1) {
                nZ1++;
                revenue += (customers.get(p)).serviceRevenue;
            }
        }
    }

    public void setParameter(RMPara parameter) {
        this.para = parameter;
    }

    /**
     * 根据输入补全信息，包括距离矩阵、最大工单数等
     */
    public void completeInputInfo() {
        // 全部设备集合
        suppliers.stream().flatMap(supplier -> supplier.components.stream()).forEach(entity -> equipments.add((RMEquipment) entity));
        typeMatchEquips = equipments.stream().collect(Collectors.groupingBy(RMEquipment::getSubType));
        para.nEquipment = equipments.size();
        mIs = new int[para.nEquipment];
        qIs = new int[para.nEquipment];
        cIs = new int[para.nEquipment];
        for (int i = 0; i < para.nEquipment; i++) {
            RMEquipment e = equipments.get(i);
            e.index = i;
            cIs[i] = e.rentFunction.NSegment;
        }

        // 全部客户集合
        para.nCustomer = customers.size();
        for (int p = 0; p < para.nCustomer; p++) {
            RMCustomer customer = customers.get(p);
            customer.setIndex(p);
            customer.getComponents().sort(Comparator.comparingInt(RMEntity::getTB));
        }

        // 全部订单集合
        List<RMEntity> components = customers.stream().flatMap(customer -> customer.getComponents().stream()).toList();
        components.stream().filter(c -> c.getLocation() == null).forEach(c -> c.setLocation(c.affiliation.location));

        para.flexibleTimeWindow = components.stream().anyMatch(c -> ((RMFlexible) c).mD > 0);
        if (para.flexibleTimeWindow) {
            customers.forEach(RMCustomer::calAvgRevenueFlex);

            flexibleOrders = components.stream().map(c -> (RMFlexible) c).toList();
            IntStream.range(0, flexibleOrders.size()).forEach(f -> flexibleOrders.get(f).setIndex(f));

            cusOrders = new ArrayList<>();
            for (RMFlexible flex : flexibleOrders) {
                int diff = flex.lE - flex.eB - flex.mD;
                for (int n = 0; n <= diff; n++) {
                    RMCusOrder fixed = flex.getFixedOrder(flex.eB + n, flex.eB + n + flex.mD);
                    cusOrders.add(fixed);
                    flex.affiliation.addComponent(fixed);
                }
                flex.affiliation.getComponents().remove(flex);
            }
            if (cusOrders.size() == flexibleOrders.size()) {
                para.flexibleTimeWindow = false;
                flexibleOrders = null;
                cusOrders.forEach(o -> o.flexible = null);
            } else {
                para.nFlexible = flexibleOrders.size();
                customerFlex = flexibleOrders.stream().collect(Collectors.groupingBy(flex -> (RMCustomer) flex.getAffiliation()));
                flexFixOrder = cusOrders.stream().collect(Collectors.groupingBy(RMCusOrder::getFlexible));
                para.spwSolver = RMPara.SPWMethod.LABEL;
            }
        } else {
            customers.forEach(RMCustomer::calAvgRevenue);
            cusOrders = components.stream().map(c -> (RMCusOrder) c).toList();
        }
        para.nCusOrder = cusOrders.size();
        IntStream.range(0, cusOrders.size()).forEach(j -> cusOrders.get(j).setIndex(j));

        calDistMatrix();
        calTimeBorder();
    }

    private void calTimeBorder() {
        int overallNK = para.maxNumLeaseTerm > 0 ? para.maxNumLeaseTerm : para.nTimePeriod;
        typeTimeBorders = new HashMap<>();
        for (var entry : typeMatchEquips.entrySet()) {
            int type = entry.getKey();
            List<RMCusOrder> compatibleOrders = cusOrders.stream().filter(order -> order.getSubType() == type).toList();
            Set<Integer> timeBEs = new HashSet<>();
            for (RMCusOrder order : compatibleOrders) {
                timeBEs.add(order.tB);
                timeBEs.add(order.tE);
            }
            List<Integer> timeBorders = timeBEs.stream().sorted().toList();
            typeTimeBorders.put(type, timeBorders);
            int nWorkOrder = timeBorders.size() - 1; // <= T
            int mI = para.allowReVisit ? nWorkOrder : Math.min(para.nTimePeriod, compatibleOrders.size());
            int qI = Math.min(overallNK, mI);
            entry.getValue().forEach(e -> {
                e.typeMatchOrders.addAll(compatibleOrders);
                e.maxNWorkOrder = mI;
                e.maxNLeaseTerm = qI;
                mIs[e.index] = mI;
                qIs[e.index] = qI;
                e.avgNTimeBorder = compatibleOrders.stream().mapToLong(order -> timeBorders.stream().filter(t
                        -> t >= order.tB && t <= order.tE).count()).sum() / (double) compatibleOrders.size();
            });
        }
    }

    /**
     * 计算距离矩阵和时间矩阵
     */
    private void calDistMatrix() {
        distIJ = new double[para.nEquipment][para.nCusOrder];
        distJJ = new double[para.nCusOrder][para.nCusOrder];
        for (int j = 0; j < para.nCusOrder; j++) {
            RMEntity order = cusOrders.get(j);
            // d_IJ
            for (int i = 0; i < para.nEquipment; i++) {
                distIJ[i][j] = equipments.get(i).location.calDist(order.location) + BASE_SCHEDULE_COST;
            }
            // d_JJ
            for (int k = j + 1; k < para.nCusOrder; k++) {
                distJJ[j][k] = order.location.calDist(cusOrders.get(k).location) + BASE_SCHEDULE_COST;
                distJJ[k][j] = distJJ[j][k];
            }
        }
    }

    /**
     * 校验结果可行性
     */
    public ErrorCode checkFeasibility(RMSolution _solution) {
        if (_solution.obj == 0) {
            return ErrorCode.FEASIBLE_ZERO;
        }
        solutionInt = new RMSolutionInt(_solution);
        // @LXY check unity of V, X, S...
        boolean feasibleY = checkFeasibilityY(solutionInt.Y);
        if (!feasibleY) {
            return ErrorCode.INFEASIBLE_Y;
        }
        boolean feasibleZ = checkFeasibilityZ(solutionInt.Z, solutionInt.Y);
        if (!feasibleZ) {
            return ErrorCode.INFEASIBLE_Z;
        }
        boolean feasibleX = checkFeasibilityX(solutionInt.X);
        if (!feasibleX) {
            return ErrorCode.INFEASIBLE_X;
        }
        boolean feasibleXY = checkFeasibilityXY(solutionInt.X, solutionInt.Y);
        if (!feasibleXY) {
            return ErrorCode.INFEASIBLE_XY;
        }
        boolean feasibleS = checkFeasibilitySeq(solutionInt.X, solutionInt.S);
        if (!feasibleS) {
            return ErrorCode.INFEASIBLE_S;
        }
        boolean feasibleT = checkFeasibilityTH();
        if (!feasibleT) {
            return ErrorCode.INFEASIBLE_T;
        }

        // check Obj
        calRevenue(solutionInt.Z);
        calRentCost(solutionInt.Gamma, solutionInt.TD);
        calScheduleCostByXS(solutionInt.X, solutionInt.S);
        calSetupCost(solutionInt.X);
        netProfit = revenue - rentCost - scheduleCost - fixedCost;
        if (Tools.differ(Math.round(netProfit), solutionInt.obj)) {
            return ErrorCode.INFEASIBLE_OBJ;
        }
        return ErrorCode.FEASIBLE;
    }

    public boolean checkFeasibilityZ(int[] z, int[][][] y) {
        // check if Y satisfy Z
        for (int p = 0; p < z.length; p++) {
            RMCustomer customer = customers.get(p);
            if (z[p] < 1) {
                if (!checkFeasibilityZ0(y, customer)) return false;
            } else {
                if (!checkFeasibilityZ1(y, customer)) return false;
            }
        }

        return true;
    }

    private boolean checkFeasibilityZ1(int[][][] Y_ijt, RMCustomer customer) {
        List<RMCusOrder> servedOrders = new ArrayList<>();
        for (RMEntity cusOrder : customer.getComponents()) {
            int j = cusOrder.getIndex();
            Map<Integer, Integer> timeEquipMap = new HashMap<>();
            for (int t = 0; t < para.nTimePeriod; t++) {
                for (int i = 0; i < Y_ijt.length; i++) {
                    if (Y_ijt[i][j][t] > 0) {
                        timeEquipMap.put(t, i);
                        break;
                    }
                }
                if ((t < cusOrder.tB || t >= cusOrder.tE) && timeEquipMap.containsKey(t)) {
                    logger.error("(yt0) j%d,%d-%d", j, timeEquipMap.get(t), t);
                }
            }
            if (timeEquipMap.size() == cusOrder.getTD()) {
                servedOrders.add((RMCusOrder) cusOrder);
            } else if (!(para.isFlexibleTimeWindow() && timeEquipMap.isEmpty())) {
                StringBuilder sb = new StringBuilder("(yt1) j");
                sb.append(j);
                timeEquipMap.forEach((key, value) -> sb.append(",").append(value).append("-").append(key));
                logger.error(sb);
                return false;
            }
        }
        if (para.isFlexibleTimeWindow()) {
            return checkFeasibilityF1(servedOrders, customer);
        }
        return true;
    }

    private boolean checkFeasibilityF1(List<RMCusOrder> servedOrders, RMCustomer customer) {
        Map<RMFlexible, List<RMCusOrder>> flexFixMap = servedOrders.stream().collect(Collectors.groupingBy(RMCusOrder::getFlexible));
        if (flexFixMap.size() < customerFlex.get(customer).size()) {
            List<RMFlexible> flexList = new ArrayList<>(customerFlex.get(customer));
            flexList.removeAll(flexFixMap.keySet());
            StringBuilder sb = new StringBuilder("(pf1) p");
            sb.append(customer.getIndex());
            flexList.forEach(flex -> sb.append(",f").append(flex.getIndex()));
            logger.error(sb);
            return false;
        }
        for (var entry : flexFixMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                StringBuilder sb = new StringBuilder("(fj1) f");
                sb.append(entry.getKey().getIndex());
                entry.getValue().forEach(order -> sb.append(",j").append(order.getIndex()));
                logger.error(sb);
                return false;
            }
        }
        return true;
    }

    private boolean checkFeasibilityZ0(int[][][] Y_ijt, RMEntity customer) {
        for (RMEntity cusOrder : customer.getComponents()) {
            int j = cusOrder.getIndex();
            for (int i = 0; i < Y_ijt.length; i++) {
                int ySum = Arrays.stream(Y_ijt[i][j]).sum();
                if (ySum > 0) {
                    logger.error("(z0) deserted order %d served by facility %d", j, i);
                    return false;
                }
            }
        }
        return true;
    }

    public boolean checkFeasibilityY(int[][][] y) {
        // check unity of i-t
        for (int i = 0; i < y.length; i++) {
            for (int t = 0; t < para.getNTimePeriod(); t++) {
                List<Integer> jVisit = new ArrayList<>();
                for (int j = 0; j < y[i].length; j++) {
                    if (y[i][j][t] > 0) {
                        jVisit.add(j);
                    }
                }
                if (jVisit.size() > 1) {
                    String line = "(y_it) i%d at t%d serves orders {%s}".formatted(i, t, Arrays.toString(jVisit.toArray()));
                    logger.error(line);
                    return false;
                }
            }
        }

        return true;
    }

    public boolean checkFeasibilityX(int[][][] X_ijw) {
        for (int i = 0; i < X_ijw.length; i++) {
            // check unity of i-j
            if (!para.allowReVisit) {
                for (int j = 0; j < X_ijw[i].length; j++) {
                    int sumW = Arrays.stream(X_ijw[i][j]).sum();
                    if (sumW > 1) {
                        logger.error("(xij) i%d serves j%d %d times", i, j, sumW);
                        return false;
                    }
                }
            }
            // check unity of i-w
            for (int w = 0; w < X_ijw[i][0].length; w++) {
                int finalW = w;
                int finalI = i;
                List<Integer> jServed = IntStream.range(0, X_ijw[i].length).boxed().filter(j -> X_ijw[finalI][j][finalW] > 0).toList();
                if (jServed.size() > 1) {
                    String line = "(xiw) i%d at w%d serves orders {%s}".formatted(i, w, Arrays.toString(jServed.toArray()));
                    logger.error(line);
                    return false;
                }
            }
        }

        return true;
    }

    public boolean checkFeasibilityXY(int[][][] X_ijw, int[][][] Y_ijt) {
        for (int i = 0; i < Y_ijt.length; i++) {
            for (int j = 0; j < Y_ijt[i].length; j++) {
                int sumY = Arrays.stream(Y_ijt[i][j]).sum();
                int sumX = Arrays.stream(X_ijw[i][j]).sum();
                if ((sumY > 0 && sumX == 0) || (sumY == 0 && sumX > 0)) {
                    logger.error("(xy) sumY %d sumX %d i%d j%d", sumY, sumX, i, j);
                    return false;
                }
            }
        }
        return true;
    }

    public boolean checkFeasibilitySeq(int[][][] X_ijw, int[][][] S_ijj) {
        for (int i = 0; i < X_ijw.length; i++) {
            if (mIs[i] == 0) continue;
            int[] sumX_iw = new int[mIs[i]];
            for (int j = 0; j < X_ijw[i].length; j++) {
                sumX_iw[0] += X_ijw[i][j][0];
            }
            for (int w = 0; w < mIs[i] - 1; w++) {
                for (int j = 0; j < X_ijw[i].length; j++) {
                    for (int l = 0; l < X_ijw[i].length; l++) {
                        if ((X_ijw[i][j][w] + X_ijw[i][l][w + 1]) > (S_ijj[i][j][l] + 1)) {
                            logger.error("(xs) i%d, w%d:j%d, w+1:l%d, s_ilj%d", i, w, j, l, S_ijj[i][j][l]);
                            return false;
                        }
                    }
                    sumX_iw[w + 1] += X_ijw[i][j][w + 1];
                }
                if (sumX_iw[w + 1] > sumX_iw[w]) {
                    logger.error("(xww) i%d, w%d", i, w);
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkFeasibilityTH() {
        int[][] HB_iw = solutionInt.HB;
        int[][] HE_iw = solutionInt.HE;
        int[][] TB_ik = solutionInt.TB;
        int[][] TE_ik = solutionInt.TE;
        int[][][] TD_ikn = solutionInt.TD;
        int[][][] Gamma_ikn = solutionInt.Gamma;
        // 检查V匹配H和T的关系
        for (RMEquipment equip : equipments) {
            int i = equip.index;
            RMSegmentFunction function = equip.getRentFunction();
            int k = 0;
            for (int w = 0; w < HB_iw[i].length; w++) {
                int hD = HE_iw[i][w] - HB_iw[i][w];
                if (hD == 0) {
                    break;
                }
                while (k < equip.getMaxNLeaseTerm() && (TB_ik[i][k] > HB_iw[i][w] || TE_ik[i][k] < HE_iw[i][w])) {
                    k++;
                }
                if (k >= equip.getMaxNLeaseTerm()) {
                    logger.error("(ht) i%d, w%d (%d, %d]", i, w, HB_iw[i][w], HE_iw[i][w]);
                    return false;
                }
                int n = Tools.findFirstOne(0, Gamma_ikn[i][k]);
                if (TD_ikn[i][k][n] != (TE_ik[i][k] - TB_ik[i][k])
                        || TD_ikn[i][k][n] < function.getBreakPoint(n - 1)
                        || TD_ikn[i][k][n] > function.getBreakPoint(n)) {
                    logger.error("(tD) i%d, w%d (%d, %d] k%d n%d (%d, %d]", i, w, HB_iw[i][w], HE_iw[i][w], k, n, TB_ik[i][k], TE_ik[i][k]);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 遍历订单，依次完整安排在还可用的设备上
     */
    public Map<RMEquipment, List<RMWorkOrder>> greedyService() {
        Map<RMEquipment, List<RMWorkOrder>> workAssigned = new LinkedHashMap<>();
        List<RMCusOrder> ordersToTackle = new ArrayList<>(cusOrders);
        while (!ordersToTackle.isEmpty()) {
            RMCusOrder cusOrder = ordersToTackle.remove(0);
            if (!typeMatchEquips.containsKey(cusOrder.equipmentType)) {
                continue;
            }
            for (RMEquipment equip : typeMatchEquips.get(cusOrder.equipmentType)) {
                List<RMWorkOrder> assigned = workAssigned.containsKey(equip) ? workAssigned.get(equip) : new ArrayList<>();
                int insertIndex = cusOrder.insertPlace(assigned);
                if (insertIndex > Tools.INVALID_INDEX) {
                    RMWorkOrder work = new RMWorkOrder(cusOrder.tB, cusOrder.tE, cusOrder);
                    work.maxNX = 1;
                    assigned.add(insertIndex, work);
                    workAssigned.put(equip, assigned);
                    if (cusOrder.flexible != null) {
                        ordersToTackle.removeIf(o -> cusOrder.flexible.equals(o.flexible));
                    }
                    break;
                }
            }
        }
        return workAssigned;
    }

    public void translateSolution(RMSolution _solution) {
        intSolFeasible = checkFeasibility(_solution);
        if (!ErrorCode.FEASIBLE.equals(intSolFeasible)) {
            return;
        }
        solutionInt.parseLeaseTerms(this);
        solutionInt.parseWorkOrders(this);
        calNSplits();
        checkCosts();
        nR1 = equipments.stream().mapToInt(equip -> equip.leaseTerms.size()).sum();
    }

    private void checkCosts() {
        rentCost = equipments.stream().mapToDouble(RMEquipment::getRentCost).sum();
        scheduleCost = equipments.stream().mapToDouble(RMEquipment::getScheduleCost).sum();
        fixedCost = equipments.stream().mapToDouble(RMEquipment::getFixedCost).sum();
        if (Tools.differ(netProfit, revenue - rentCost - scheduleCost - fixedCost)) {
            logger.error("OBJ DIFF. CHECK EACH FACILITY!!!");
        }
    }

    /**
     * 统计拆分服务的数量
     */
    private void calNSplits() {
        nSplits = 0;
        nSplit1 = 0;
        nSplit2 = 0;
        nSplitOrder = 0;
        cusOrders.forEach(order -> countSplits(Math.max(0, order.getWorkOrders().size() - 1)));
    }

    private void countSplits(int nSplit) {
        nSplits += nSplit;
        switch (nSplit) {
            case 0:
                break;
            case 1:
                nSplit1++;
                nSplitOrder++;
                break;
            case 2:
                nSplit2++;
                nSplitOrder++;
                break;
            default:
                nSplitOrder++;
                break;
        }
    }

    public List<String> collectSolutionInfo() {
        solutionInfo = new ArrayList<>();
        if (!ErrorCode.FEASIBLE.equals(intSolFeasible)) {
            solutionInfo.add(intSolFeasible.toString());
            return solutionInfo;
        }
        solutionInfo.add(profitToString());
        solutionInfo.add(nSplitsToString());
        solutionInfo.add(">> Lease Terms and Work Orders:");
        for (int i = 0; i < para.nEquipment; i++) {
            RMEquipment equip = equipments.get(i);
            StringBuilder sb = new StringBuilder();
            sb.append("Facility ").append(equip.id).append(" L");
            equip.leaseTerms.stream().map(term -> String.format(" %s", term.timeWindowToString())).forEach(sb::append);
            equip.workOrders.stream().map(work -> String.format(" W%d J%d %s",
                    (work.workSequenceIndex + 1), work.cusOrder.id, work.timeWindowToString())).forEach(sb::append);
            solutionInfo.add(sb.toString());
        }
        solutionInfo.add(">> Service:");
        for (int p = 0; p < para.nCustomer; p++) {
            RMCustomer customer = customers.get(p);
            StringBuilder sb = new StringBuilder();
            sb.append("Customer ").append(customer.id);
            if (solutionInt.getZ()[p] == 1) {
                sb.append(" served");
                for (RMEntity entity : customer.components) {
                    RMCusOrder order = (RMCusOrder) entity;
                    if (!order.workOrders.isEmpty()) {
                        sb.append(String.format("%nOrder %d %s", order.id, order.timeWindowToString()));
                        order.workOrders.stream().map(work -> String.format(" I%d %s", work.affiliation.id, work.timeWindowToString())).forEach(sb::append);
                    }
                }
            } else {
                sb.append(" not served");
            }
            solutionInfo.add(sb.toString());
        }
        return solutionInfo;
    }

    private String nSplitsToString() {
        return String.format(">> NSplitOrder(%d), 1Split(%d), 2Split(%d), NSplits(%d)", nSplitOrder, nSplit1, nSplit2, nSplits);
    }

    private String profitToString() {
        return String.format(">> Profit(%s) = Revenue(%s) - Rent(%s) - ScheduleCost(%s) - SetupCost(%s)", Tools.df1.format(netProfit),
                Tools.df1.format(revenue), Tools.df1.format(rentCost), Tools.df1.format(scheduleCost), Tools.df1.format(fixedCost));
    }

    public List<String> collectCaseInfo() {
        List<String> caseInfo = new ArrayList<>();
        // 规模概况
        caseInfo.add(String.format("CASE %s T %d K %d N %d", name,
                para.nTimePeriod, para.maxNumLeaseTerm, para.maxNumCostSegment));
        // 客户和订单
        for (RMCustomer customer : customers) {
            caseInfo.add(String.format("%s %s", customer.toString(), customer.location.toString()));
            for (RMEntity component : customer.components) {
                RMCusOrder order = (RMCusOrder) component;
                caseInfo.add(order.toString());
            }
        }
        // 设备和价格
        for (RMSupplier supplier : suppliers) {
            supplier.rentFunctions.stream().map(RMSegmentFunction::toString).forEach(caseInfo::add);
        }
        equipments.stream().map(RMEquipment.class::cast).map(RMEquipment::toString).forEach(caseInfo::add);

        return caseInfo;
    }

    public static void setBaseScheduleCost(int baseCost) {
        BASE_SCHEDULE_COST = baseCost;
    }

    public static int getBaseScheduleCost() {
        return BASE_SCHEDULE_COST;
    }

    private void collectFlexSplits() {
        for (var entry : typeMatchEquips.entrySet()) {
            List<RMCusOrder> compatibleOrders = cusOrders.stream().filter(order -> order.getSubType() == entry.getKey()).toList();
            if (entry.getValue().size() <= 1) {
                compatibleOrders.forEach(RMCusOrder::collectSingleWorkOrder);
            } else {
                for (RMCusOrder order : compatibleOrders) {
                    List<RMCusOrder> exclusiveOrders = new ArrayList<>(compatibleOrders);
                    exclusiveOrders.removeAll(flexFixOrder.get(order.flexible));
                    exclusiveOrders.add(order);
                    Set<Integer> timeBorders = new HashSet<>();
                    for (RMCusOrder other : exclusiveOrders) {
                        timeBorders.add(other.tB);
                        timeBorders.add(other.tE);
                    }
                    List<Integer> timeSplits = timeBorders.stream().sorted().filter(t -> t >= order.tB && t <= order.tE).toList();
                    order.setCandidateWorkOrders(order.splitWorkOrders(timeSplits));
                }
            }
        }
    }

    private void collectWorkSplits() {
        for (var entry : typeMatchEquips.entrySet()) {
            int type = entry.getKey();
            List<RMCusOrder> compatibleOrders = cusOrders.stream().filter(order -> order.getSubType() == type).toList();
            if (para.forbidSplits || entry.getValue().size() <= 1) {
                compatibleOrders.forEach(RMCusOrder::collectSingleWorkOrder);
            } else {
                for (RMCusOrder order : compatibleOrders) {
                    List<Integer> timeSplits = typeTimeBorders.get(type).stream().filter(t -> t >= order.tB && t <= order.tE).toList();
                    order.setCandidateWorkOrders(order.splitWorkOrders(timeSplits));
                }
            }
        }
    }

    public void collectWorkOrderSchemes(CGNode node) {
        if (para.spwSolver == RMPara.SPWMethod.CPLEX) {
            return;
        }
        if (para.flexibleTimeWindow) {
            collectFlexSplits();
        } else {
            collectWorkSplits();
        }
        if (para.isInitNonSplit() || para.spwSolver == RMPara.SPWMethod.ENUMERATE) {
            collectCandidateSchemes(node);
        }
    }

    private void collectCandidateSchemes(CGNode node) {
        Map<Integer, List<CGSchemeWorkOrders>> equipTypeWorkSchemeMap = new LinkedHashMap<>();
        for (Integer type : typeMatchEquips.keySet()) {
            List<CGSchemeWorkOrders> extendable = new ArrayList<>();
            List<CGSchemeWorkOrders> determined = new ArrayList<>();
            // 此类型设备匹配的订单集合
            List<RMCusOrder> candidateOrders = cusOrders.stream().filter(order -> order.getSubType() == type).toList();
            for (RMCusOrder order : candidateOrders) { // 从每个订单开始扩展
                for (RMWorkOrder work : order.candidateWorkOrders) {
                    CGSchemeWorkOrders scheme0 = new CGSchemeWorkOrders();
                    List<RMWorkOrder> works = new ArrayList<>();
                    works.add(work.getCopySimple());
                    scheme0.setWorkOrders(works);
                    extendable.add(scheme0);
                }
            }
            while (!extendable.isEmpty()) {
                CGSchemeWorkOrders current = extendable.remove(0);
                determined.add(current);
                extendable.addAll(current.extendOneWork(candidateOrders, para.isAllowReVisit()));
            }
            equipTypeWorkSchemeMap.put(type, determined);
        }
        node.setEquipTypeCandidateSchemes(equipTypeWorkSchemeMap);
    }

    public String getReport() {
        return "%d\t%d\t%d\t%d\t%d\t%s\t%s\t%s\t%s".formatted(nZ1, nSplitOrder, nSplit1, nSplit2, nSplits,
                Tools.df1.format(revenue), Tools.df1.format(rentCost), Tools.df1.format(scheduleCost), Tools.df1.format(fixedCost));
    }

    public void countBranch(int branchIdx) {
        nBranch[branchIdx]++;
    }

    public String reportBranch(int length) {
        int reportN = (length < 0 || length > nBranch.length) ? nBranch.length : length;
        return Arrays.stream(nBranch).limit(reportN).mapToObj(n -> "\t" + n).collect(Collectors.joining());
    }
}
