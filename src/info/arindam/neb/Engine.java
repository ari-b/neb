package site.bsws.neb;

import site.bsws.neb.algorithms.Algorithm;
import site.bsws.neb.algorithms.BBrot;
import site.bsws.neb.algorithms.DLA;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Arindam Biswas <arindam dot b at ftml dot net>
 */
public class Engine {
    // TODO: Add safeguards.

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
        public DataBuffer buffer;
        public Dimension size;
        // #data holds any other data that the algorithm might need.
        public HashMap<String, Object> data;

        Negative(DataBuffer buffer, Dimension size) {
            this.buffer = buffer;
            this.size = size;
            data = new HashMap<>();
        }
    }

    private class NebThread extends Thread {

        boolean pause, stop;
        LinkedBlockingQueue<Task> taskQueue;

        NebThread(LinkedBlockingQueue<Task> taskQueue) {
            this.taskQueue = taskQueue;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    if (pause) {
                        synchronized (nebThreadLock) {
                            sleeping();
                            nebThreadLock.wait();
                        }
                    } else if (stop) {
                        synchronized (nebThreadLock) {
                            stopped();
                            nebThreadLock.wait();
                        }
                    } else {
                        Task task = taskQueue.take();
                        task.run();
                        taskCompleted(task);
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

    void stopped() {
        synchronized (engineLock) {
            stoppedThreadCount++;
            if (stoppedThreadCount == threads.length) {
                canRun = true;
                engineLock.notify();
            }
        }
    }

    void taskCompleted(Task task) {
        if (task.iteration < task.iterationGoal) {
            task.iteration++;
            taskQueue.add(task);
            listener.negativeRendered();
        } else {
            developedNegativeCount++;
            if (developedNegativeCount == negatives.length) {
                synchronized (this) {
                    algorithm.process(negatives, positive);
                    renderInProgress = false;
                }
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

        public void negativeRendered();

        public void renderingPaused();

        public void renderingResumed();

        public void renderingEnded();

        public void errorOccurred();

        public void algorithmSet(LinkedHashMap<String, String> newParameters);

        public void parametersSet();

        public void parametersReset(LinkedHashMap<String, String> newParameters);

        public void log(String message);
    }

    private int developedNegativeCount, sleepingThreadCount, stoppedThreadCount;
    private Dimension rasterSize;
    private Negative[] negatives;
    private Positive positive;
    private final LinkedBlockingQueue<Task> taskQueue;
    private final NebThread[] threads;
    private final Object nebThreadLock = new Object(), engineLock = new Object();
    private Algorithm algorithm;
    private final Listener listener;
    private boolean renderInProgress, canRun, threadsAlive;

    public Engine(Listener listener) {
        threads = new NebThread[Runtime.getRuntime().availableProcessors()]; // One thread each.
        rasterSize = new Dimension(640, 640); // Set the raster size to a default of 640x640.
        positive = new Positive(rasterSize);
        this.listener = listener;
        taskQueue = new LinkedBlockingQueue<>();
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new NebThread(taskQueue);
        }
        canRun = true;
    }

    public void start() {
        if (threadsAlive) {
            return;
        }

        for (NebThread thread : threads) {
            thread.start();
        }
        sleepingThreadCount = stoppedThreadCount = 0;
        threadsAlive = true;
    }

    private void pauseThreads() {
        assert (sleepingThreadCount == 0);
        canRun = false;
        for (NebThread thread : threads) {
            thread.pause = true;
        }
        synchronized (engineLock) {
            try {
                while (!canRun) {
                    engineLock.wait();
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void resumeThreads() {
        for (NebThread thread : threads) {
            thread.pause = false;
        }
        sleepingThreadCount = 0;
        synchronized (nebThreadLock) {
            nebThreadLock.notifyAll();
        }
    }

    public void stop() {
        assert (stoppedThreadCount == 0);
        if (!threadsAlive) {
            return;
        }

        canRun = false;
        for (NebThread thread : threads) {
            thread.stop = true;
        }
        synchronized (engineLock) {
            try {
                while (!canRun) {
                    engineLock.wait();
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        threadsAlive = false;
    }

    private static Class getAlgorithmClass(String name) {
        switch (name) {
            case "bbrot":
                return BBrot.class;
            case "dla":
                return DLA.class;
            default:
                return null;
        }
    }

    private static Algorithm createAlgorithmInstance(Class algorithmClass,
            LinkedHashMap<String, String> parameters) {
        try {
            return (Algorithm) algorithmClass.getConstructors()[0].newInstance(parameters);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                InvocationTargetException ex) {
            Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    private static LinkedHashMap<String, String> getAlgorithmDefaultParameters(Class algorithmClass) {
        try {
            return (LinkedHashMap<String, String>) algorithmClass.getField("DEFAULT_PARAMETERS").
                    get(null);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException |
                SecurityException ex) {
            Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public void setRasterSize(Dimension rasterSize) {
        this.rasterSize = rasterSize;
        positive = new Positive(rasterSize);
    }

    public void setAlgorithm(String name) { // TODO: Replace boolean signal with exception.
        Class algorithmClass = getAlgorithmClass(name);
        LinkedHashMap<String, String> parameters = getAlgorithmDefaultParameters(algorithmClass);
        algorithm = createAlgorithmInstance(algorithmClass, parameters);

        listener.algorithmSet(parameters);
    }

    public String getAlgorithm() {
        return algorithm.toString();
    }

    public void setParameters(LinkedHashMap<String, String> parameters) {
        // TODO: Check parameter sanity.
        algorithm = createAlgorithmInstance(getAlgorithmClass(algorithm.toString()), parameters);

        listener.parametersSet();
    }

    public void resetParameters() {
        Class algorithmClass = getAlgorithmClass(algorithm.toString());
        LinkedHashMap<String, String> parameters = getAlgorithmDefaultParameters(algorithmClass);
        algorithm = createAlgorithmInstance(algorithmClass, parameters);

        listener.parametersReset(parameters);
    }

    public LinkedHashMap<String, String> getParameters() {
        return getAlgorithmDefaultParameters(getAlgorithmClass(algorithm.toString()));
    }

    public void render() {
        int processorCount = Runtime.getRuntime().availableProcessors();
        int multiplier = algorithm.getNegativeMultiplier(processorCount);
        negatives = new Negative[threads.length * multiplier];
        developedNegativeCount = 0;
        int i = 0, j = 0, taskIterationGoal = algorithm.getTaskIterationGoal(processorCount);
        while (i < negatives.length) {
            negatives[i] = new Negative(algorithm.createNegativeBuffer(rasterSize), rasterSize);
            negatives[i].data.put("residue_class", j);
            taskQueue.add(new Task(negatives[i], algorithm, taskIterationGoal));
            i++;
            j = (j + 1) % multiplier;
        }
        renderInProgress = true;
        listener.renderingBegun();
        listener.log(String.format("Raster size: %dx%d\nNegatives: %d", rasterSize.width,
                rasterSize.height, negatives.length));
    }

    public synchronized Positive getPositive() {
        if (renderInProgress) {
            pauseThreads();
            listener.renderingPaused();
            algorithm.process(negatives, positive);
            resumeThreads();
            listener.renderingResumed();
            return positive;
        } else {
            return positive;
        }
    }
}
