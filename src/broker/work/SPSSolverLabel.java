package broker.work;

import broker.CGNode;
import broker.CGScheme;
import broker.Tools;
import lombok.Getter;
import lombok.Setter;
import problem.*;

import java.util.*;
import java.util.stream.Collectors;

@Setter
public class SPSSolverLabel {

    private final double mu;

    private final CGNode node;

    private final RMEquipment equip;

    private final RMPara para;

    private List<Integer> timeBorders;

    private final SPSCoefficient coefficient;

    int nColumnOld = 0;
    int nColumnAdded = 0;
    RMSolutionS solution;

    SPSSolverLabel(RMProblem problem, RMSolution rmp, CGNode _node, int i) {
        node = _node;
        equip = problem.getEquipments().get(i);
        para = problem.getPara();
        mu = rmp.getDualMu()[i];

        coefficient = new SPSCoefficient(problem, rmp, i, _node);

        if (!para.isLabelExtendByOrder()) {
            timeBorders = new ArrayList<>(problem.getTypeTimeBorders().get(equip.getSubType()));
            timeBorders.removeIf(t -> t <= equip.getTB());
        }
    }

    public RMSolutionS extend() {
        List<CGScheme> historyList = node.getEquipWorkLeaseSchemeMap().get(equip);
        List<CGSchemeWorkLeases> newSchemeList = new ArrayList<>();

        List<CGSchemeWorkLeases> pricedOutSchemes = priceOutSchemes();

        double bestObj = -Integer.MAX_VALUE;
        CGSchemeWorkLeases bestScheme = null;
        for (CGSchemeWorkLeases completeScheme : pricedOutSchemes) {
            if (Tools.nonNegative(completeScheme.spsObj - bestObj)) { // better
                bestObj = completeScheme.spsObj;
                bestScheme = completeScheme;
            }
            if (completeScheme.isBrandNew(historyList)) {
                newSchemeList.add(completeScheme);
                nColumnAdded++;
                manageSize(newSchemeList);  // in case of out-of-memory error
            } else {
                nColumnOld++;
            }
        }
        // truncate new columns
        if (para.getNColumnAddedSP() > 0 && newSchemeList.size() > para.getNColumnAddedSP()) {
            newSchemeList.sort(Comparator.comparing(CGSchemeWorkLeases::getSpsObj).reversed());
            newSchemeList = newSchemeList.subList(0, para.getNColumnAddedSP()); // keep most promising columns
        }
        historyList.addAll(newSchemeList);
        // update node columns
        node.getEquipWorkLeaseSchemeMap().put(equip, historyList);
        // get solution from best scheme
        if (bestScheme != null) {
            solution = bestScheme.getSolutionS();
        }
        return solution;
    }

