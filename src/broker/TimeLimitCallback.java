package broker;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import problem.RMSolution;

/**
 * <p>Information callback with abortion based on time limit.</p>
 * Spend at most timeLimit seconds on optimization, but once
 * this limit is reached, quit as soon as the solution is acceptable.
 */
public class TimeLimitCallback extends IloCplex.MIPInfoCallback {
    IloCplex _cplex;
    boolean _aborted;
    double _timeLimit;
    double _timeStart;
    double _acceptableGap;
    boolean _record;
    double bestObjTime = -1;
    double bestObj;
    int minuteRecord = 1;
    Model model;
    String filePath;

    TimeLimitCallback(IloCplex cplex, boolean aborted, double timeStart,
                      int timeLimit, double acceptableGap, String path, String name) {
        _cplex = cplex;
        _aborted = aborted;
        _timeStart = timeStart;
        _timeLimit = timeLimit;
        _acceptableGap = acceptableGap;
        if (path != null) {
            filePath = path;
            _record = true;
            WriteText.setFilePathName(name, path);
        }
    }

    public void main() throws IloException {
        double timeUsed;
        int minuteUsed;
        boolean newIncumbent = false;
        if (!_aborted && hasIncumbent()) {
            timeUsed = _cplex.getCplexTime() - _timeStart;
            minuteUsed = (int) Math.ceil(timeUsed / 60);
            double upperBound = getBestObjValue();
            double lowerBound = getIncumbentObjValue();
            double gap = (upperBound - lowerBound) / Math.abs(upperBound + 1e-10);
            if (lowerBound - bestObj >= 1) {
                bestObj = lowerBound;
                bestObjTime = timeUsed;
                newIncumbent = true;
            }
            if (_record && (newIncumbent || (minuteUsed > minuteRecord) || (gap <= _acceptableGap))) {
                minuteRecord = minuteUsed;
                String stamp = String.format("%s\t%s\t%s\t%s",
                        Tools.df3.format(timeUsed),
                        Tools.df6.format(gap),
                        Tools.df1.format(lowerBound),
                        Tools.df1.format(upperBound));
                System.out.println(stamp);
                WriteText.writeLine(stamp);
                if (newIncumbent) {
                    saveIncumbentSolution();
                }
            }
            if (timeUsed >= _timeLimit || gap <= _acceptableGap) {
                System.out.printf("CallbackInfo: incumbent %s s, %s s, gap = %s, LB = %s, UB = %s, quitting.%n",
                        Tools.df3.format(bestObjTime),
                        Tools.df3.format(timeUsed),
                        Tools.df6.format(gap),
                        Tools.df2.format(lowerBound),
                        Tools.df2.format(upperBound));
                _aborted = true;
                abort();
            }
        }
    }

    private void saveIncumbentSolution() throws IloException {
        if (model == null) {
            return;
        }
        RMSolution solution = new RMSolution(model.getProblem());
        solution.setObj(bestObj);
        solution.setZ_p(getIncumbentValues(model.getZ_p()));
        for (int i = 0; i < solution.getX_ijw().length; i++) {
            if (solution.getHB_iw()[i].length == 0) continue;
            for (int j = 0; j < solution.getY_ijt()[i].length; j++) {
                solution.setY(i, j, getIncumbentValues(model.getY_ijt()[i][j]));
                solution.setX(i, j, getIncumbentValues(model.getX_ijw()[i][j]));
                solution.setS(i, j, getIncumbentValues(model.getS_ijj()[i][j]));
            }
            for (int k = 0; k < solution.getV_ikw()[i].length; k++) {
                solution.setV(i, k, getIncumbentValues(model.getV_ikw()[i][k]));
                solution.setGamma(i, k, getIncumbentValues(model.getGamma_ikn()[i][k]));
                solution.setTD(i, k, getIncumbentValues(model.getTD_ikn()[i][k]));

            }
            solution.setHB(i, getIncumbentValues(model.getHB_iw()[i]));
            solution.setHE(i, getIncumbentValues(model.getHE_iw()[i]));
            solution.setTB(i, getIncumbentValues(model.getTB_ik()[i]));
            solution.setTE(i, getIncumbentValues(model.getTE_ik()[i]));
        }
        CaseIO.writeFeasibleSolution(solution, model.getProblem(), filePath);
    }
}
