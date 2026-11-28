package com.christian34.survivalgames;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A separate thread notices when the server thread is blocked, samples where it hangs and lets {@link Telemetry}
 * report it if the plugin caused it.
 *
 * @author Christian34
 */
final class LagWatchdog {
    /**
     * a lag report is sent at the latest after the server thread is blocked this long
     */
    static final long LAG_REPORT_AFTER = 10_000;
    /**
     * the server thread is sampled from this many milliseconds of a stall on (a stall of 1 s gets about 8 samples)
     */
    static final long SAMPLE_AFTER = 200;
    /**
     * at most this many samples of one stall
     */
    static final int MAX_SAMPLES = 100;
    private final SurvivalGames plugin;
    private final Telemetry telemetry;
    @Nullable
    private BukkitTask tickTask;
    @Nullable
    private Thread thread;
    /**
     * System.nanoTime() of the last server tick (0: the server isn't ticking yet)
     */
    private volatile long lastTick;

    LagWatchdog(SurvivalGames plugin, Telemetry telemetry) {
        this.plugin = plugin;
        this.telemetry = telemetry;
    }

    /**
     * a separate thread notices when the server thread is blocked (sentry.lag-threshold milliseconds) by the code of
     * the plugin, and reports where it hangs (lag spikes, freezes); 0 turns it off
     */
    void start(long threshold) {
        if (threshold <= 0 || !Bukkit.isPrimaryThread()) return;
        Thread main = Thread.currentThread();
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> lastTick = System.nanoTime(), 1, 1);
        Thread thread = new Thread(() -> watch(main, threshold), "SurvivalGames Watchdog");
        thread.setDaemon(true);
        thread.start();
        this.thread = thread;
    }

    private void watch(Thread main, long threshold) {
        // the server thread is sampled every 100 ms while it is blocked, so the report shows where it spent the time
        long sampleAfter = Math.min(SAMPLE_AFTER, threshold / 2);
        long stalledTick = 0;
        long blocked = 0;
        long gcBefore = 0;
        List<StackTraceElement[]> samples = new ArrayList<>();
        boolean sent = false;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                return;
            }
            long last = lastTick;
            if (last == 0 || Bukkit.isStopping()) continue;
            long millis = (System.nanoTime() - last) / 1_000_000;
            if (millis >= sampleAfter) {
                if (stalledTick != last) {
                    // a new stall
                    stalledTick = last;
                    samples.clear();
                    sent = false;
                    gcBefore = gcMillis();
                }
                blocked = millis;
                if (samples.size() < MAX_SAMPLES) samples.add(main.getStackTrace());
                if (!sent && millis >= LAG_REPORT_AFTER) {
                    sent = true;
                    telemetry.reportLag(samples, millis, gcMillis() - gcBefore, true);
                }
            } else if (stalledTick != 0) {
                if (!sent && blocked >= threshold) telemetry.reportLag(samples, blocked, gcMillis() - gcBefore, false);
                stalledTick = 0;
                samples.clear();
            }
        }
    }

    /**
     * @return the time the garbage collectors have needed since the JVM started
     */
    private static long gcMillis() {
        long millis = 0;
        for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            millis += Math.max(0, collector.getCollectionTime());
        }
        return millis;
    }

    /**
     * where the server thread was blocked by the plugin
     *
     * @param stack    a sample at that place
     * @param frame    the topmost line of the plugin
     * @param samples  all samples of the stall
     * @param inPlugin the samples with the plugin in the stack
     * @param atFrame  the samples at that line
     */
    record LagCulprit(StackTraceElement[] stack, String frame, int samples, int inPlugin, int atFrame) {
    }

    /**
     * @return the line of the plugin the server thread spent most samples in, null if the plugin isn't in at least
     * half of the samples (another plugin, the server itself, or a pause of the JVM)
     */
    @Nullable
    static LagCulprit culprit(List<StackTraceElement[]> samples) {
        Map<String, List<StackTraceElement[]>> byFrame = new LinkedHashMap<>();
        int inPlugin = 0;
        for (StackTraceElement[] stack : samples) {
            StackTraceElement frame = ownFrame(stack);
            if (frame == null) continue;
            inPlugin++;
            byFrame.computeIfAbsent(frame.toString(), key -> new ArrayList<>()).add(stack);
        }
        if (inPlugin == 0 || inPlugin * 2 < samples.size()) return null;
        Map.Entry<String, List<StackTraceElement[]>> most = null;
        for (Map.Entry<String, List<StackTraceElement[]>> entry : byFrame.entrySet()) {
            if (most == null || entry.getValue().size() > most.getValue().size()) most = entry;
        }
        return new LagCulprit(most.getValue().getFirst(), most.getKey(), samples.size(), inPlugin,
                most.getValue().size());
    }

    /**
     * @return the topmost line of the plugin in the stack (not its bundled libraries)
     */
    @Nullable
    static StackTraceElement ownFrame(StackTraceElement[] stack) {
        for (StackTraceElement element : stack) {
            if (Telemetry.isOwnClass(element.getClassName())) return element;
        }
        return null;
    }

    /**
     * @return true if the JVM loads or links classes above the plugin (warm-up of a slow server, e.g. the first call of
     * a lambda): not caused by the code of the plugin
     */
    static boolean isClassLoading(StackTraceElement[] stack) {
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (Telemetry.isOwnClass(className)) return false;
            if (className.startsWith("java.lang.invoke.") || className.startsWith("jdk.internal.loader.")
                    || className.startsWith("jdk.internal.reflect.") || className.equals("java.lang.ClassLoader")
                    || element.getMethodName().equals("<clinit>")) {
                return true;
            }
        }
        return false;
    }

    static boolean hasOwnFrame(StackTraceElement[] stack) {
        return ownFrame(stack) != null;
    }

    void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    /**
     * the server thread was blocked by the plugin, the stack trace shows where
     */
    static class ServerThreadBlocked extends RuntimeException {
        ServerThreadBlocked(long millis, boolean stillBlocked, StackTraceElement[] stack) {
            super("The server thread was blocked for " + millis + " ms" + (stillBlocked ? " (and still is)" : ""),
                    null, false, true);
            setStackTrace(stack);
        }
    }

}
