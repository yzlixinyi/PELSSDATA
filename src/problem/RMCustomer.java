package problem;

import lombok.Getter;

import java.util.*;

@Getter
public class RMCustomer extends RMEntity {

    double serviceRevenue = 0;

    double averageRevenue = 0;

    public RMCustomer(int _id) {
        super(_id, RMType.CUSTOMER);
    }

    public void setRevenue(double revenue) {
        this.serviceRevenue = revenue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RMCustomer customer = (RMCustomer) o;
        return Double.compare(customer.serviceRevenue, serviceRevenue) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), serviceRevenue);
    }

    public boolean assureFullServiceExclusively(int[][][] intY, RMProblem problem) {
        for (RMEntity entity : components) {
            RMCusOrder cusOrder = (RMCusOrder) entity;
            if (!cusOrder.orderServedExclusively(intY, problem)) {
                return false;
            }
        }
        return true;
    }

    public void clearYt(int[][][] intY) {
        for (RMEntity cusOrder : components) {
            int j = cusOrder.index;
            for (int[][] intY_i : intY) {
                Arrays.fill(intY_i[j], 0);
            }
        }
    }

    public void calAvgRevenue() {
        int sumT = components.stream().mapToInt(RMEntity::getTD).sum();
        averageRevenue = serviceRevenue / sumT;
    }

    public void calAvgRevenueFlex() {
        int sumT = components.stream().mapToInt(c -> ((RMFlexible) c).mD).sum();
        averageRevenue = serviceRevenue / sumT;
    }

    public String toString() {
        return "CUSTOMER %d R %d".formatted(id, (int) serviceRevenue);
    }
}
