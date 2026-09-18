package com.gregor0410.speedrunpractice.common.util;

/**
 * Prefixed logging (plan section 36). Version adapters may bridge these to
 * the game logger; the default writes to stdout/stderr so shared code and
 * tests stay dependency-free.
 */
public final class SpeedrunLogger {
    public static final String PREFIX = "[SpeedrunPractice] ";
    private static volatile boolean debugEnabled;

    private SpeedrunLogger() {
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void info(String message) {
        System.out.println(PREFIX + message);
    }

    public static void warn(String message) {
        System.out.println(PREFIX + "[WARN] " + message);
    }

    public static void error(String message) {
        System.err.println(PREFIX + "[ERROR] " + message);
    }

    public static void error(String message, Throwable cause) {
        System.err.println(PREFIX + "[ERROR] " + message);
        if (cause != null) {
            cause.printStackTrace(System.err);
        }
    }

    /** Verbose diagnostics (e.g. per-seed rejection reasons); silent unless enabled. */
    public static void debug(String message) {
        if (debugEnabled) {
            System.out.println(PREFIX + "[DEBUG] " + message);
        }
    }
}
