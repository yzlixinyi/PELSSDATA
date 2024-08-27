package broker.work;

import broker.*;
import lombok.Setter;
import problem.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Setter
public class SPWSolverEnumerate {

    private final int mMaxN;

    private final double pi;

    private final CGNode node;

    private final int equipmentIndex;

    private final RMEquipment equip;

    private final RMPara para;

    private final int T;

    private final List<CGSchemeWorkOrders> candidateSchemes;

    private final SPWCoefficient coefficient;

    int nColumnOld = 0;
    int nColumnAdded = 0;
    boolean bestSPLAdded = false;
    RMSolutionW solution;

    SPWSolverEnumerate(RMProblem problem, RMSolution rmp, CGNode _node, int i) {
        node = _node;
        equip = problem.getEquipments().get(i);
        para = problem.getPara();
        T = problem.getPara().getNTimePeriod();
        pi = rmp.getDualPi()[i];
        equipmentIndex = i;

        coefficient = new SPWCoefficient(problem, rmp, i, _node);

        candidateSchemes = new ArrayList<>(node.getEquipTypeCandidateSchemes().get(equip.getSubType()));
        initBranchCandidates();

        // all candidate orders
        List<RMCusOrder> J_i = new ArrayList<>(equip.getTypeMatchOrders());
        J_i.removeIf(order -> coefficient.branchUBSplit[order.getIndex()] == 0);
        J_i.removeIf(order -> coefficient.branchService0[order.getIndex()]);
        mMaxN = Math.min(J_i.size(), equip.getMaxNWorkOrder());
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

    public RMSolutionW enumerate() {
        List<CGScheme> historyList = node.getEquipWorkOrderSchemeMap().get(equip);
        List<CGSchemeWorkOrders> newSchemeList = new ArrayList<>();

        double bestObj = -Integer.MAX_VALUE;
        CGSchemeWorkOrders bestScheme = null;
        for (CGSchemeWorkOrders scheme : candidateSchemes) {
            if (scheme.getWorkOrders().get(0).getTB() < equip.getTB() || scheme.getWorkOrders().size() > mMaxN) {
                continue;
            }
            double currentObj = computeSchemeObj(scheme);
            if (Tools.nonNegative(currentObj - pi)) { // column priced out
                CGSchemeWorkOrders completeScheme = new CGSchemeWorkOrders(equip, para);
                completeScheme.completeScheme(scheme.getWorkOrders());
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
        matchBestSPL(newSchemeList);
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

    private void matchBestSPL(List<CGSchemeWorkOrders> newSchemeList) {
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
    }

    private double computeSchemeObj(CGSchemeWorkOrders scheme) {
        double obj = 0;
        int w = 0;
        RMEntity previousEntity = equip;
        for (RMWorkOrder work : scheme.getWorkOrders()) {
            obj += coefficient.computePartialObj(w, work, previousEntity);
            previousEntity = work.getCusOrder();
            w++;
        }
        obj += Tools.getArraySumRange(coefficient.lambdaB_w, w, coefficient.lambdaB_w.length) * T;
        obj += Tools.getArraySumRange(coefficient.lambdaE_w, w, coefficient.lambdaE_w.length) * T;
        return obj;
    }
}
