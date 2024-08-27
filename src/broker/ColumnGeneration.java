package broker;

import broker.work.CGLRMPSolverRepair;
import ilog.concert.IloException;
import problem.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static broker.Tools.INVALID_INDEX;

public abstract class ColumnGeneration {
    /**
     * 算法开始时间
     */
    private long algoStartTime;
    /**
     * 计算耗时(ms)
     */
    private int CAL_TIME = 0;
    /**
     * 当前最优解节点索引
     */
    protected int optimalNodeIdx = Tools.INVALID_INDEX;
    /**
     * 搜索点数
     */
    protected int nNodes;
    /**
     * 经过求解的点数
     */
    protected int nExplored;
    /**
     * 次梯度处理退化的次数
     */
    protected int degenerationLoop;
    /**
     * 处理退化的时间(ms)
     */
    protected long lagrangeTime;
    /**
     * 求解限制主问题线性松弛的时间(ms)
     */
    protected long simplexTime;
    /**
     * 求解限制主问题线性松弛的次数
     */
    protected int simplexN;
    /**
     * 子问题列生成次数
     */
    protected int pricingN;

    /**
     * 最优上界对应主问题解（存储对偶）
     */
    protected RMSolution bestUpperBoundDual;
    /**
     * 当前拉格朗日问题解
     */
    protected RMSolution lagrangianSolution;
    /**
     * 当前主问题的解
     */
    protected RMSolution rmpSolution;
    /**
     * Feasible integer solution
     */
    protected RMSolution solution;

    /**
     * 子问题求解状态
     */
    protected PricingStatus[] subProblemStatus;

    protected double integerObjValue = 0;

    /**
     * 此问题上界 v(RMP) + sum_i{v(SP_i)} >= v(Lagrange) >= v(RMP)
     */
    protected double dualBound;

    /**
     * (UB-LB)/|UB|
     */
    protected double GAP = 1;
    protected double LB = 0;
    protected double UB;
    /**
     * 运行时间超过限制
     */
    protected boolean stopByTime;
    /**
     * Gap在可接受范围内
     */
    protected boolean stopByGap;

    protected boolean ifAddNewColumn;
    protected boolean ifGenOldColumn;
    protected boolean ifDegeneration = false;
    /**
     * node list for iteration (filtered)
     */
    protected List<CGNode> nodesFiltered;
    protected RMPara para;
    protected RMProblem problem;

    protected String rootReport;

    protected String feasibleSolutionPath;

    protected String branchDecisionPath;

    protected abstract String getDetailedTime();

    protected abstract void solveNode(CGNode node) throws IloException, IOException;

    protected abstract boolean isInteger(CGNode node);

    protected abstract void initSpec();

    protected abstract void initRootNodeColumn(CGNode node);

    protected abstract void removeInfeasibleColumns(CGNode node);

    public RMSolution solve(RMProblem rmProblem) throws IloException, IOException {
        algoStartTime = System.currentTimeMillis();

        problem = rmProblem;
        para = rmProblem.getPara();

        nodesFiltered = new ArrayList<>();
        solution = new RMSolution(problem);

        nNodes = 1;
        CGNode rootNode = new CGNode(0);
        rootNode.setBestUpperBound(Integer.MAX_VALUE);
        rootNode.initBranchCons(problem);
        rootNode.setRoot(true); // 根节点
        // 生成初始列
        problem.collectWorkOrderSchemes(rootNode);
        initRootNodeColumn(rootNode);
        initSpec();

        solveNode(rootNode);
        UB = rootNode.getBestUpperBound();
        updateGap();
        rootReport = String.format("%s\t%s\t%d", Tools.df1.format(LB), Tools.df6.format(GAP), calTimeMillis());
        reportUpperBound(rootNode.getId());

        if (!(rootNode.isStop() || para.isRootNodeEnd())) {
            // 分支定界
            switch (para.getSearchMethod()) {
                case BEST_UB_FIRST -> {
                    decideBranches(rootNode, problem);
                    reportBranchDecision(rootNode);
                    nodesFiltered.add(0, rootNode.getSuccessorR());
                    nodesFiltered.add(0, rootNode.getSuccessorL());
                    ufs();
                }
                case DEPTH_FIRST -> {
                    decideBranches(rootNode, problem);
                    nodesFiltered.add(0, rootNode.getSuccessorR());
                    nodesFiltered.add(0, rootNode.getSuccessorL());
                    dfs();
                }
                case BREADTH_FIRST -> {
                    nodesFiltered.add(rootNode);
                    decideBranches(rootNode, problem);
                    nodesFiltered.add(rootNode.getSuccessorL());
                    nodesFiltered.add(rootNode.getSuccessorR());
                    bfs();
                }
            }
        }

        CAL_TIME = calTimeMillis();
        String stopType;
        if (stopByTime) {
            stopType = "by Time ";
        } else if (stopByGap) {
            stopType = "by Gap ";
        } else if (para.isRootNodeEnd()) {
            stopType = "at Root";
        } else {
            GAP = 0;
            stopType = "Optimal ";
        }

        System.out.println("Stop " + stopType + CAL_TIME + " ms, GAP " + Tools.df6.format(GAP) + " Best node " + optimalNodeIdx);

        return solution;
    }

