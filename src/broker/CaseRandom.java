package broker;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import problem.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Setter
public class CaseRandom {

    private final int nT;
    private int nI;
    private int nType = 1;
    private int nJ;
    private int nK = -1;
    private int nN;

    private long seed;
    private Random random;

    private int mapScale = 10;
    private static final int GRID_NUM = 11;
    private static final double REVENUE_BASE = 500.0;
    private static final double REVENUE_BASE_DAILY = 3000.0;
    private static final double REVENUE_CEIL_DAILY = 4000.0;

    private Map<String, List<Integer>> typeRentDay;
    private Map<String, List<Integer>> typeRentMonth;

    RMPara para;

    @Getter
    private int nFlex;
    private int flexMod;
    private int flexTex;
    private int flexRmd;
    private int setupCost;
    private int switchCost;

    public CaseRandom(int _T, int _I, int _J, int _N) {
        nT = _T;
        nI = _I;
        nJ = _J;
        nN = _N;
        para = new RMPara();
        para.setNTimePeriod(nT);
        para.setMaxNumLeaseTerm(nK);
        para.setMaxNumCostSegment(nN);
    }

    public void setRentData(RentData data) {
        typeRentDay = data.typeRentDay;
        typeRentMonth = data.typeRentMonth;
    }

    private void generateRequests(RMProblem problem, int tD) {
        for (int j = 0; j < nJ; j++) {
            RMCusOrder order = new RMFlexible(j + 1);
            int tB = random.nextInt(nT - tD + 1);
            order.setTimeRange(tB, tB + tD);

            RMCustomer customer = new RMCustomer(j + 1);
            customer.setLocation(randomNode());

            order.affiliated(customer);
            customer.addComponent(order);
            problem.addCustomer(customer);
        }
    }

    private void generateCustomers(RMProblem problem, List<RMCusOrder> orders) {
        int nP = (int) Math.ceil(nJ / 2.0);
        List<Integer> js = IntStream.range(1, nJ).boxed().collect(Collectors.toList());
        Collections.shuffle(js, random);
        List<Integer> idxJp = js.subList(0, nP - 1).stream().sorted().collect(Collectors.toList());
        idxJp.add(0, 0);
        idxJp.add(nJ);
        for (int p = 0; p < nP; p++) {
            RMCustomer customer = new RMCustomer(p + 1);
            customer.setLocation(randomNode());
            List<RMCusOrder> chosen = orders.subList(idxJp.get(p), idxJp.get(p + 1));
            for (RMCusOrder order : chosen) {
                order.affiliated(customer);
                customer.addComponent(order);
            }
            customer.setRevenue(chosen.size() * nT * REVENUE_BASE);
            problem.addCustomer(customer);
        }
    }

    private List<RMCusOrder> generateOrders(List<Integer> types) {
        List<RMCusOrder> orders = new ArrayList<>();
        for (int j = 0; j < types.size(); j++) {
            RMCusOrder order = new RMFlexible(j + 1);
            order.setEquipmentType(types.get(j));
            int[] range = randomDemandTimeWindow();
            order.setTimeRange(range[0], range[1]);
            orders.add(order);
        }
        for (int j = types.size(); j < nJ; j++) {
            RMCusOrder order = new RMFlexible(j + 1);
            order.setEquipmentType(types.get(random.nextInt(types.size())));
            int[] range = randomDemandTimeWindow();
            order.setTimeRange(range[0], range[1]);
            orders.add(order);
        }
        return orders;
    }

    private RMNode randomNode() {
        int x = random.nextInt(GRID_NUM) * mapScale;
        int y = random.nextInt(GRID_NUM) * mapScale;
        return new RMNode(x, y, true);
    }

    private int[] randomDemandTimeWindow() {
        int maxD;
        if (nT <= 5) {
            maxD = 3;
        } else if (nT <= 10) {
            maxD = 5;
        } else {
            maxD = 7;
        }
        int tD = random.nextInt(maxD) + 1;
        int tB = random.nextInt(nT - tD + 1);
        return new int[]{tB, tB + tD};
    }

