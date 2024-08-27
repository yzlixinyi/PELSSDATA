package broker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

public class Tools {

    private Tools() {
    }

    // output format
    public static final DecimalFormat df1 = new DecimalFormat("0.0");
    public static final DecimalFormat df2 = new DecimalFormat("0.00");
    public static final DecimalFormat df3 = new DecimalFormat("0.000");
    public static final DecimalFormat df4 = new DecimalFormat("0.0000");
    public static final DecimalFormat df6 = new DecimalFormat("0.000000");
    public static final double HALF = 0.5;
    public static final double PRECISION = 1e-3;
    public static final int INVALID_INDEX = -1;

    private static double halfUpSmooth(double v, int digits) {
        return BigDecimal.valueOf(v).setScale(digits, RoundingMode.HALF_UP).doubleValue();
    }

    public static double copyCPLEXSmooth(double a) {
        return halfUpSmooth(a, 7);
    }

    public static boolean[][] copy(boolean[][] a) {
        boolean[][] c = new boolean[a.length][];
        for (int i = 0; i < c.length; i++) {
            c[i] = copy(a[i]);
        }
        return c;
    }

    public static boolean[] copy(boolean[] a) {
        return a != null ? Arrays.copyOf(a, a.length) : null;
    }

    public static int[] copy(int[] a) {
        return a != null ? Arrays.copyOf(a, a.length) : null;
    }

    public static int[][] copy(int[][] a) {
        int[][] c = new int[a.length][];
        for (int i = 0; i < c.length; i++) c[i] = copy(a[i]);
        return c;
    }

    public static int[][][] copy(int[][][] a) {
        int[][][] c = new int[a.length][][];
        for (int i = 0; i < c.length; i++) {
            c[i] = copy(a[i]);
        }
        return c;
    }

    /**
     * @param a 一维数组
     * @return 复制的一维数组
     */
    public static double[] copy(double[] a) {
        return a != null ? Arrays.copyOf(a, a.length) : null;
    }

    public static double[] copyCPLEXSmooth(double[] a) {
        double[] c = new double[a.length];
        for (int i = 0; i < c.length; i++) {
            c[i] = copyCPLEXSmooth(a[i]);
        }
        return c;
    }

    public static double[] copySmooth(double[] a) {
        double[] c = new double[a.length];
        for (int i = 0; i < c.length; i++) {
            c[i] = halfUpSmooth(a[i], 5);
        }
        return c;
    }

    public static void smooth(double[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = halfUpSmooth(a[i], 5);
        }
    }

    public static int[] copyToInt(double[] a) {
        int[] c = new int[a.length];
        for (int i = 0; i < c.length; i++) {
            c[i] = (int) Math.round(a[i]);
        }
        return c;
    }

    public static double[] copyToDouble(int[] a) {
        return Arrays.stream(Arrays.copyOf(a, a.length)).mapToDouble(value -> (double) value).toArray();
    }

    /**
     * @param a 二维数组
     * @return 复制的二维数组
     */
    public static double[][] copy(double[][] a) {
        if (a == null) return null;
        double[][] c = new double[a.length][];
        for (int i = 0; i < c.length; i++) c[i] = copy(a[i]);
        return c;
    }

    public static void smooth(double[][] a) {
        for (double[] doubles : a) {
            smooth(doubles);
        }
    }

    public static int[][] copyToInt(double[][] a) {
        int[][] c = new int[a.length][];
        for (int i = 0; i < c.length; i++) c[i] = copyToInt(a[i]);
        return c;
    }

    public static double[][] copyToDouble(int[][] a) {
        double[][] c = new double[a.length][];
        for (int i = 0; i < c.length; i++) {
            c[i] = copyToDouble(a[i]);
        }
        return c;
    }

    /**
     * @param a 三维数组
     * @return 复制的三维数组
     */
    public static double[][][] copy(double[][][] a) {
        double[][][] c = new double[a.length][][];
        for (int i = 0; i < c.length; i++) c[i] = copy(a[i]);
        return c;
    }

    public static void smooth(double[][][] a) {
        for (double[][] doubles : a) {
            smooth(doubles);
        }
    }

    public static int[][][] copyToInt(double[][][] a) {
        int[][][] c = new int[a.length][][];
        for (int i = 0; i < c.length; i++) {
            c[i] = copyToInt(a[i]);
        }
        return c;
    }