    /**
     * 按规则分支
     */
    private void decideBranches(CGNode node, RMProblem problem) {
        // 找到节点子项
        System.out.println("Decide branches.");

        CGNode childNodeLeft = new CGNode(nNodes, node);
        nNodes++;
        CGNode childNodeRight = new CGNode(nNodes, node);
        nNodes++;
        System.out.printf("Node %d and Node %d | ", childNodeLeft.getId(), childNodeRight.getId());
        node.clean();

        // 子项约束设置
        if (node.getPIndex() > INVALID_INDEX) {
            node.branchOnCustomer(childNodeLeft, childNodeRight, problem);
        } else if (node.getIndexFractionEquip() == INVALID_INDEX) {
            node.branchOnCusOrder(childNodeLeft, childNodeRight, problem);
        } else {
            node.branchWithEquip(childNodeLeft, childNodeRight, problem);
        }
        childNodeRight.updateBranchExclusive(problem);
    }

    /**
     * MP no solution / gap 0 / time out
     */
    protected boolean stopByMP(CGNode node) throws IOException {
        if (rmpSolution == null) {
            // infeasible, unbounded, ...
            node.setNoFeasibleSolution(true);
            node.setStop(true);
            return true;
        }
        // optimal or feasible
        node.setIntegerSolution(isInteger(node));
        node.setStop(node.isIntegerSolution());
        if (node.isIntegerSolution()) {
            integerObjValue = rmpSolution.getObj();
            checkLB(node, rmpSolution, false);
        } else if (para.isRepairIntegrality() && !para.isFlexibleTimeWindow()) {
            CGLRMPSolverRepair repair = new CGLRMPSolverRepair(rmpSolution);
            RMSolution repairedSolution = repair.solve(problem);
            checkLB(node, repairedSolution, true);
        }
        return stop();
    }

    /**
     * update integer solution and lower bound
     */
    protected void checkLB(CGNode node, RMSolution integerSolution, boolean repaired) throws IOException {
        if (integerSolution == null) {
            return;
        }
        if (Tools.nonNegative(integerSolution.getObj() - LB)) {
            LB = integerSolution.getObj();
            System.out.println(">> Update LB " + Tools.df1.format(LB));
            updateGap();
            optimalNodeIdx = node.getId();
            solution = integerSolution.getCopy();
            reportLowerBound(node.getId(), repaired);
            if (feasibleSolutionPath != null) {
                CaseIO.writeFeasibleSolution(integerSolution, problem, feasibleSolutionPath);
            }
        }
    }

    protected void checkColumnStatus() {
        ifAddNewColumn = Arrays.stream(subProblemStatus).anyMatch(status -> status == PricingStatus.NEW_COLUMN_GENERATED);
        ifGenOldColumn = Arrays.stream(subProblemStatus).anyMatch(status -> status == PricingStatus.OLD_COLUMN_GENERATED);
        ifDegeneration = !ifAddNewColumn && ifGenOldColumn;
    }

