package com.christian34.survivalgames;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs to the plugin's logger. Unexpected errors are logged with their exception (the server shows the stack trace,
 * printStackTrace bypasses the logger), expected problems (invalid config, ...) without.
 */
public class Debug {

    public static void warn(String message) {
        log(Level.WARNING, message, null);
    }

    /**
     * an unexpected error that the plugin survives
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

    private static Logger logger() {
        SurvivalGames plugin = SurvivalGames.getInstance();
        return plugin != null ? plugin.getLogger() : Logger.getLogger("SurvivalGames");
    }

}
