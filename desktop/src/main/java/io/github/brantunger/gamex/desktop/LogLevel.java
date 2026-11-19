package io.github.brantunger.gamex.desktop;

import java.util.Locale;

/**
 * Reads the log level the {@code GAMEX_LOG_LEVEL} environment variable asks for and hands it to Logback.
 * <p>
 * This class has no logger of its own, on purpose: {@link #install(String)} must run before the first logger is
 * created, because {@code logback.xml} reads the {@value #PROPERTY} system property once, when Logback starts.
 */
final class LogLevel {

    /** The system property {@code logback.xml} takes the root level from. */
    static final String PROPERTY = "gamex.logLevel";
    /** The level used when the variable is unset, blank or not a level. */
    static final String DEFAULT = "INFO";

    private LogLevel() {}

    /**
     * Reads a log level such as {@code debug}.
     * @param text the value of the {@code GAMEX_LOG_LEVEL} environment variable, or null
     * @return {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN} or {@code ERROR}, whatever the case of the
     *     text and the spaces around it; {@value #DEFAULT} when the text is missing, blank or none of those
     */
    static String resolve(String text) {
        return isValid(text) ? text.trim().toUpperCase(Locale.ROOT) : DEFAULT;
    }

    /**
     * @param text the value of the {@code GAMEX_LOG_LEVEL} environment variable, or null
     * @return whether the text was given and is not a level, so that the launcher should warn about it
     */
    static boolean isRejected(String text) {
        return text != null && !text.trim().isEmpty() && !isValid(text);
    }

    /**
     * Sets {@value #PROPERTY} to the level the text asks for. Call it before any logger exists.
     * @param text the value of the {@code GAMEX_LOG_LEVEL} environment variable, or null
     * @return the level that was set
     */
    static String install(String text) {
        String level = resolve(text);
        System.setProperty(PROPERTY, level);
        return level;
    }

    private static boolean isValid(String text) {
        if (text == null) return false;
        switch (text.trim().toUpperCase(Locale.ROOT)) {
            case "TRACE":
            case "DEBUG":
            case "INFO":
            case "WARN":
            case "ERROR":
                return true;
            default:
                return false;
        }
    }
}