    private void updateGap() {
        double diff = UB - LB;
        if (diff < Tools.PRECISION) {
            GAP = 0;
        } else if (LB < Tools.PRECISION) {
            GAP = 1;
        } else {
            GAP = 1 - LB / UB;
        }
        System.out.printf("UB %s LB %s GAP %s%n", Tools.df1.format(UB), Tools.df1.format(LB), Tools.df4.format(GAP));
    }

    private void greedyUBFlex(CGNode node, RMProblem problem) {
        int BASE_SCHEDULE_COST = RMProblem.getBaseScheduleCost();
        double minSumRent = 0;
        double minSumDist = 0;
        boolean[] branchZ0 = node.getBranchZ0();
        List<RMCustomer> candidateCustomers = problem.getCustomers().stream()
                .filter(customer -> !branchZ0[customer.getIndex()]).toList();
        double maxRevenue = candidateCustomers.stream().mapToDouble(RMCustomer::getServiceRevenue).sum();
        List<RMFlexible> candidateOrders = new ArrayList<>();
        candidateCustomers.forEach(customer -> candidateOrders.addAll(problem.getCustomerFlex().get(customer)));
        Map<Integer, List<RMFlexible>> typeOrderMap = candidateOrders.stream().collect(Collectors.groupingBy(RMFlexible::getSubType));
        for (var entry : typeOrderMap.entrySet()) {
            int type = entry.getKey();
            List<RMFlexible> substituteOrders = entry.getValue();
            int minEB = substituteOrders.stream().mapToInt(RMFlexible::getEB).summaryStatistics().getMin();
            int maxLE = substituteOrders.stream().mapToInt(RMFlexible::getLE).summaryStatistics().getMax();
            int maxTD = maxLE - minEB;
            List<RMEquipment> substituteEquips = problem.getTypeMatchEquips().get(type);
            double minRentRate = substituteEquips.stream().mapToDouble(e -> e.getRentFunction().getRent(maxTD)).min().orElse(0) / maxTD;
            minSumRent += minRentRate * substituteOrders.stream().mapToDouble(RMFlexible::getMD).sum();
            for (RMFlexible order : substituteOrders) {
                double minDist = substituteEquips.stream().mapToDouble(e -> order.getLocation().calDist(e.getLocation())).min().orElse(0);
                if (Tools.nonNegative(minDist)) {
                    double dist = substituteOrders.stream().filter(o -> !o.equals(order)).mapToDouble(o -> order.getLocation().calDist(o.getLocation())).min().orElse(0);
                    if (dist < minDist) {
                        minDist = dist;
                    }
                }
                minSumDist += (minDist + BASE_SCHEDULE_COST);
            }
        }
        double greedyObj = maxRevenue - minSumRent - minSumDist;
        if (greedyObj < node.bestUpperBound) {
            node.bestUpperBound = greedyObj;
            if (node.isRoot()) {
                UB = greedyObj;
            }
        }
    }

