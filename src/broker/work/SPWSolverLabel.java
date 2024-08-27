package broker.work;

import broker.*;
import lombok.Setter;
import problem.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Setter
public class SPWSolverLabel {

    private final double pi;

    private final CGNode node;

    private final RMEquipment equip;

    private final RMPara para;

    private final int T;

    private final List<RMCusOrder> J_i;

    private List<Integer> timeBorders;

    private final SPWCoefficient coefficient;

    int nColumnOld = 0;
    int nColumnAdded = 0;
    boolean bestSPLAdded = false;
    RMSolutionW solution;

    SPWSolverLabel(RMProblem problem, RMSolution rmp, CGNode _node, int i) {
        node = _node;
        equip = problem.getEquipments().get(i);
        para = problem.getPara();
        T = para.getNTimePeriod();
        pi = rmp.getDualPi()[i];

        coefficient = new SPWCoefficient(problem, rmp, i, _node);

        if (!para.isLabelExtendByOrder()) {
            timeBorders = new ArrayList<>(problem.getTypeTimeBorders().get(equip.getSubType()));
            timeBorders.removeIf(t -> t <= equip.getTB());
        }

        // all candidate orders
        J_i = new ArrayList<>(equip.getTypeMatchOrders());
        J_i.removeIf(order -> coefficient.branchUBSplit[order.getIndex()] == 0);
        J_i.removeIf(order -> coefficient.branchService0[order.getIndex()]);
    }

    public RMSolutionW extend() {
        List<CGScheme> historyList = node.getEquipWorkOrderSchemeMap().get(equip);
        List<CGSchemeWorkOrders> newSchemeList = new ArrayList<>();

        List<Label> labelList;
        if (para.isLabelExtendByOrder()) {
            labelList = extendByOrder();
        } else {
            labelList = extendByTime();
        }

        double bestObj = -Integer.MAX_VALUE;
        CGSchemeWorkOrders bestScheme = null;
        for (Label label : labelList) {
            double currentObj = computeLabelObj(label);
            if (Tools.nonNegative(currentObj - pi)) { // column priced out
                CGSchemeWorkOrders completeScheme = new CGSchemeWorkOrders(equip, para);
                completeScheme.completeScheme(label.plannedWorks);
                completeScheme.spwObj = currentObj;
                coefficient.checkFeasibility(completeScheme);
                if (completeScheme.penaltySum == 0) { // feasible
                    if (Tools.nonNegative(currentObj - bestObj)) { // better
                        bestObj = currentObj;
                        bestScheme = completeScheme;
                    }
                    if (completeScheme.isBrandNew(historyList)) {
                        newSchemeList.add(completeScheme.getCopy());
                        nColumnAdded++;
                        manageSize(newSchemeList);  // in case of out-of-memory error
                    } else {
                        nColumnOld++;
                    }
                }
            }
        }
        // truncate new columns
        if (para.getNColumnAddedSP() > 0 && newSchemeList.size() > para.getNColumnAddedSP()) {
            newSchemeList.sort(Comparator.comparing(CGSchemeWorkOrders::getSpwObj).reversed());
            newSchemeList = newSchemeList.subList(0, para.getNColumnAddedSP()); // keep most promising columns
        }
        historyList.addAll(newSchemeList);
        // update node columns
        node.getEquipWorkOrderSchemeMap().put(equip, historyList);
        List<CGScheme> leaseTerms = node.getEquipLeaseTermSchemeMap().get(equip);
        for (CGSchemeWorkOrders scheme : newSchemeList) {
            LeaseTermDP dp = new LeaseTermDP(equip, scheme.getWorkOrders().size(), scheme.getHB_w(), scheme.getHE_w());
            List<RMLeaseTerm> terms = dp.enumerate();
            CGSchemeLeaseTerms schemeL = new CGSchemeLeaseTerms(equip, para, terms);
            CGSchemeLeaseTerms existedLeaseTerm = schemeL.existed(leaseTerms);
            if (existedLeaseTerm != null) {
                schemeL = existedLeaseTerm;
            } else if (node.columnFeasible(schemeL)) {
                leaseTerms.add(schemeL);
                bestSPLAdded = true;
            }
            scheme.setBestCoverScheme(schemeL);
            schemeL.getBestCoveredSchemes().add(scheme);
        }
        node.getEquipLeaseTermSchemeMap().put(equip, leaseTerms);
        // get solution from best scheme
        if (bestScheme != null) {
            solution = bestScheme.getSolutionW();
        }
        return solution;
    }

