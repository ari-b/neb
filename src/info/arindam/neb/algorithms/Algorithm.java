package info.arindam.neb.algorithms;

import info.arindam.neb.Engine;
import java.util.HashMap;

/**
 *
 * @author Arindam Biswas <arindam dot b at eml dot cc>
 */
public abstract class Algorithm {
        public abstract int getNegativeMultiplier();
        public static HashMap<String, Object> getDefaultParameters() {
            return null;
        }
        public abstract void run(Engine.Negative negative);
        public abstract void process(Engine.Negative[] negatives, Engine.Positive positive);
}