    protected void greedyUB(CGNode node, RMProblem problem) {
        if (!para.isGreedyUpperBound() || node.depth > para.getNodeDepthGreedyUB()) {
            return;
        }
        if (para.isFlexibleTimeWindow()) {
            greedyUBFlex(node, problem);
            return;
        }
        double[][] d_ij = problem.getDistIJ();
        double[][] d_jj = problem.getDistJJ();
        double minSumRent = 0;
        double minSumDist = 0;
        boolean[] branchZ0 = node.getBranchZ0();
        List<RMCustomer> candidateCustomers = problem.getCustomers().stream()
                .filter(customer -> !branchZ0[customer.getIndex()]).toList();
        double maxRevenue = candidateCustomers.stream().mapToDouble(RMCustomer::getServiceRevenue).sum();
        List<RMCusOrder> candidateOrders = new ArrayList<>();
        candidateCustomers.forEach(customer -> customer.getComponents().stream().map(RMCusOrder.class::cast).forEach(candidateOrders::add));
        Map<Integer, List<RMCusOrder>> typeOrderMap = candidateOrders.stream().collect(Collectors.groupingBy(RMCusOrder::getSubType));
        for (var entry : typeOrderMap.entrySet()) {
            int type = entry.getKey();
            List<RMCusOrder> substituteOrders = entry.getValue();
            int minTB = substituteOrders.stream().mapToInt(RMEntity::getTB).summaryStatistics().getMin();
            int maxTE = substituteOrders.stream().mapToInt(RMEntity::getTE).summaryStatistics().getMax();
            int maxTD = maxTE - minTB;
            List<RMEquipment> substituteEquips = problem.getTypeMatchEquips().get(type);
            double minRentRate = substituteEquips.stream().mapToDouble(e -> e.getRentFunction().getRent(maxTD)).min().orElse(0) / maxTD;
            minSumRent += minRentRate * substituteOrders.stream().mapToDouble(RMCusOrder::getTD).sum();
            for (RMCusOrder order : substituteOrders) {
                double minDist = Integer.MAX_VALUE;
                int j = order.getIndex();
                for (RMEquipment equip : substituteEquips) {
                    int i = equip.getIndex();
                    if (d_ij[i][j] < minDist) {
                        minDist = d_ij[i][j];
                    }
                }
                for (RMCusOrder order0 : substituteOrders) {
                    int l = order0.getIndex();
                    if (l != j && d_jj[l][j] < minDist) {
                        minDist = d_jj[l][j];
                    }
                }
                minSumDist += minDist;
            }
        }
        double greedyObj = maxRevenue - minSumRent - minSumDist;
        if (greedyObj < node.bestUpperBound) {
            node.bestUpperBound = greedyObj;
            if (node.isRoot()) {
                UB = greedyObj;
            }
        }
    }

    private void dfs() throws IloException, IOException {
        // 遍历每个节点
        while (!(nodesFiltered.isEmpty() || stop())) {
            CGNode current = nodesFiltered.get(0);
            if (current.bestUpperBound <= LB) {
                // 剪枝
                current.setStop(true);
                current.setUnpromising(true);
                nodesFiltered.remove(current);
                continue;
            }
            // 移除不可行列
            removeInfeasibleColumns(current);
            solveNode(current);
            // 上界：搜索树所有点的主问题目标函数值取最大
            double oldUB = UB;
            UB = Collections.max(nodesFiltered.stream().map(o -> o.bestUpperBound).toList());
            updateGap();
            if (Tools.differ(oldUB, UB)) {
                reportUpperBound(current.getId());
            }
            nodesFiltered.remove(current);
            // 剪枝/分支
            if (!current.isStop()) { // noFeasibleSolution / unpromising / isIntegerSolution
                // 分数解，分支，寻找子项
                decideBranches(current, problem);
                nodesFiltered.add(0, current.getSuccessorR());
                nodesFiltered.add(0, current.getSuccessorL());
            }
        }
    }

    private void bfs() throws IloException, IOException {
        // 遍历每个节点
        while (!(nodesFiltered.isEmpty() || stop())) {
            CGNode current = nodesFiltered.remove(0);
            if (current.bestUpperBound <= LB) {
                // 剪枝
                current.setStop(true);
                current.setUnpromising(true);
                continue;
            }
            // 分支
            List<CGNode> successors = new ArrayList<>();
            successors.add(current.getSuccessorL());
            successors.add(current.getSuccessorR());
            while (!successors.isEmpty() && !stop()) {
                // 计算每个子项的结果
                CGNode successor = successors.remove(0);
                // 移除不可行列
                removeInfeasibleColumns(successor);
                solveNode(successor);
                // 上界：搜索树所有点的主问题目标函数值取最大
                double oldUB = UB;
                UB = Collections.max(nodesFiltered.stream().map(o -> o.bestUpperBound).toList());
                updateGap();
                if (Tools.differ(oldUB, UB)) {
                    reportUpperBound(successor.getId());
                }
                // 剪枝/分支
                if (successor.isStop()) { // noFeasibleSolution / unpromising / isIntegerSolution
                    nodesFiltered.remove(successor);
                } else { // 分数解，分支，寻找子项
                    decideBranches(successor, problem);
                    nodesFiltered.add(successor.getSuccessorL());
                    nodesFiltered.add(successor.getSuccessorR());
                }
            }
        }
    }

