package info.arindam.neb;

import info.arindam.neb.algorithms.Algorithm;
import info.arindam.neb.algorithms.Green1;
import info.arindam.neb.algorithms.Green2;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.LinkedHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Arindam Biswas <arindam dot b at eml dot cc>
 */
public class Engine { // TODO: Add safeguards.

    public static class Positive extends BufferedImage {

        public int[] buffer;

        public Positive(Dimension rasterSize) {
            super(rasterSize.width, rasterSize.height, BufferedImage.TYPE_INT_ARGB);
            buffer = ((DataBufferInt) this.getRaster().getDataBuffer()).getData();
        }
    }

    /*
     Each thread works on a single 'negative'. A negative is an object whose contents may
     vary depending on the algorithm. Multiple negatives are combined to obtain a 'positive',
     i.e. the final image.
     */
    public class Negative {

        // #buffer holds the output of the thread this negative is assigned to.
        public int[][] buffer;
        // #data holds any other data that the algorithm might need.
        public LinkedHashMap<String, Object> data;

        Negative(Dimension rasterSize) {
            super();
            buffer = new int[rasterSize.width][rasterSize.height];
            data = new LinkedHashMap();
        }
    }

    private class NebThread extends Thread {

        boolean canRun;
        LinkedBlockingQueue<Task> taskQueue;

        NebThread(LinkedBlockingQueue<Task> taskQueue) {
            this.taskQueue = taskQueue;
            canRun = true;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    if (canRun) {
                        Task task = taskQueue.take();
                        task.run();
                        taskCompleted(task);
                    } else {
                        synchronized (nebThreadLock) {
                            sleeping();
                            nebThreadLock.wait();
                        }
                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    void sleeping() {
        synchronized (engineLock) {
            sleepingThreadCount++;
            if (sleepingThreadCount == threads.length) {
                canRun = true;
                engineLock.notify();
            }
        }
    }

    void taskCompleted(Task task) {
        if (task.iteration < task.iterationGoal) {
            task.iteration++;
            taskQueue.add(task);
        } else {
            developedNegativeCount++;
            if (developedNegativeCount == negatives.length) {
                algorithm.process(negatives, positive);
                renderInProgress = false;
                listener.renderingEnded();
            }
        }
    }

    private class Task {

        Algorithm algorithm;
        Negative negative;
        int iteration, iterationGoal;

        Task(Negative negative, Algorithm algorithm, int iterationGoal) {
            this.algorithm = algorithm;
            this.negative = negative;
            iteration = 1;
            this.iterationGoal = iterationGoal;
        }

        void run() {
            algorithm.run(negative);
        }
    }

    public static interface Listener {
        public void renderingBegun();

        public void renderingPaused();

        public void renderingResumed();

        public void renderingEnded();

        public void errorOccurred();

        public void algorithmSet(LinkedHashMap<String, String> newParameters);

        public void parametersSet();

        public void parametersReset(LinkedHashMap<String, String> newParameters);

        public void log(String message);
    }

    private int developedNegativeCount, sleepingThreadCount;
    private Dimension rasterSize;
    private Negative[] negatives;
    private Positive positive;
    private final LinkedBlockingQueue<Task> taskQueue;
    private final NebThread[] threads;
    private final Object nebThreadLock = new Object(), engineLock = new Object();
    private Algorithm algorithm;
    private final Listener listener;
    private boolean renderInProgress, canRun;

    public Engine(Listener listener) {
        threads = new NebThread[Runtime.getRuntime().availableProcessors()]; // One thread each.
        rasterSize = new Dimension(640, 640); // Set the raster size to a default of 640x640.
        renderInProgress = canRun = false;
        this.listener = listener;
        taskQueue = new LinkedBlockingQueue<>();
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new NebThread(taskQueue);
            threads[i].start();
        }
    }

    private static Algorithm getAlgorithmInstance(String name, LinkedHashMap<String, String> parameters) {
        Algorithm algorithm;

        switch (name) {
            case "green_1":
                algorithm = new Green1(parameters);
                break;
            case "green_2":
                algorithm = new Green2(parameters);
                break;
            default:
                algorithm = null;
        }
        return algorithm;
    }

    private static LinkedHashMap<String, String> getAlgorithmDefaultParameters(String name) {
        switch (name) {
            case "green_1":
                return Green1.DEFAULT_PARAMETERS;
            case "green_2":
                return Green2.DEFAULT_PARAMETERS;
            default:
                return null;
        }
    }

    public void setRasterSize(Dimension rasterSize) {
        this.rasterSize = rasterSize;
    }

    public boolean setAlgorithm(String name) { // TODO: Replace boolean signal with exception.
        LinkedHashMap<String, String> parameters = getAlgorithmDefaultParameters(name);
        algorithm = getAlgorithmInstance(name, parameters);

        listener.algorithmSet(parameters);
        return true;
    }

    public String getAlgorithm() {
        return algorithm.toString();
    }

    public boolean setParameters(LinkedHashMap<String, String> parameters) {
        // TODO: Check parameter sanity.
        algorithm = getAlgorithmInstance(algorithm.toString(), parameters);

        listener.parametersSet();
        return true;
    }

    public void resetParameters() {
        LinkedHashMap<String, String> parameters = getAlgorithmDefaultParameters(algorithm.toString());
        algorithm = getAlgorithmInstance(algorithm.toString(), parameters);

        listener.parametersReset(parameters);
    }

    public LinkedHashMap<String, String> getParameters() {
        return null;
    }

    public void render() {
        int processorCount = Runtime.getRuntime().availableProcessors();
        int multiplier = algorithm.getNegativeMultiplier(processorCount);
        negatives = new Negative[threads.length * multiplier];
        positive = new Positive(rasterSize);
        developedNegativeCount = 0;
        sleepingThreadCount = 0;
        renderInProgress = canRun = true;
        listener.renderingBegun();
        int i = 0, j = 0, taskIterationGoal = algorithm.getTaskIterationGoal(processorCount);
        while (i < negatives.length) {
            negatives[i] = new Negative(rasterSize);
            negatives[i].data.put("class", j);
            taskQueue.add(new Task(negatives[i], algorithm, taskIterationGoal));
            i++;
            j = (j + 1) % multiplier;
        }
        listener.log(String.format("Raster size: %dx%d\nNegatives: %d", rasterSize.width,
                rasterSize.height, negatives.length));
    }

    // Returns the (partially) rendered positive or null, if no rendering has been done yet.
    public Positive getPositive() {
        if (renderInProgress) {
            synchronized (engineLock) {
                for (NebThread thread : threads) {
                    thread.canRun = false;
                }
                canRun = false;
                try {
                    while (!canRun) {
                        engineLock.wait();
                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
                }
                listener.renderingPaused();
                algorithm.process(negatives, positive);
                for (NebThread thread : threads) {
                    thread.canRun = true;
                }
                sleepingThreadCount = 0;
            }
            synchronized (nebThreadLock) {
                nebThreadLock.notifyAll();
                listener.renderingResumed();
            }
            return positive;
        } else {
            return positive;
        }
    }
}
