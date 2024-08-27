package broker.work;

import broker.CGConstraint;
import broker.CGNode;
import broker.CGScheme;
import broker.Tools;
import lombok.Setter;
import problem.*;

import java.util.*;

@Setter
public class SPSSolverEnumerate {

    private final int mMaxN;

    private final double mu;

    private final CGNode node;

    private final int equipmentIndex;

    private final RMEquipment equip;

    private final RMPara para;

    private final List<CGSchemeWorkOrders> candidateSchemes;

    private final SPSCoefficient coefficient;

    int nColumnOld = 0;
    int nColumnAdded = 0;
    RMSolutionS solution;

    SPSSolverEnumerate(RMProblem problem, RMSolution rmp, CGNode _node, int i) {
        node = _node;
        equip = problem.getEquipments().get(i);
        para = problem.getPara();
        mu = rmp.getDualMu()[i];
        equipmentIndex = i;

        coefficient = new SPSCoefficient(problem, rmp, i, _node);

        candidateSchemes = new ArrayList<>(node.getEquipTypeCandidateSchemes().get(equip.getSubType()));
        initBranchCandidates();

        // all candidate orders
        Set<Integer> timeBEs = new HashSet<>();
        coefficient.J_i.forEach(order -> {
            timeBEs.add(order.getTB());
            timeBEs.add(order.getTE());
        });
        List<Integer> timeBorders = timeBEs.stream().sorted().toList();
        int nWorkOrder = timeBorders.size() - 1; // <= T
        mMaxN = para.isAllowReVisit() ? nWorkOrder : Math.min(nWorkOrder, coefficient.J_i.size());
    }

    private void initBranchCandidates() {
        CGNode consNode = node;
        CGConstraint cons;
        while (consNode != null && !consNode.isRoot()) {
            cons = consNode.getConstraint();
            if (cons.branchVarZ) {
                modCandidateZ(cons);
            } else if (cons.equipment != null && cons.equipment.getIndex() == equipmentIndex) {
                if (cons.branchService) {
                    modCandidateService(cons);
                } else if (cons.branchVarX) {
                    modCandidateX(cons);
                } else if (cons.branchVarHB) {
                    modCandidateHB(cons);
                } else if (cons.branchVarHE) {
                    modCandidateHE(cons);
                } else if (cons.branchVarY) {
                    modCandidateY(cons);
                }
            }
            consNode = consNode.getPredecessor();
        }
    }

    private void modCandidateZ(CGConstraint cons) {
        if (cons.noMoreThan) {
            candidateSchemes.removeIf(scheme -> scheme.serveCustomerOrNot(cons.pIndex));
        }
    }

    private void modCandidateHE(CGConstraint cons) {
        if (cons.noMoreThan) {
            candidateSchemes.removeIf(scheme -> (
                    scheme.getWorkOrders().size() < (cons.wIndex + 1) ||
                            scheme.getWorkOrders().get(cons.wIndex).getTE() > cons.rightHandSide));
        } else {
            candidateSchemes.removeIf(scheme -> (
                    scheme.getWorkOrders().size() >= (cons.wIndex + 1) &&
                            scheme.getWorkOrders().get(cons.wIndex).getTE() < cons.rightHandSide));
        }
    }

    private void modCandidateHB(CGConstraint cons) {
        if (cons.noMoreThan) {
            candidateSchemes.removeIf(scheme -> (
                    scheme.getWorkOrders().size() < (cons.wIndex + 1) ||
                            scheme.getWorkOrders().get(cons.wIndex).getTB() > cons.rightHandSide));
        } else {
            candidateSchemes.removeIf(scheme ->
                    scheme.getWorkOrders().size() >= (cons.wIndex + 1) &&
                            scheme.getWorkOrders().get(cons.wIndex).getTB() < cons.rightHandSide);
        }
    }

    private void modCandidateService(CGConstraint cons) {
        candidateSchemes.removeIf(scheme -> scheme.serveCusOrderOrNot(cons.jIndex) == cons.noMoreThan);
    }

    private void modCandidateX(CGConstraint cons) {
        candidateSchemes.removeIf(scheme -> scheme.serveCusOrderOrNot(cons.jIndex, cons.wIndex) == cons.noMoreThan);
    }

    private void modCandidateY(CGConstraint cons) {
        if (cons.noMoreThan) {
            candidateSchemes.removeIf(scheme -> scheme.serveCusOrderAtT(cons.jIndex, cons.tIndex));
        } else {
            candidateSchemes.removeIf(scheme -> !scheme.serveCusOrderAtT(cons.jIndex, cons.tIndex));
        }
    }

    public RMSolutionS enumerate() {
        List<CGScheme> historyList = node.getEquipWorkLeaseSchemeMap().get(equip);
        List<CGSchemeWorkLeases> newSchemeList = new ArrayList<>();

        double bestObj = -Integer.MAX_VALUE;
        CGSchemeWorkLeases bestScheme = null;
        List<CGSchemeWorkOrders> deserted = new ArrayList<>();
        for (CGSchemeWorkOrders scheme : candidateSchemes) {
            if (scheme.getWorkOrders().get(0).getTB() < equip.getTB() || scheme.getWorkOrders().size() > mMaxN
                    || scheme.getWorkOrders().stream().allMatch(work -> work.getMaxNX() == 1)) {
                continue;
            }
            CGSchemeWorkLeases completeScheme = new CGSchemeWorkLeases(equip, para);
            completeScheme.completeSchemeWork(scheme.getWorkOrders());
            SPSCoefficient.FEASIBLE_TYPE feasible_type = coefficient.checkFeasibilityWork(completeScheme);
            if (feasible_type.equals(SPSCoefficient.FEASIBLE_TYPE.FEASIBLE)) {
                completeScheme.completeSchemeRent();
                coefficient.computeSchemeObj(completeScheme);
                if (Tools.nonNegative(completeScheme.spsObj - mu)) { // column priced out
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
            } else if (feasible_type.equals(SPSCoefficient.FEASIBLE_TYPE.INFEASIBLE_J)) {
                deserted.add(scheme);
            }
        }
        node.getEquipTypeCandidateSchemes().get(equip.getSubType()).removeAll(deserted);
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

    private void manageSize(List<CGSchemeWorkLeases> schemes) {
        if (schemes.size() > para.getNCopySafe()) {
            schemes.sort(Comparator.comparing(CGSchemeWorkLeases::getSpsObj));
            schemes.remove(0);
        }
    }
}