    private List<CGSchemeWorkLeases> priceOutSchemes() {
        List<CGSchemeWorkLeases> labelSchemes = new ArrayList<>();
        if (node.getEquipTypeSPSRawLabelMap() != null && node.getEquipTypeSPSRawLabelMap().containsKey(equip.getSubType())) {
            List<CGSchemeWorkLeases> typeLabels = node.getEquipTypeSPSRawLabelMap().get(equip.getSubType());
            List<CGSchemeWorkLeases> deserted = new ArrayList<>();
            for (CGSchemeWorkLeases label : typeLabels) {
                CGSchemeWorkLeases completeScheme = new CGSchemeWorkLeases(equip, para);
                completeScheme.completeSchemeWork(label.getWorkOrders());
                SPSCoefficient.FEASIBLE_TYPE feasible_type = coefficient.checkFeasibilityWork(completeScheme);
                if (feasible_type.equals(SPSCoefficient.FEASIBLE_TYPE.FEASIBLE)) { // feasible
                    completeScheme.completeSchemeRent(label.getLeaseTerms());
                    coefficient.computeSchemeObj(completeScheme);
                    if (Tools.nonNegative(completeScheme.spsObj - mu)) {
                        labelSchemes.add(completeScheme);
                    }
                } else if (feasible_type.equals(SPSCoefficient.FEASIBLE_TYPE.INFEASIBLE_J)) {
                    deserted.add(label);
                }
            }
            if (!deserted.isEmpty()) {
                typeLabels.removeAll(deserted);
            }
            node.getEquipTypeSPSRawLabelMap().put(equip.getSubType(), typeLabels);
            return labelSchemes;
        }

        List<Label> labelList = para.isLabelExtendByOrder() ? extendByOrder() : extendByTime();

        if (para.isStoreExtendLabels()) {
            if (node.getEquipTypeSPSRawLabelMap() == null) {
                node.setEquipTypeSPSRawLabelMap(new HashMap<>());
            }
            node.getEquipTypeSPSRawLabelMap().put(equip.getSubType(), labelList.stream().map(this::copySimpleScheme).toList());
        }

        while (!labelList.isEmpty()) {
            Label label = labelList.remove(0);
            CGSchemeWorkLeases completeScheme = new CGSchemeWorkLeases(equip, para);
            completeScheme.completeSchemeWork(label.plannedWorks);
            if (coefficient.checkFeasibilityWork(completeScheme).equals(SPSCoefficient.FEASIBLE_TYPE.FEASIBLE)) { // feasible
                completeScheme.completeSchemeRent(label.currentRents);
                coefficient.computeSchemeObj(completeScheme);
                if (Tools.nonNegative(completeScheme.spsObj - mu)) {
                    labelSchemes.add(completeScheme);
                }
            }
        }

        return labelSchemes;
    }

    private CGSchemeWorkLeases copySimpleScheme(Label label) {
        CGSchemeWorkLeases copy = new CGSchemeWorkLeases();
        copy.setWorkOrders(label.plannedWorks.stream().map(RMWorkOrder::getCopySimple).toList());
        copy.setLeaseTerms(label.currentRents.stream().map(RMLeaseTerm::getCopy).toList());
        return copy;
    }

    private void manageSize(List<CGSchemeWorkLeases> schemes) {
        if (schemes.size() > para.getNCopySafe()) {
            schemes.sort(Comparator.comparing(CGSchemeWorkLeases::getSpsObj));
            schemes.remove(0);
        }
    }

