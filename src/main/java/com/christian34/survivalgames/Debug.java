package com.christian34.survivalgames;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs to the plugin's logger. Unexpected errors are logged with their exception: the server shows the stack trace
 * and {@link Telemetry} reports them to Sentry (System.err/printStackTrace bypasses both). Expected problems (invalid
 * config, database unreachable, ...) are logged without the exception, so they aren't reported.
 */
public class Debug {

    public static void warn(String message) {
        log(Level.WARNING, message, null);
    }

    /**
     * an unexpected error that the plugin survives (reported to Sentry)
     */
    public static void warn(String message, Throwable exception) {
        log(Level.WARNING, message, exception);
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    private static void log(Level level, String message, Throwable exception) {
        logger().log(level, message, exception);
    }

    /**
     * the logger of the plugin, where {@link Telemetry} listens
     */
    private static Logger logger() {
        SurvivalGames plugin = SurvivalGames.getInstance();
        return plugin != null ? plugin.getLogger() : Logger.getLogger("SurvivalGames");
    }

}
