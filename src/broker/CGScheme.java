package broker;

import lombok.Getter;
import lombok.Setter;
import problem.RMEquipment;
import problem.RMLeaseTerm;
import problem.RMWorkOrder;

import java.util.List;
import java.util.stream.IntStream;

@Getter
@Setter
public abstract class CGScheme {
    protected RMEquipment equipment;

    protected int[][] y_jt;
    protected int[][] x_jw;
    protected int[][] s_jj;
    protected int[] hB_w;
    protected int[] hE_w;
    protected int[][] tD_kn;
    protected int[][] gamma_kn;
    protected int[] tB_k;
    protected int[] tE_k;

    protected List<RMWorkOrder> workOrders;
    protected List<RMLeaseTerm> leaseTerms;

    public boolean serveCustomerOrNot(int pIndex) {
        return workOrders.stream().anyMatch(work -> work.getCusOrder().getAffiliation().getIndex() == pIndex);
    }

    public boolean serveCusOrderOrNot(int jIndex) {
        return workOrders.stream().anyMatch(work -> work.getCusOrder().getIndex() == jIndex);
    }

    public boolean serveCusOrderOrNot(int jIndex, int wIndex) {
        return IntStream.range(0, Math.min(wIndex + 1, workOrders.size())).anyMatch(w -> workOrders.get(w).getCusOrder().getIndex() == jIndex);
    }

    /**
     * 判断是否全新策略（与列表中所有策略都不同）
     */
    public <E> boolean isBrandNew(List<E> schemeList) {
        return schemeList.stream().noneMatch(this::identical);
    }

    protected abstract <E> boolean identical(E e);
}