    public static double[][][] copyToDouble(int[][][] a) {
        double[][][] c = new double[a.length][][];
        for (int i = 0; i < c.length; i++) {
            c[i] = copyToDouble(a[i]);
        }
        return c;
    }

    public static double[][][][] copy(double[][][][] a) {
        double[][][][] c = new double[a.length][][][];
        for (int i = 0; i < c.length; i++) c[i] = copy(a[i]);
        return c;
    }

    public static boolean differ(double v1, double v2) {
        return Math.abs(v1 - v2) >= PRECISION;
    }

    public static boolean isNotInteger(double value) {
        return Math.abs(value - Math.round(value)) >= PRECISION;
    }

    public static double getAbsoluteSum(double[][] array) {
        double temp = 0;
        for (double[] a : array) temp += getAbsoluteSum(a);
        return temp;
    }

    public static double getAbsoluteSum(double[] array) {
        if (array == null) {
            return 0;
        }
        double temp = 0;
        for (double a : array) temp += Math.abs(a);
        return temp;
    }

    public static double getArraySumRange(double[] array, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += array[i];
        }
        return sum;
    }

    /**
     * @param array 一维数组
     * @return 数组各项和
     */
    public static double getArraySum(double[] array) {
        if (array == null) {
            return 0;
        }
        return Arrays.stream(array).sum();
    }

    /**
     * @param array 二维数组
     * @return 数组各项和
     */
    public static double getArraySum(double[][] array) {
        double temp = 0;
        for (double[] a : array) temp += getArraySum(a);
        return temp;
    }

    /**
     * 0-1变量按区间取整
     */
    public static void roundToInteger(double[] array, double range) {
        if (range < PRECISION) {
            Arrays.fill(array, 0);
            return;
        }
        double half = halfUpSmooth(range * 0.5, 3);
        for (int i = 0; i < array.length; i++) {
            if (array[i] < half) {
                array[i] = 0;
            } else {
                array[i] = 1;
            }
        }
    }

    public static void fill(double[][][] tensor, int val) {
        for (double[][] matrix : tensor) {
            fill(matrix, val);
        }
    }

    public static void fill(double[][] matrix, int val) {
        for (double[] array : matrix) {
            Arrays.fill(array, val);
        }
    }

    public static int findFirstOne(int fromIndex, int[] array) {
        for (int i = fromIndex; i < array.length; i++) {
            if (array[i] == 1) {
                return i;
            }
        }
        return INVALID_INDEX;
    }

    public static void getSchemeSum(double[] variable, double use, int[] schemeValue) {
        for (int v = 0; v < variable.length; v++) {
            variable[v] += schemeValue[v] * use;
        }
    }

    /**
     * 打印列生成情况
     *
     * @param newColumn 产生了新列
     * @param oldColumn 产生了重复列
     */
    public static void printPricingStatus(boolean newColumn, boolean oldColumn) {
        if (newColumn && oldColumn) {
            System.out.println("New column(s) and old column(s) generated.");
        } else if (newColumn) {
            System.out.println("New column(s) generated.");
        } else if (oldColumn) {
            System.out.println("Old column(s) generated.");
        } else {
            System.out.println("No column generated.");
        }
    }

    /**
     * return v > 0
     */
    public static boolean nonNegative(double v) {
        return v > PRECISION;
    }

    /**
     * @return 数组各元素相加得到的新数组（数组长度取短）
     */
    public static double[] plus(double[] a1, double[] a2) {
        int length = Math.min(a1.length, a2.length);
        double[] sum = new double[length];
        for (int i = 0; i < length; i++) {
            sum[i] = a1[i] + a2[i];
        }
        return sum;
    }

    public static double diffHalf(double v) {
        double floor = Math.floor(v);
        return Math.abs(floor + 0.5 - v);
    }

    public static int translateToInteger(String s) {
        String[] seq = s.split("-");
        int type = 0;
        for (int i = 0; i < 3; i++) {
            int v = Integer.parseInt(seq[i].substring(1));
            type += (int) (v * Math.pow(10, 4.0 - i));
        }
        type += Integer.parseInt(seq[3].substring(1));
        return type;
    }

    public static String fileTimeStamp() {
        Calendar calendar = Calendar.getInstance(); // get current instance of the calendar
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss");
        return formatter.format(calendar.getTime());
    }
}