    private List<Label> extendByTime() {
        List<Label> labelList = new ArrayList<>();
        labelList.add(new Label(equip.getTB(), coefficient.J_i, equip));
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

    /**
     * Dominance 1. l1 = l2, J1 = J2, G1 dominates G2 if t1 <= t2 and v1 > v2
     */
    private void applyDominanceByOrder(List<Label> labels) {
        Map<Label, List<Label>> labelMapJL = labels.stream().collect(Collectors.groupingBy(Label::getLJClassifier));
        labelMapJL.forEach((key, sameOrderLabels) -> {
            for (int l1 = 0; l1 < sameOrderLabels.size(); l1++) {
                Label label1 = sameOrderLabels.get(l1);
                for (int l2 = l1 + 1; l2 < sameOrderLabels.size(); l2++) {
                    Label label2 = sameOrderLabels.get(l2);
                    if (Tools.nonNegative(label1.partialObj - label2.partialObj) && label1.exploredT <= label2.exploredT) {
                        label2.dominated = true;
                    } else if (Tools.nonNegative(label2.partialObj - label1.partialObj) && label1.exploredT >= label2.exploredT) {
                        label1.dominated = true;
                    }
                }
            }
        });
    }

    /**
     * Dominance 2. t1 = t2, J1 = J2, G1 dominates G2 if v1 - max{d_lj} > v2
     */
    private void applyDominanceByTime(List<Label> labels) {
        Map<Label, List<Label>> labelMapTJ = labels.stream().collect(Collectors.groupingBy(Label::getTJClassifier));
        for (var entry : labelMapTJ.entrySet()) {
            List<RMCusOrder> candidates = entry.getKey().candidateOrders;
            List<Label> sameCandidateLabels = entry.getValue();
            Map<RMCusOrder, List<Label>> labelMapLastJ = sameCandidateLabels.stream().collect(Collectors.groupingBy(label -> label.lastServedOrder));
            // same t, same candidates, same lastServedOrder
            for (List<Label> sameLastJLabels : labelMapLastJ.values()) {
                sameLastJLabels.sort(Comparator.comparingDouble(Label::getPartialObj).reversed());
                double bestPartialObj = sameLastJLabels.get(0).partialObj;
                sameLastJLabels.stream().filter(label -> Tools.nonNegative(bestPartialObj - label.partialObj)).forEach(label -> label.dominated = true);
            }
            sameCandidateLabels.removeIf(label -> label.dominated);
            labelMapLastJ = sameCandidateLabels.stream().collect(Collectors.groupingBy(label -> label.lastServedOrder));
            // same t, same candidates, different lastServedOrder
            List<RMCusOrder> lastServedList = new ArrayList<>(labelMapLastJ.keySet());
            for (int j1 = 0; j1 < lastServedList.size(); j1++) {
                RMCusOrder order1 = lastServedList.get(j1);
                double maxPossibleDist1 = order1.getMaxDistTo(candidates);
                double maxPartialObj1 = labelMapLastJ.get(order1).get(0).partialObj;
                double obj1 = maxPartialObj1 - maxPossibleDist1;
                for (int j2 = j1 + 1; j2 < lastServedList.size(); j2++) {
                    RMCusOrder order2 = lastServedList.get(j2);
                    double maxPossibleDist2 = order2.getMaxDistTo(candidates);
                    double maxPartialObj2 = labelMapLastJ.get(order2).get(0).partialObj;
                    double obj2 = maxPartialObj2 - maxPossibleDist2;
                    if (Tools.nonNegative(obj2 - maxPartialObj1)) {
                        labelMapLastJ.get(order1).forEach(label -> label.dominated = true);
                    } else if (Tools.nonNegative(obj1 - maxPartialObj2)) {
                        labelMapLastJ.get(order2).forEach(label -> label.dominated = true);
                    }
                }
            }
        }
    }

    private List<Label> extendByOrder() {
        List<Label> extendable = new ArrayList<>();
        extendable.add(new Label(equip.getTB(), coefficient.J_i, equip));
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
                        label.lastServedOrder == order).toList());
                sameOrderLabels.addAll(fathomed.stream().filter(label ->
                        label.lastServedOrder == order).toList());
                applyDominanceByOrder(sameOrderLabels);
                fathomed.removeIf(label -> label.dominated);
                extendable.removeIf(label -> label.dominated);
            }
            // dominance by time
