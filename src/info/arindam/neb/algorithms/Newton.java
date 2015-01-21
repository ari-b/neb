package info.arindam.neb.algorithms;

import info.arindam.neb.Engine;
import java.util.HashMap;

/**
 *
 * @author Arindam Biswas <arindam dot b at eml dot cc>
 */
public class Newton extends Algorithm {

    @Override
    public int getNegativeMultiplier() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public static HashMap<String, Object> getDefaultParameters() {
        HashMap<String, Object> parameters = new HashMap();
        return parameters;
    }

    @Override
    public void run(Engine.Negative negative) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void process(Engine.Negative[] negatives, Engine.Positive positive) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