    public String getTIJSeed(int caseIdx) {
        return String.format("T%d_I%d_J%d_S%d", nT, nI, nJ, seed + caseIdx);
    }

    private String recordCaseName(int caseIdx) {
        return String.format("T%d_I%d_J%d_K%d_N%d_S%d", nT, nI, nJ, nK, nN, seed + caseIdx);
    }

    public void setTimeLimit(double _minutes) {
        para.setTIME_LIMIT((int) (_minutes * 60));
    }

    public String getScale() {
        return String.format("[%d_%d_%d_%d_%d]", nT, nI, nJ, nK, nN);
    }

    public void setMapScale(int _scale) {
        mapScale = _scale;
        para.setMapScale(_scale);
    }

    public void setRootNodeEnd() {
        para.setRootNodeEnd(true);
    }

    public RMSupplier replicateGByDayMonth(int caseIdx) {
        random = new Random(seed + caseIdx);
        nI = nJ;

        List<String> typeStrings = new ArrayList<>(typeRentDay.keySet());
        typeStrings = typeStrings.stream().sorted(Comparator.comparingInt(s
                -> typeRentDay.get(s).size()).reversed()).toList().subList(0, (int) Math.ceil(nJ / 4.0));

        // 供应商和设备
        return generateSupplier(typeStrings);
    }

    public RMProblem replicateTDHetero(int caseIdx, int tD) {
        RMProblem problem = new RMProblem();

        // 问题规模
        problem.setParameter(para);
        RMProblem.setBaseScheduleCost((int) (mapScale * 0.2));

        RMSupplier supplier = replicateGByDayMonth(caseIdx);
        problem.addSupplier(supplier);

        List<Integer> types = new ArrayList<>();
        supplier.getComponents().forEach(e -> types.add(((RMEquipment) e).getRentFunction().getEquipmentType()));

        // 每个订单对应一个客户，订单需求时长固定，同类型
        generateRequests(problem, tD);
        for (int j = 0; j < nJ; j++) {
            RMCusOrder order = (RMCusOrder) problem.getCustomers().get(j).getComponents().get(0);
            order.setEquipmentType(types.get(j));
        }
        for (RMCustomer customer : problem.getCustomers()) {
            int nST = customer.getComponents().stream().mapToInt(RMEntity::getTD).sum();
            double revenue = nST * REVENUE_CEIL_DAILY;
            customer.setRevenue(revenue);
        }

        problem.completeInputInfo();
        problem.setName(recordCaseName(caseIdx));

        return problem;
    }

    public RMProblem replicate1(int caseIdx) {
        random = new Random(seed + caseIdx);

        RMProblem problem = new RMProblem();
        problem.setName(recordCaseName(caseIdx));

        // 问题规模
        problem.setParameter(para);
        RMProblem.setBaseScheduleCost((int) (mapScale * 0.2));

        nType = (int) Math.ceil(nI / 4.0);
        List<String> typeStrings = new ArrayList<>(typeRentDay.keySet());
        Collections.shuffle(typeStrings, random);
        typeStrings = typeStrings.subList(0, Math.min(nType, 10));
        List<Integer> types = typeStrings.stream().map(Tools::translateToInteger).toList();

        // 订单
        List<RMCusOrder> orders = generateOrders(types);
        if (para.isFlexibleTimeWindow()) {
            flex(orders);
        }
        if (setupCost > 0) {
            orders.forEach(order -> order.setSetupCost(setupCost));
        }
        if (switchCost > 0) {
            orders.forEach(order -> order.setSwitchCost(switchCost));
        }
        // 客户
        generateCustomers(problem, orders);
        for (RMCustomer customer : problem.getCustomers()) {
            int nST = customer.getComponents().stream().mapToInt(RMEntity::getTD).sum();
            double deviation = (random.nextInt(11) + 15) / 20.0;
            double revenue = nST * REVENUE_BASE_DAILY * deviation;
            customer.setRevenue(revenue);
        }

        // 供应商
        problem.addSupplier(generateSupplier(typeStrings));

        problem.completeInputInfo();

        return problem;
    }