    private void manageSize(List<CGSchemeWorkOrders> schemes) {
        if (schemes.size() > para.getNCopySafe()) {
            schemes.sort(Comparator.comparing(CGSchemeWorkOrders::getSpwObj));
            schemes.remove(0);
        }
    }

    private List<Label> extendByOrder() {
        List<Label> extendable = new ArrayList<>();
        extendable.add(new Label(equip.getTB(), J_i));
        List<Label> fathomed = new ArrayList<>();
        while (!extendable.isEmpty()) {
            Label current = extendable.remove(0);
            fathomed.add(current);
            List<Label> samePreviousLabels = new ArrayList<>();
            for (RMCusOrder order : current.candidateOrders) {
                List<Label> sameOrderLabels = current.extendByOrder(order, coefficient);
                samePreviousLabels.addAll(sameOrderLabels);
                // dominance by order
                sameOrderLabels.addAll(extendable.stream().filter(label ->
                        label.plannedWorks.size() == (current.nW + 1) && // same w
                                label.lastServedOrder == order).toList()); // same l
                sameOrderLabels.addAll(fathomed.stream().filter(label ->
                        label.plannedWorks.size() == (current.nW + 1) && // same w
                                label.lastServedOrder == order).toList()); // same l
                applyDominanceByOrder(sameOrderLabels);
            }
            // dominance by time
            List<Label> labelToApplyDominanceTime = new ArrayList<>(samePreviousLabels);
            labelToApplyDominanceTime.addAll(extendable);
            applyDominanceByTime(labelToApplyDominanceTime);
            // extendable or fathomed
            for (Label label : samePreviousLabels) {
                if (!label.dominated) {
                    if (label.candidateOrders.isEmpty()) {
                        fathomed.add(label);
                    } else {
                        extendable.add(label);
                    }
                }
            }
        }
        return fathomed;
    }

    private List<Label> extendByTime() {
        List<Label> labelList = new ArrayList<>();
        labelList.add(new Label(equip.getTB(), J_i));
        for (Integer timeStamp : timeBorders) {
            List<Label> sameTimeLabels = new ArrayList<>();
            for (Label current : labelList) {
                List<Label> currentExtendedLabels = current.extendByTime(timeStamp, coefficient);
                sameTimeLabels.addAll(currentExtendedLabels);
            }
            applyDominanceByTime(sameTimeLabels);
            sameTimeLabels.removeIf(label -> label.dominated);
            labelList.addAll(sameTimeLabels);
            applyDominanceByOrder(labelList);
            labelList.removeIf(label -> label.dominated);
        }
        return labelList;
    }

    private double computeLabelObj(Label label) {
        double obj = label.partialObj;
        int nW = label.plannedWorks.size();
        obj += Tools.getArraySumRange(coefficient.lambdaB_w, nW, coefficient.lambdaB_w.length) * T;
        obj += Tools.getArraySumRange(coefficient.lambdaE_w, nW, coefficient.lambdaE_w.length) * T;
        return obj;
    }

    /**
     * Rule 1: w1=w2, j1=j2, candidateJ1=candidateJ2, compare exploredT and partialObj
     */
    private void applyDominanceByOrder(List<Label> labels) {
        Map<Label, List<Label>> labelMapWLJ = labels.stream().collect(Collectors.groupingBy(Label::getWLJClassifier));
        labelMapWLJ.forEach((key, sameOrderLabels) -> {
            for (int l1 = 0; l1 < sameOrderLabels.size(); l1++) {
                Label label1 = sameOrderLabels.get(l1);
                for (int l2 = l1 + 1; l2 < sameOrderLabels.size(); l2++) {
                    Label label2 = sameOrderLabels.get(l2);
                    if (label1.partialObj > label2.partialObj && label1.exploredT <= label2.exploredT) {
                        label2.dominated = true;
                    } else if (label1.partialObj < label2.partialObj && label1.exploredT >= label2.exploredT) {
                        label1.dominated = true;
                    }
                }
            }
        });
    }

