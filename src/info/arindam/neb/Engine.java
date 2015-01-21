package info.arindam.neb;

import info.arindam.neb.algorithms.Algorithm;
import info.arindam.neb.algorithms.Green1;
import info.arindam.neb.algorithms.Green2;
import info.arindam.neb.algorithms.Multi;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Arindam Biswas <arindam dot b at eml dot cc>
 */
public class Engine { // TODO: Make static methods instance methods.
    public static class Positive extends BufferedImage {
        public int[] buffer;

        public Positive(int width, int height) {
            super(width, height, BufferedImage.TYPE_INT_ARGB);
            buffer = ((DataBufferInt) this.getRaster().getDataBuffer()).getData();
        }
    }

    /*
     Each thread works on a single 'negative'. A negative is an object whose contents may
     vary depending on the algorithm. Multiple negatives are combined to obtain a 'positive',
     i.e. the final image.
     */
    public static class Negative {

        // #buffer holds the output of the thread this negative is assigned to.
        public int[][] buffer;
        // #data holds any other data that the algorithm might need.
        public HashMap<String, Object> data;

        Negative(int rasterWidth, int rasterHeight) {
            super();
            buffer = new int[rasterWidth][rasterHeight];
            data = new HashMap();
        }
    }

    private static class NebThread extends Thread {

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
                        Engine.taskCompleted(task);
                    } else {
                        synchronized (nebThreadLock) {
                            Engine.sleeping();
                            nebThreadLock.wait();
                        }
                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    static void sleeping() {
        synchronized (engineLock) {
            sleepingThreadCount++;
            if (sleepingThreadCount == threads.length) {
                canRun = true;
                engineLock.notify();
            }
        }
    }

    static void taskCompleted(Task task) {
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

    private static class Task {
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
    }

    private static int taskSampleSize, developedNegativeCount, sleepingThreadCount, rasterWidth,
            rasterHeight;
    private static Negative[] negatives;
    private static Positive positive;
    private static LinkedBlockingQueue<Task> taskQueue;
    private static NebThread[] threads;
    private static final Object nebThreadLock = new Object(), engineLock = new Object();
    private static HashMap<String, Object> parameters;
    private static Algorithm algorithm;
    private static Listener listener;
    private static boolean renderInProgress, canRun;

    public static void initialize() {
        threads = new NebThread[Runtime.getRuntime().availableProcessors()];
        taskSampleSize = 1000;
        rasterWidth = rasterHeight = 640;
        renderInProgress = false;
        negatives = null;
        positive = null;
        listener = null;
        parameters = Green1.getDefaultParameters();
        algorithm = new Green1(parameters);
        taskQueue = new LinkedBlockingQueue<>();
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new NebThread(taskQueue);
            threads[i].start();
        }
    }

    public static boolean setParameter(String parameter, Object value) {
        if (parameters.containsKey(parameter)) {
            parameters.put(parameter, value);
            return true;
        } else {
            return false;
        }
    }

    public static String getParameterString() {
        return parameters.toString();
    }

    public static boolean setAlgorithm(String name) {
        switch (name) {
            case "green_1":
                algorithm = new Green1(parameters);
                break;
            case "green_2":
                algorithm = new Green2(parameters);
                break;
            case "multi":
                algorithm = new Multi(parameters);
            default:
                return false;
        }
        return true;
    }

    public static void render(Listener listener) {
        Engine.listener = listener;
        int multiplier = algorithm.getNegativeMultiplier();
        negatives = new Negative[threads.length * multiplier];
        positive = new Positive(rasterWidth, rasterHeight);
        developedNegativeCount = 0;
        sleepingThreadCount = 0;
        renderInProgress = canRun = true;
        listener.renderingBegun();
        int i = 0, j = 0, taskIterationGoal = ((int) parameters.get("sample_size"))
                / (negatives.length * taskSampleSize);
        while (i < negatives.length) {
            negatives[i] = new Negative(rasterWidth, rasterHeight);
            negatives[i].data.put("sample_size", taskSampleSize);
            negatives[i].data.put("class", j);
            taskQueue.add(new Task(negatives[i], algorithm, taskIterationGoal));
            i++;
            j = (j + 1) % multiplier;
        }
    }

    // Returns the (partially) rendered positive or null, if no rendering has been done yet
    public static Positive getPositive() {
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

    public static void purge() {

    }
}
