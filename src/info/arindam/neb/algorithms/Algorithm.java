package info.arindam.neb.algorithms;

import info.arindam.neb.Engine;
import java.awt.Dimension;
import java.awt.image.DataBuffer;

/**
 *
 * @author Arindam Biswas <arindam dot b at eml dot cc>
 */
public interface Algorithm {
    public int getNegativeMultiplier(int processorCount);

    public DataBuffer createNegativeBuffer(Dimension rasterSize);

    public void run(Engine.Negative negative);

    public void process(Engine.Negative[] negatives, Engine.Positive positive);

    @Override
    public String toString();

    public int getTaskIterationGoal(int processorCount);
}