    /**
     * Rule 2: w1=w2, hE1=hE2, candidateJ1=candidateJ2, compare partialObj - max(d_ilj)
     */
    private void applyDominanceByTime(List<Label> labels) {
        Map<Label, List<Label>> labelMapWTJ = labels.stream().collect(Collectors.groupingBy(Label::getWTJClassifier));
        for (var entry : labelMapWTJ.entrySet()) {
            List<Label> sameCandidateLabels = entry.getValue();
            List<RMCusOrder> candidates = entry.getKey().candidateOrders;
            for (int l1 = 0; l1 < sameCandidateLabels.size(); l1++) {
                Label label1 = sameCandidateLabels.get(l1);
                RMCusOrder order1 = (RMCusOrder) label1.lastServedOrder;
                double maxPossibleDist1 = order1.getMaxDistTo(candidates);
                double obj1 = label1.partialObj - maxPossibleDist1;
                for (int l2 = l1 + 1; l2 < sameCandidateLabels.size(); l2++) {
                    Label label2 = sameCandidateLabels.get(l2);
                    RMCusOrder order2 = (RMCusOrder) label2.lastServedOrder;
                    if (order2.getIndex() == order1.getIndex()) {
                        if (label2.partialObj > label1.partialObj) {
                            label1.dominated = true;
                        } else if (label2.partialObj < label1.partialObj) {
                            label2.dominated = true;
                        }
                    } else {
                        double maxPossibleDist2 = order2.getMaxDistTo(candidates);
                        double obj2 = label2.partialObj - maxPossibleDist2;
                        if (obj2 > label1.partialObj) {
                            label1.dominated = true;
                        } else if (obj1 > label2.partialObj) {
                            label2.dominated = true;
                        }
                    }
                }
            }
        }
    }

    private static class Label {
        int nW;
        RMEntity lastServedOrder;

        int exploredT;
        List<RMCusOrder> candidateOrders;
        List<RMWorkOrder> plannedWorks;
        double partialObj;
        boolean dominated;

        public Label(int _endT, List<RMCusOrder> candidates) {
            exploredT = _endT;
            candidateOrders = new ArrayList<>(candidates);
            plannedWorks = new ArrayList<>();
            partialObj = 0;
        }

        public Label() {
        }

        public List<Label> extendByTime(int _endT, SPWCoefficient coefficient) {
            List<Label> extendedLabels = new ArrayList<>();
            for (RMCusOrder order : candidateOrders) {
                for (RMWorkOrder work : order.getCandidateWorkOrders()) {
                    if (coefficient.allowRevisit && lastServedOrder != null && lastServedOrder.equals(order) && exploredT == work.getTB()) {
                        continue;
                    }
                    if (work.getTB() >= exploredT && work.getTE() == _endT && coefficient.branchFeasible(nW, work)) {
                        Label copy = extend(work);
                        if (!coefficient.allowRevisit) {
                            copy.candidateOrders.remove(order);
                        }
                        double deltaObj = coefficient.computePartialObj(nW, work, lastServedOrder);
                        copy.partialObj = partialObj + deltaObj;
                        extendedLabels.add(copy);
                    }
                }
            }
            return extendedLabels;
        }

        public List<Label> extendByOrder(RMCusOrder order, SPWCoefficient coefficient) {
            List<Label> extendedLabels = new ArrayList<>();
            for (RMWorkOrder work : order.getCandidateWorkOrders()) {
                if (coefficient.allowRevisit && lastServedOrder != null && lastServedOrder.equals(order) && exploredT == work.getTB()) {
                    continue;
                }
                if (work.getTB() >= exploredT && coefficient.branchFeasible(nW, work)) {
                    Label copy = extend(work);
                    if (!coefficient.allowRevisit) {
                        copy.candidateOrders.remove(order);
                    }
                    double deltaObj = coefficient.computePartialObj(nW, work, lastServedOrder);
                    copy.partialObj = partialObj + deltaObj;
                    extendedLabels.add(copy);
                }
            }
            return extendedLabels;
        }

        public Label extend(RMWorkOrder work) {
            Label copy = new Label();
            copy.nW = nW + 1;
            copy.lastServedOrder = work.getCusOrder();
            copy.exploredT = work.getTE();
            copy.candidateOrders = new ArrayList<>(candidateOrders);
            copy.candidateOrders.removeIf(order -> order.getTE() <= copy.exploredT);
            copy.plannedWorks = new ArrayList<>(plannedWorks);
            copy.plannedWorks.add(work);
            return copy;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (RMWorkOrder work : plannedWorks) {
                sb.append(" J ").append(work.getCusOrder().getId());
                sb.append(" ").append(work.timeWindowToString());
            }
            return sb.toString();
        }

        public Label getWLJClassifier() {
            Label partialCopy = new Label();
            partialCopy.nW = plannedWorks.size();
            partialCopy.lastServedOrder = lastServedOrder;
            partialCopy.candidateOrders = new ArrayList<>(candidateOrders);
            return partialCopy;
        }

        public Label getWTJClassifier() {
            Label partialCopy = new Label();
            partialCopy.nW = plannedWorks.size();
            partialCopy.exploredT = exploredT;
            partialCopy.candidateOrders = new ArrayList<>(candidateOrders);
            return partialCopy;
        }
    }
}
