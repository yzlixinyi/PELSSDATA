package problem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class RMSupplier extends RMEntity {

    /**
     * 分段线性租金成本函数集合，{g_sf(t)}
     */
    List<RMSegmentFunction> rentFunctions;

    public RMSupplier(int _id) {
        super(_id, RMType.SUPPLIER);
        rentFunctions = new ArrayList<>();
    }

    public void addRentFunction(RMSegmentFunction function) {
        function.setSupplier(this);
        if (!rentFunctions.contains(function)) {
            rentFunctions.add(function);
        }
        rentFunctions.sort(Comparator.comparingInt(RMSegmentFunction::getId));
    }

    public List<RMSegmentFunction> getRentFunctions() {
        return rentFunctions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RMSupplier supplier = (RMSupplier) o;
        return Objects.equals(rentFunctions, supplier.rentFunctions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rentFunctions);
    }
}
