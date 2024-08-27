package broker;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import lombok.Getter;

@Getter
public class Analysis {
    public final IloCplex.Status solutionStatus;
    public final IloCplex.CplexStatus CPLEXStatus;
    double GAP = 1.0;
    double L_B = 0.0;
    double U_B = Integer.MAX_VALUE;
    double T_I = -1;

    Analysis(IloCplex model, boolean recordGAP) throws IloException {
        solutionStatus = model.getStatus();
        CPLEXStatus = model.getCplexStatus();
        if (solutionStatus == IloCplex.Status.Feasible || solutionStatus == IloCplex.Status.Optimal) {
            L_B = model.getObjValue();
            if (recordGAP) {
                U_B = model.getBestObjValue();
                double diff = U_B - L_B;
                GAP = Math.abs(diff) < 1e-5 ? 0 : (diff / (U_B + 1e-10));
            }
        }
    }
}
