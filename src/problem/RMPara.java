package problem;

import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Getter
@Setter
public class RMPara {

    int mapScale = 0;

    /**
     * T
     */
    int nTimePeriod = 0;

    /**
     * max(q_i)
     */
    int maxNumLeaseTerm = 2;
    /**
     * max{c_i}
     */
    int maxNumCostSegment = 2;

    /**
     * nJ
     */
    int nCusOrder = 0;
    /**
     * nI
     */
    int nEquipment = 0;

    /**
     * 计算时间(s)超过此值，求解终止
     */
    int TIME_LIMIT = 1800;

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
            e.printStackTrace();
        }
        return sb.toString();
    }
}
