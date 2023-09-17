package problem;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
public class RMProblem {

    String name;

    RMPara para;
    /**
     * 客户集合
     */
    List<RMCustomer> customers = new ArrayList<>();
    /**
     * 所有客户订单集合
     */
    List<RMCusOrder> cusOrders = new ArrayList<>();
    /**
     * 供应商集合
     */
    List<RMSupplier> suppliers = new ArrayList<>();
    /**
     * 所有供应商设备集合
     */
    List<RMEquipment> equipments = new ArrayList<>();
    /**
     * 从设备初始位置到订单的距离
     */
    double[][] distIJ;
    /**
     * 从订单到订单的距离
     */
    double[][] distJJ;
    /**
     * 每个设备最大租约数 q_i
     */
    int[] qIs;
    /**
     * 每个设备最大工单数 m_i
     */
    int[] mIs;
    /**
     * 每个设备租赁成本函数段数
     */
    int[] cIs;
    /**
     * 设备类型-设备集合
     */
    Map<Integer, List<RMEquipment>> typeMatchEquips;

    static int BASE_SCHEDULE_COST = 2;

    public void addCustomer(RMCustomer customer) {
        customers.add(customer);
    }

    public void addSupplier(RMSupplier supplier) {
        suppliers.add(supplier);
    }

    public void setParameter(RMPara parameter) {
        this.para = parameter;
    }

    /**
     * 根据输入补全信息，包括距离矩阵、最大工单数等
     */
    public void completeInputInfo() {
        for (int p = 0; p < customers.size(); p++) {
            RMCustomer customer = customers.get(p);
            customer.setIndex(p);
            customer.calAvgRevenue();
        }
        // 全部订单集合
        for (RMEntity customer : customers) {
            for (RMEntity entity : customer.components) {
                cusOrders.add((RMCusOrder) entity);
            }
        }
        para.nCusOrder = cusOrders.size();
        for (int j = 0; j < para.nCusOrder; j++) {
            RMEntity order = cusOrders.get(j);
            order.setIndex(j);
        }
        // 全部设备集合
        suppliers.stream().flatMap(supplier -> supplier.components.stream()).forEach(entity -> equipments.add((RMEquipment) entity));
        typeMatchEquips = equipments.stream().collect(Collectors.groupingBy(RMEquipment::getSubType));
        para.nEquipment = equipments.size();
        mIs = new int[para.nEquipment];
        qIs = new int[para.nEquipment];
        cIs = new int[para.nEquipment];
        for (int i = 0; i < para.nEquipment; i++) {
            RMEquipment e = equipments.get(i);
            e.index = i;
            // 设备i类型匹配的订单集合
            e.typeMatchOrders.addAll(cusOrders.stream().filter(order
                    -> (order.getSubType() == e.getSubType() && order.getTE() > e.getTB())).map(RMCusOrder.class::cast).collect(Collectors.toList()));
            // 最大工单数
            e.maxNWorkOrder = Math.min(para.nTimePeriod, e.typeMatchOrders.size());
            mIs[i] = e.maxNWorkOrder;
            // 最大租约数
            if (e.maxNLeaseTerm == 0) {
                e.maxNLeaseTerm = para.maxNumLeaseTerm > 0 ? Math.min(para.maxNumLeaseTerm, e.maxNWorkOrder) : e.maxNWorkOrder;
            }
            qIs[i] = e.maxNLeaseTerm;
            // 租赁成本函数段数
            cIs[i] = e.rentFunction.NSegment;
        }
        calDistMatrix();
    }

    /**
     * 计算距离矩阵和时间矩阵
     */
    private void calDistMatrix() {
        distIJ = new double[para.nEquipment][para.nCusOrder];
        distJJ = new double[para.nCusOrder][para.nCusOrder];
        for (int j = 0; j < para.nCusOrder; j++) {
            RMEntity order = cusOrders.get(j);
            // d_IJ
            for (int i = 0; i < para.nEquipment; i++) {
                distIJ[i][j] = equipments.get(i).location.calDist(order.location) + BASE_SCHEDULE_COST;
            }
            // d_JJ
            for (int k = j + 1; k < para.nCusOrder; k++) {
                distJJ[j][k] = order.location.calDist(cusOrders.get(k).location) + BASE_SCHEDULE_COST;
                distJJ[k][j] = distJJ[j][k];
            }
        }
    }

    public List<String> collectCaseInfo() {
        List<String> caseInfo = new ArrayList<>();
        // 规模概况
        caseInfo.add(String.format("CASE %s T %d K %d N %d", name,
                para.nTimePeriod, para.maxNumLeaseTerm, para.maxNumCostSegment));
        // 客户和订单
        for (RMCustomer customer : customers) {
            caseInfo.add(String.format("CUSTOMER %d R %d %s", customer.id,
                    (int) customer.serviceRevenue, customer.location.toString()));
            for (RMEntity component : customer.components) {
                RMCusOrder order = (RMCusOrder) component;
                caseInfo.add(order.toString());
            }
        }
        // 设备和价格
        for (RMSupplier supplier : suppliers) {
            supplier.rentFunctions.stream().map(RMSegmentFunction::toString).forEach(caseInfo::add);
        }
        equipments.stream().map(RMEquipment.class::cast).map(RMEquipment::toString).forEach(caseInfo::add);

        return caseInfo;
    }

    public static void setBaseScheduleCost(int baseCost) {
        BASE_SCHEDULE_COST = baseCost;
    }

}
