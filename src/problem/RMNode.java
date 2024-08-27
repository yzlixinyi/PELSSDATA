package problem;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RMNode {
    int idx;
    int id;
    int x;
    int y;

    boolean manhattanDist;

    public RMNode(int _x, int _y, boolean useManhattan) {
        x = _x;
        y = _y;
        manhattanDist = useManhattan;
    }

    public double calDist(RMNode e) {
        if (e == null) {
            return 0;
        }
        double dx = (double) e.x - this.x;
        double dy = (double) e.y - this.y;
        if (manhattanDist) {
            return Math.abs(dx) + Math.abs(dy);
        } else {
            return Math.round(Math.hypot(dx, dy));
        }
    }

    public String toString() {
        return String.format("[%d, %d]", x, y);
    }
}