//            List<Label> labelToApplyDominanceTime = new ArrayList<>(samePreviousLabels);
//            labelToApplyDominanceTime.addAll(extendable);
//            applyDominanceByTime(labelToApplyDominanceTime);
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

    @Getter
    private static class Label {
        int nW;
        RMCusOrder lastServedOrder;

        int exploredT;
        RMEquipment equipment;
        List<RMCusOrder> candidateOrders;
        List<RMWorkOrder> plannedWorks;
        List<RMLeaseTerm> currentRents;
        double workRelatedObj;
        double rentalCosts;
        double partialObj;
        boolean dominated;

        public Label() {
        }

        public Label(int _endT, List<RMCusOrder> candidates, RMEquipment equip) {
            exploredT = _endT;
            equipment = equip;
            candidateOrders = new ArrayList<>(candidates);
            plannedWorks = new ArrayList<>();
            currentRents = new ArrayList<>();
            workRelatedObj = 0;
            rentalCosts = 0;
            partialObj = 0;
        }

        public List<Label> extendByTime(Integer _endT, SPSCoefficient coefficient) {
            List<Label> extendedLabels = new ArrayList<>();
            for (RMCusOrder order : candidateOrders) {
                for (RMWorkOrder work : order.getCandidateWorkOrders()) {
                    if (coefficient.allowRevisit && lastServedOrder != null && lastServedOrder.equals(order) && exploredT == work.getTB()) {
                        continue;
                    }
                    if (work.getTB() >= exploredT && work.getTE() == _endT && coefficient.branchFeasible(nW, work)) {
                        Label copy = extend(work, coefficient);
                        extendedLabels.add(copy);
                    }
                }
            }
            return extendedLabels;
        }

        public List<Label> extendByOrder(RMCusOrder order, SPSCoefficient coefficient) {
            List<Label> extendedLabels = new ArrayList<>();
            for (RMWorkOrder work : order.getCandidateWorkOrders()) {
                if (coefficient.allowRevisit && lastServedOrder != null && lastServedOrder.equals(order) && exploredT == work.getTB()) {
                    continue;
                }
                if (work.getTB() >= exploredT && coefficient.branchFeasible(nW, work)) {
                    Label copy = extend(work, coefficient);
                    extendedLabels.add(copy);
                }
            }
            return extendedLabels;
        }

        private Label extend(RMWorkOrder work, SPSCoefficient coefficient) {
            Label copy = new Label();
            copy.nW = nW + 1;
            copy.lastServedOrder = work.getCusOrder();
            copy.exploredT = work.getTE();
            copy.equipment = equipment;
            copy.candidateOrders = new ArrayList<>(candidateOrders);
            copy.candidateOrders.removeIf(order -> order.getTE() <= copy.exploredT);
            copy.plannedWorks = new ArrayList<>(plannedWorks);
            copy.plannedWorks.add(work);
            copy.currentRents = new ArrayList<>();
            if (currentRents.isEmpty()) {
                copy.currentRents.add(new RMLeaseTerm(equipment, work.getTB(), work.getTE()));
            } else {
                currentRents.forEach(term -> copy.currentRents.add(term.getCopy()));
                RMLeaseTerm lastLease = copy.currentRents.remove(currentRents.size() - 1);
                List<RMLeaseTerm> termsCoverWork = lastLease.coverWork(work);
                copy.currentRents.addAll(termsCoverWork);
            }
            copy.rentalCosts = copy.currentRents.stream().mapToDouble(RMLeaseTerm::getRentCost).sum();
            copy.workRelatedObj = workRelatedObj + coefficient.newWorkObj(work, lastServedOrder);
            copy.partialObj = copy.workRelatedObj - copy.rentalCosts;
            if (!coefficient.allowRevisit) {
                copy.candidateOrders.remove(work.getCusOrder());
            }
            if (coefficient.flexibleTime) {
                copy.candidateOrders.removeIf(o -> work.getCusOrder().getFlexible().equals(o.getFlexible()));
            }
            return copy;
        }

        public Label getTJClassifier() {
            Label partialCopy = new Label();
            partialCopy.exploredT = exploredT;
            partialCopy.candidateOrders = new ArrayList<>(candidateOrders);
            return partialCopy;
        }

        public Label getLJClassifier() {
            Label partialCopy = new Label();
            partialCopy.lastServedOrder = (lastServedOrder != null && lastServedOrder.getFlexible() != null) ? lastServedOrder.getFlexible() : lastServedOrder;
            partialCopy.candidateOrders = new ArrayList<>(candidateOrders);
            return partialCopy;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("t ").append(exploredT);
            for (RMLeaseTerm term : currentRents) {
                sb.append(" ").append(term.timeWindowToString());
            }
            for (RMWorkOrder work : plannedWorks) {
                sb.append(" J").append(work.getCusOrder().getId()).append(" ").append(work.timeWindowToString());
            }
            return sb.toString();
        }
    }
}
