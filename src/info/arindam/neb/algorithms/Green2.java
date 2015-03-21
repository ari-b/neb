package info.arindam.neb.algorithms;

import java.util.LinkedHashMap;

/**
 *
 * @author Arindam Biswas <arindam dot b at eml dot cc>
 */
public class Green2 implements Algorithm {
    public static LinkedHashMap<String, String> DEFAULT_PARAMETERS = null;

    public Green2 (LinkedHashMap<String, String> parameters) {

    }

    @Override
    public int getNegativeMultiplier(int processorCount) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void run(info.arindam.neb.Engine.Negative negative) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void process(info.arindam.neb.Engine.Negative[] negatives, info.arindam.neb.Engine.Positive positive) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String toString() {
        return "green_2";
    }

    @Override
    public int getTaskIterationGoal(int processorCount) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