    private void ufs() throws IloException, IOException {
        while (!(nodesFiltered.isEmpty() || stop())) {
            CGNode current = nodesFiltered.remove(0);
            if (current.bestUpperBound <= LB) {
                // 剪枝
                current.setStop(true);
                current.setUnpromising(true);
                nodesFiltered.remove(current);
                continue;
            }
            // 移除不可行列
            removeInfeasibleColumns(current);
            solveNode(current);
            // 剪枝/分支
            if (!current.isStop()) { // noFeasibleSolution / unpromising / isIntegerSolution
                // 分数解，分支，寻找子项
                decideBranches(current, problem);
                reportBranchDecision(current);
                nodesFiltered.add(current.getSuccessorL());
                nodesFiltered.add(current.getSuccessorR());
            }
            nodesFiltered.sort(Comparator.comparingDouble(CGNode::getBestUpperBound).reversed().thenComparingInt(CGNode::getId));
            // 上界：搜索树所有点的主问题目标函数值取最大
            double oldUB = UB;
            UB = nodesFiltered.isEmpty() ? LB : nodesFiltered.get(0).bestUpperBound;
            updateGap();
            if (Tools.differ(oldUB, UB)) {
                reportUpperBound(current.getId());
            }
        }
    }

    /**
     * obtain tolerant gap or reach time limit
     */
    protected boolean stop() {
        stopByGap = GAP < para.getMIP_GAP();
        return stopByGap || reachTimeLimit();
    }

    private boolean reachTimeLimit() {
        int time_s = calTimeMillis() / 1000;
        stopByTime = time_s >= para.getTIME_LIMIT();
        return stopByTime;
    }

    private int calTimeMillis() {
        return (int) (System.currentTimeMillis() - algoStartTime);
    }

    /**
     * T(ms), GAP, LB, UB, nodeID, byRepair
     */
    private void reportLowerBound(int id, boolean repaired) throws IOException {
        if (feasibleSolutionPath != null) {
            String line = "%d\t%s\t%s\t%s\t%d\t%d".formatted(calTimeMillis(), Tools.df6.format(GAP), Tools.df1.format(LB), Tools.df1.format(UB), id, repaired ? 1 : 0);
            WriteText.createTxtFile(problem.getName(), feasibleSolutionPath);
            WriteText.appendTxtLine(line);
        }
    }

    /**
     * T(ms), GAP, LB, UB, nodeID
     */
    private void reportUpperBound(int id) throws IOException {
        if (feasibleSolutionPath != null) {
            String line = "%d\t%s\t%s\t%s\t%d".formatted(calTimeMillis(), Tools.df6.format(GAP), Tools.df1.format(LB), Tools.df1.format(UB), id);
            WriteText.createTxtFile(problem.getName(), feasibleSolutionPath);
            WriteText.appendTxtLine(line);
        }
    }

    public void writeFeasibleSolution(String path) {
        feasibleSolutionPath = path + getClass().getSimpleName().substring(6) + '/';
    }

    public void writeBranchDecisions(String path) {
        branchDecisionPath = path;
    }

    private void reportBranchDecision(CGNode curNode) throws IOException {
        if (branchDecisionPath != null) {
            WriteText.createTxtFile(problem.getName() + "_tree", branchDecisionPath);
            WriteText.appendTxtLine(curNode.id + " L " + curNode.getSuccessorL().toString());
            WriteText.appendTxtLine(curNode.id + " R " + curNode.getSuccessorR().toString());
        }
    }

    /**
     * @return "obj(df1), gap(df6), t(ms)"
     */
    public String getSimpleReport() {
        return String.format("%s\t%s\t%d", Tools.df1.format(LB), Tools.df6.format(GAP), CAL_TIME);
    }

    /**
     * @return nNode, nSolved, bestID, nDLoop
     */
    public String getExploreReport() {
        return String.format("%d\t%d\t%d\t%d", nNodes, nExplored, optimalNodeIdx, degenerationLoop);
    }

    public String getDetailReport() {
        return "%s\t%s\t%s\t%s".formatted(rootReport, getSimpleReport(), getExploreReport(), getDetailedTime());
    }
}
