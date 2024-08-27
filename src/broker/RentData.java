package broker;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class RentData {

    Map<String, List<Integer>> typeRentDay;
    Map<String, List<Integer>> typeRentMonth;

    public RentData() throws IOException {
        typeRentDay = CaseIO.readRent("./yun/rentByDay.txt");
        typeRentMonth = CaseIO.readRent("./yun/rentByMonth.txt");
    }
}
