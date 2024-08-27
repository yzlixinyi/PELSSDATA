package problem;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public abstract class RMEntity {
    /**
     * 类型
     */
    RMType type;
    /**
     * 编号
     */
    int id;
    /**
     * 索引
     */
    int index;
    /**
     * 位置
     */
    RMNode location;
    /**
     * 开始时间（不含）
     */
    int tB;
    /**
     * 结束时间（包含）
     */
    int tE;
    /**
     * 包含成员集合: 客户[客户订单],供应商[设备],
     */
    List<RMEntity> components;

    /**
     * 归属上层实体: 客户订单-客户,设备-供应商,工单-设备,租约-设备
     */
    RMEntity affiliation;

    static final int RM_INVALID_ID = 0;
    static final int RM_INVALID_INDEX = -1;

    RMEntity(int _id, RMType _type) {
        id = _id;
        type = _type;
        index = RM_INVALID_INDEX;
    }

    RMEntity(RMType _type) {
        type = _type;
        index = RM_INVALID_INDEX;
    }

    /**
     * 设置隶属关系
     *
     * @param affiliation 归属的上层实体
     */
    public void affiliated(RMEntity affiliation) {
        this.affiliation = affiliation;
        this.location = affiliation.location;
    }

    public void addComponent(RMEntity component) {
        if (components == null) {
            components = new ArrayList<>();
        }
        components.add(component);
    }

    /**
     * (tB, tE]
     *
     * @param timeBegin, exclusive start time
     * @param timeEnd,   inclusive finish time
     */
    public void setTimeRange(int timeBegin, int timeEnd) {
        tB = timeBegin;
        tE = timeEnd;
    }

    /**
     * @return 限定类型ID
     */
    public int getSubType() {
        return RM_INVALID_ID;
    }

    public int getTD() {
        return tE - tB;
    }

    public String timeWindowToString() {
        return String.format("(%d, %d]", tB, tE);
    }
}
