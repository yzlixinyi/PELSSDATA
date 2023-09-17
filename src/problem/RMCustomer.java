package problem;

import java.util.Objects;

public class RMCustomer extends RMEntity {

    /**
     * (输出)是否服务该客户
     */
    boolean served = false;

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
        return served == customer.served && Double.compare(customer.serviceRevenue, serviceRevenue) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), served, serviceRevenue);
    }

    public void calAvgRevenue() {
        int sumT = components.stream().mapToInt(RMEntity::getTD).sum();
        averageRevenue = serviceRevenue / sumT;
    }
}