    private void flex(List<RMCusOrder> orders) {
        nFlex = 0;
        for (int j = 0; j < orders.size(); j++) {
            RMCusOrder order = orders.get(j);
            RMFlexible flex = (RMFlexible) order;
            flex.setMD(order.getTD());
            if ((j + 1) % flexMod < flexRmd) {
                nFlex++;
                if (flexTex == 1) {
                    if (order.getTE() < nT) {
                        flex.setEB(order.getTB());
                        flex.setLE(order.getTE() + 1);
                    } else {
                        flex.setEB(order.getTB() - 1);
                        flex.setLE(order.getTE());
                    }
                } else {
                    if (nT >= (order.getTD() + 2)) {
                        if (order.getTE() == nT) {
                            flex.setEB(order.getTB() - 2);
                            flex.setLE(nT);
                        } else if (order.getTB() == 0) {
                            flex.setEB(0);
                            flex.setLE(order.getTE() + 2);
                        } else {
                            flex.setEB(order.getTB() - 1);
                            flex.setLE(order.getTE() + 1);
                        }
                    } else {
                        flex.setEB(Math.max(0, order.getTB() - 1));
                        flex.setLE(Math.min(nT, order.getTE() + 1));
                        LogManager.getLogger(CaseRandom.class).warn("can not flex J{} mod 2", order.getId());
                    }
                }
            } else {
                flex.setEB(order.getTB());
                flex.setLE(order.getTE());
            }
        }
    }

    private RMSupplier generateSupplier(List<String> typeStrings) {
        RMSupplier supplier = new RMSupplier(1);
        for (int i = 0; i < nI; i++) {
            RMEquipment equip = new RMEquipment(i + 1);
            equip.setIndex(i);
            equip.affiliated(supplier);
            String typeString = typeStrings.get(random.nextInt(typeStrings.size()));
            RMSegmentFunction gsf = generateFunctionsByDayMonthRent(typeString, i + 1);
            equip.setRentFunction(gsf);
            equip.setTimeRange(0, nT);
            equip.setLocation(randomNode());
            supplier.addComponent(equip);
            supplier.addRentFunction(gsf);
        }
        return supplier;
    }

    private RMSegmentFunction generateFunctionsByDayMonthRent(String typeString, int gID) {
        int type = Tools.translateToInteger(typeString);
        RMSegmentFunction gsf = new RMSegmentFunction(gID, nN, type);
        List<Integer> dayRents = typeRentDay.get(typeString);
        List<Integer> monthRents = typeRentMonth.get(typeString);
        int gD = dayRents.get(random.nextInt(dayRents.size()));
        int gM = monthRents.get(random.nextInt(monthRents.size()));
        int dB = (int) Math.ceil((double) nT / nN);
        double dA = Math.ceil(Math.min(gD, 2 * (gD - gM / 30.0)) / (nN - 1));
        double[] alphas = new double[nN];
        double[] betas = new double[nN];
        double[] breaks = new double[nN];
        alphas[0] = gD;
        betas[0] = 0;
        Arrays.setAll(breaks, n -> (n + 1) * dB);
        for (int n = 1; n < nN; n++) {
            alphas[n] = Math.max(alphas[n - 1] - dA, 0);
            betas[n] = dA * breaks[n - 1] + betas[n - 1];
        }
        gsf.setSlops(alphas);
        gsf.setIntercepts(betas);
        gsf.setBreakPoints(breaks);
        return gsf;
    }

    public void setRevisit(int revisit) {
        if (revisit == 0) {
            para.setForbidSplits(true);
        } else if (revisit > 1) {
            para.setAllowReVisit(true);
        }
    }

    public void setFlexible(int tex, int mod, int rmd) {
        para.setFlexibleTimeWindow(true);
        flexMod = mod;
        flexTex = tex;
        flexRmd = rmd;
    }
}
