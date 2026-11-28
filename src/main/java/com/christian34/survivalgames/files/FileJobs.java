package com.christian34.survivalgames.files;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Work on the files of maps and worlds (copying a map, trimming it) off the server thread: a big map takes seconds.
 * <p>
 * The jobs on the same folder run one after the other, in the order they were started: a world is only copied once
 * the copy before it has been deleted, a map is only trimmed when no copy reads it anymore. Jobs on other folders run
 * at the same time.
 *
 * @author Christian34
 */
public final class FileJobs {
    /**
     * the last job of every folder (absolute path), removed once it is done
     */
    private static final Map<String, CompletableFuture<Void>> LAST = new ConcurrentHashMap<>();
    @Nullable
    private static ExecutorService service;
    @Nullable
    private static Executor testExecutor;

    private FileJobs() {
    }

    /**
     * only for the tests: the jobs run right away on the calling thread
     */
    public static void setExecutor(@Nullable Executor executor) {
        testExecutor = executor;
    }

    private static synchronized Executor executor() {
        if (testExecutor != null) return testExecutor;
        if (service == null) {
            AtomicInteger number = new AtomicInteger();
            service = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "SurvivalGames Files #" + number.incrementAndGet());
                // the server doesn't wait for a copy when it stops - the leftover is deleted on the next start
                thread.setDaemon(true);
                return thread;
            });
        }
        return service;
    }

    private static String key(File folder) {
        return folder.getAbsoluteFile().toPath().normalize().toString();
    }

    /**
     * runs the job off the server thread, after the jobs started before on any of the folders (call it on the server
     * thread)
     *
     * @return completes when the job is done (exceptionally if it threw)
     */
    public static CompletableFuture<Void> run(Runnable job, File... folders) {
        CompletableFuture<?>[] before = new CompletableFuture<?>[folders.length];
        for (int i = 0; i < folders.length; i++) {
            before[i] = LAST.getOrDefault(key(folders[i]), CompletableFuture.completedFuture(null));
        }
        // a failed job doesn't stop the next one
        CompletableFuture<Void> future = CompletableFuture.allOf(before).handle((result, failure) -> null)
                .thenRunAsync(job, executor());
        for (File folder : folders) {
            String key = key(folder);
            LAST.put(key, future);
            future.whenComplete((result, failure) -> LAST.remove(key, future));
        }
        return future;
    }

    /**
     * waits until the jobs on the folder are done (before the server thread changes it itself)
     */
    public static void await(File folder) {
        CompletableFuture<Void> last = LAST.get(key(folder));
        if (last == null) return;
        try {
            last.join();
        } catch (CompletionException ignored) {
            // the job has failed - its caller reports it
        }
    }

    /**
     * waits until all jobs are done (the plugin is disabled: the last saves of the games are written), at most for
     * the given time - a copy of a big map isn't needed anymore and is deleted on the next start
     */
    public static void awaitAll(long millis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        for (CompletableFuture<Void> last : LAST.values().toArray(CompletableFuture[]::new)) {
            try {
                last.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (ExecutionException | CancellationException ignored) {
                // the job has failed - its caller reports it
            } catch (TimeoutException ex) {
                Debug.warn("File jobs are still running, the server doesn't wait for them");
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * runs the task on the server thread: right away if it is called there (the job has been done already), otherwise
     * with the next tick - not at all once the plugin is disabled
     */
    public static void onServerThread(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                Debug.warn("Error after a file job", ex);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            safe.run();
            return;
        }
        SurvivalGames plugin = SurvivalGames.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, safe);
        } catch (RuntimeException ignored) {
            // the plugin has been disabled right now
        }
    }

}
