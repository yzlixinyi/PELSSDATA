package broker;

import problem.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CaseRandom {

    private final int nT;
    private int nI;
    private final int nJ;
    private final int nK;
    private final int nN;

    private int nodeID;

    private long seed;
    private Random random;

    private int mapScale = 10;

    private static final double REVENUE_BASE = 500.0;
    private static final double REVENUE_BASE_DAILY = 3000.0;
    private static final double REVENUE_CEIL_DAILY = 4000.0;

    private Map<String, List<Integer>> typeRentDay;
    private Map<String, List<Integer>> typeRentMonth;
    private List<Integer> types;

    RMPara para;

    public CaseRandom(int _T, int _I, int _J, int _K, int _N) {
        nT = _T;
        nI = _I;
        nJ = _J;
        nK = _K;
        nN = _N;
        para = new RMPara();
        para.setNTimePeriod(nT);
        para.setMaxNumLeaseTerm(nK);
        para.setMaxNumCostSegment(nN);
    }

    public void setSeed(int _seed) {
        seed = _seed;
    }

    private void generateRequests(RMProblem problem, int tD) {
        for (int j = 0; j < nJ; j++) {
            RMCusOrder order = new RMCusOrder(j + 1);
            int tB = random.nextInt(nT - tD + 1);
            order.setTimeRange(tB, tB + tD);

            RMCustomer customer = new RMCustomer(j + 1);
            RMNode node = randomNode();
            node.useManhattanDist();
            customer.setLocation(node);

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
            RMNode node = randomNode();
            node.useManhattanDist();
            customer.setLocation(node);
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
            RMCusOrder order = new RMCusOrder(j + 1);
            order.setEquipmentType(types.get(j));
            int[] range = randomDemandTimeWindow();
            order.setTimeRange(range[0], range[1]);
            orders.add(order);
        }
        for (int j = types.size(); j < nJ; j++) {
            RMCusOrder order = new RMCusOrder(j + 1);
            order.setEquipmentType(types.get(random.nextInt(types.size())));
            int[] range = randomDemandTimeWindow();
            order.setTimeRange(range[0], range[1]);
            orders.add(order);
        }
        return orders;
    }

    private RMNode randomNode() {
        nodeID++;
        int x = random.nextInt(11) * mapScale;
        int y = random.nextInt(11) * mapScale;
        return new RMNode(nodeID, x, y);
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

    public RMSupplier replicateGByDayMonth(int caseIdx) throws IOException {
        random = new Random(seed + caseIdx);
        nI = nJ;

        typeRentDay = CaseIO.readRent("./yun/rentByDay.txt");
        typeRentMonth = CaseIO.readRent("./yun/rentByMonth.txt");
        List<String> typeStrings = new ArrayList<>(typeRentDay.keySet());
        typeStrings = typeStrings.stream().sorted(Comparator.comparingInt(s
                -> typeRentDay.get(s).size()).reversed()).collect(Collectors.toList()).subList(0, (int) Math.ceil(nJ / 4.0));

        // 供应商和设备
        RMSupplier supplier = new RMSupplier(1);
        types = new ArrayList<>();
        for (int i = 0; i < nI; i++) {
            RMEquipment equip = new RMEquipment(i + 1);
            equip.affiliated(supplier);
            String typeString = typeStrings.get(random.nextInt(typeStrings.size()));
            RMSegmentFunction gsf = generateFunctionsByDayMonthRent(typeString, i + 1);
            types.add(gsf.getEquipmentType());
            equip.setRentFunction(gsf);
            equip.setTimeRange(0, nT);
            RMNode eNode = randomNode();
            eNode.useManhattanDist();
            equip.setLocation(eNode);
            supplier.addComponent(equip);
            supplier.addRentFunction(gsf);
        }

        return supplier;
    }

    public RMProblem replicateTDHetero(int caseIdx, int tD) throws IOException {
        RMProblem problem = new RMProblem();

        // 问题规模
        problem.setParameter(para);
        RMProblem.setBaseScheduleCost((int) (mapScale * 0.2));

        problem.addSupplier(replicateGByDayMonth(caseIdx));

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

    public RMProblem replicate1(int caseIdx) throws IOException {
        random = new Random(seed + caseIdx);

        RMProblem problem = new RMProblem();
        problem.setName(recordCaseName(caseIdx));

        // 问题规模
        problem.setParameter(para);
        RMProblem.setBaseScheduleCost((int) (mapScale * 0.2));

        int nTypes = (int) Math.ceil(nI / 4.0);
        typeRentDay = CaseIO.readRent("./yun/rentByDay.txt");
        typeRentMonth = CaseIO.readRent("./yun/rentByMonth.txt");
        List<String> typeStrings = new ArrayList<>(typeRentDay.keySet());
        Collections.shuffle(typeStrings, random);
        typeStrings = typeStrings.subList(0, Math.min(nTypes, 10));
        types = typeStrings.stream().map(this::translateToInteger).collect(Collectors.toList());

        // 订单
        List<RMCusOrder> orders = generateOrders(types);
        // 客户
        generateCustomers(problem, orders);
        for (RMCustomer customer : problem.getCustomers()) {
            int nST = customer.getComponents().stream().mapToInt(RMEntity::getTD).sum();
            double deviation = (random.nextInt(11) + 15) / 20.0;
            double revenue = nST * REVENUE_BASE_DAILY * deviation;
            customer.setRevenue(revenue);
        }

        // 供应商
        RMSupplier supplier = new RMSupplier(1);
        for (int i = 0; i < nI; i++) {
            RMEquipment equip = new RMEquipment(i + 1);
            equip.affiliated(supplier);
            String typeString = typeStrings.get(random.nextInt(typeStrings.size()));
            RMSegmentFunction gsf = generateFunctionsByDayMonthRent(typeString, i + 1);
            equip.setRentFunction(gsf);
            equip.setTimeRange(0, nT);
            RMNode eNode = randomNode();
            eNode.useManhattanDist();
            equip.setLocation(eNode);
            supplier.addComponent(equip);
            supplier.addRentFunction(gsf);
        }
        problem.addSupplier(supplier);

        problem.completeInputInfo();

        return problem;
    }

    private RMSegmentFunction generateFunctionsByDayMonthRent(String typeString, int gID) {
        int type = translateToInteger(typeString);
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
        IntStream.range(0, breaks.length).forEach(n -> breaks[n] += 0.5);
        gsf.setSlops(alphas);
        gsf.setIntercepts(betas);
        gsf.setBreakPoints(breaks);
        return gsf;
    }

    private int translateToInteger(String s) {
        String[] seq = s.split("-");
        int type = 0;
        for (int i = 0; i < 3; i++) {
            int v = Integer.parseInt(seq[i].substring(1));
            type += v * Math.pow(10, 4.0 - i);
        }
        type += Integer.parseInt(seq[3].substring(1));
        return type;
    }
}
