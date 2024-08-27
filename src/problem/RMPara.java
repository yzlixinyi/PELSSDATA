package problem;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Getter
@Setter
public class RMPara {

    int mapScale = 0;
    // 模型参数

    /**
     * nP
     */
    int nCustomer = 0;
    /**
     * T
     */
    int nTimePeriod = 0;

    /**
     * <p>[能力集]所有设备最大租约数，restricted (K)</p>
     * <p>每个设备的最大租约数，首先由最大工单数决定，即由决策周期长度和匹配订单数决定</p>
     * <p>若使用能力集参数(K > 0)，则对所有设备最大租约数进行二次限制: q_i = min(q_i, K)</p>
     * <p>若不使用该参数，可由q_i倒推一个有效值: K = max(q_i)</p>
     */
    int maxNumLeaseTerm = 2;
    /**
     * [能力集]所有设备最大成本函数段数，max{c_i}，knowledge
     */
    int maxNumCostSegment = 2;

    /**
     * nF
     */
    int nFlexible = 0;
    /**
     * nJ
     */
    int nCusOrder = 0;
    /**
     * nI
     */
    int nEquipment = 0;

    /**
     * <p>FALSE: init greedy works</p>
     * <p>TRUE: enumerate all non-split works</p>
     */
    boolean initNonSplit = false;
    boolean useConsUL = true;
    boolean UBNonNegativeSP = true;
    boolean greedyUpperBound = true;
    int nodeDepthGreedyUB = 10;

    /**
     * 是否在根节点终止
     */
    boolean rootNodeEnd = false;
    /**
     * 计算时间(s)超过此值，求解终止
     */
    int TIME_LIMIT = 1800;
    /**
     * GAP低于此值，求解终止
     */
    double MIP_GAP = 1e-3;
    // Exact cplex
    /**
     * .sav模型文件名，空即不存
     */
    String exactModelName = null;

    // 搜索方法
    SearchMethod searchMethod = SearchMethod.BEST_UB_FIRST;

    // 子问题求解方法
    /**
     * 是否用动态规划求解租赁子问题SPL
     */
    boolean splSolverDP = true;
    /**
     * 是否用cplex验证动态规划求解SPL的正确性
     */
    boolean checkSPLSolverDP;

    SPWMethod spwSolver = SPWMethod.LABEL;

    boolean labelExtendByOrder = false;

    boolean storeExtendLabels = true;

    boolean forbidSplits = false;

    boolean allowReVisit = false;

    boolean flexibleTimeWindow = false;

    /**
     * 是否将松弛解可行化为整数解
     */
    boolean repairIntegrality = true;
    /**
     * SP每次最多添加的列数 <= nCopySafe
     */
    int nColumnAddedSP = -1;

    /**
     * SP列数保护
     */
    int nCopySafe = 64;

    public enum SPWMethod {
        CPLEX,
        LABEL,
        ENUMERATE,
    }

    public enum SearchMethod {
        BEST_UB_FIRST,
        BREADTH_FIRST,
        DEPTH_FIRST,
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            // 获得当前类的class
            Class<? extends RMPara> clazz = this.getClass();
            // 获取当前类的全部属性
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                // 遍历属性得到属性名
                String fieldName = field.getName();
                // 如果是用于序列化的直接过滤掉
                if ("serialVersionUID".equals(fieldName)) {
                    continue;
                }
                // 判断属性的类型，主要是区分boolean和其他类型
                Class<?> type = field.getType();
                // boolean取值是is，其他是get
                String methodName = (type.getTypeName().equals("boolean") ? "is" : "get")
                        + fieldName.substring(0, 1).toUpperCase()
                        + fieldName.substring(1);
                // 通过方法名得到方法对象
                Method method = clazz.getMethod(methodName);
                // 得到方法的返回值
                Object resultObj = method.invoke(this);
                // 将属性名和它对应的值进行匹配打印
                if (resultObj != null && !"".equals(resultObj)) {
                    sb.append(fieldName).append(": ").append(resultObj).append("\n");
                }
            }
        } catch (Exception e) {
            LogManager.getLogger(RMPara.class).error(e);
        }
        return sb.toString();
    }
}
