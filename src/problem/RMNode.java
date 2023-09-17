package problem;

public class RMNode {
    int id;
    int x;
    int y;

    boolean manhattanDist;

    public RMNode(int _id, int _x, int _y) {
        id = _id;
        x = _x;
        y = _y;
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

    public void useManhattanDist() {
        manhattanDist = true;
    }

    public String toString() {
        return String.format("[%d, %d]", x, y);
    }
}
