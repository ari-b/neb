package site.bsws.neb.algorithms;

import java.awt.Dimension;
import java.awt.image.DataBufferUShort;
import java.util.LinkedHashMap;

/**
 *
 * @author Arindam Biswas <arindam dot b at eml dot cc>
 */
public class MBrot implements Algorithm {
    private final int iterationLimit, colourShift, degree, aaMultiplier;
    private final double minX, minY, rangeX, rangeY, escapeDistance;
    public static final LinkedHashMap<String, String> DEFAULT_PARAMETERS;

    static {
        DEFAULT_PARAMETERS = new LinkedHashMap<>();
        DEFAULT_PARAMETERS.put("min_x", "-2.0");
        DEFAULT_PARAMETERS.put("min_y", "-1.5");
        DEFAULT_PARAMETERS.put("range_x", "3.0");
        DEFAULT_PARAMETERS.put("range_y", "3.0");
        DEFAULT_PARAMETERS.put("iteration_limit", "100");
        DEFAULT_PARAMETERS.put("escape_distance", "2.0");
        DEFAULT_PARAMETERS.put("degree", "2");
        DEFAULT_PARAMETERS.put("colour", "blue");
        DEFAULT_PARAMETERS.put("aa", "1");
        DEFAULT_PARAMETERS.put("--", "--");
    }

    public MBrot (LinkedHashMap<String, String> parameters) {
        minX = Double.parseDouble(parameters.get("min_x"));
        minY = Double.parseDouble(parameters.get("min_y"));
        rangeX = Double.parseDouble(parameters.get("range_x"));
        rangeY = Double.parseDouble(parameters.get("range_y"));
        iterationLimit = Integer.parseInt(parameters.get("iteration_limit"));
        degree = Integer.parseInt(parameters.get("degree"));
        aaMultiplier = Integer.parseInt(parameters.get("aa"));
        escapeDistance = Double.parseDouble(parameters.get("escape_distance"));
        String colour = (String) parameters.get("colour");
        switch (colour) {
            case "red":
                colourShift = 16;
                break;
            case "green":
                colourShift = 8;
                break;
            default:
                colourShift = 0;
        }
    }

    @Override
    public int getNegativeMultiplier(int processorCount) {
        return aaMultiplier;
    }

    @Override
    public void run(site.bsws.neb.Engine.Negative negative) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void process(site.bsws.neb.Engine.Negative[] negatives, site.bsws.neb.Engine.Positive positive) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String toString() {
        return "mbrot";
    }

    @Override
    public int getTaskIterationGoal(int processorCount) {
        return 1;
    }

    @Override
    public DataBufferUShort createNegativeBuffer(Dimension rasterSize) {
        return new DataBufferUShort((int) (rasterSize.width * rasterSize.height), 2);
    }
}
