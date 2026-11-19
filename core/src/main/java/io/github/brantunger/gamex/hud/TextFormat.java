package io.github.brantunger.gamex.hud;

import java.math.BigDecimal;
import java.util.Locale;

/** Writes numbers the way the menus and the HUD show them. */
public final class TextFormat {

    private TextFormat() {}

    /** @return the number with no more decimals than it needs: {@code 1.8} for 1.8 and {@code 2} for 2.0. */
    public static String decimal(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Not a finite number: " + value);
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }

    /** @return the scale as a whole percentage: {@code 70%} for 0.7. */
    public static String percent(float scale) {
        return Math.round(scale * 100f) + "%";
    }

    /** @return the scale as a whole percentage above or below 100: {@code +25%} for 1.25, {@code -20%} for 0.8. */
    public static String signedPercent(float scale) {
        int change = Math.round(scale * 100f) - 100;
        return (change < 0 ? "-" : "+") + Math.abs(change) + "%";
    }

    /** @return the time as minutes and whole seconds, as in {@code 1:42}; it stops at {@code 99:59}. */
    public static String clock(float seconds) {
        int whole = (int) Math.min(99 * 60 + 59, Math.max(0f, seconds));
        return String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60);
    }

    /**
     * @return the time as minutes, seconds and tenths, as in {@code 01:42.5}; a negative time and one that is not
     *     a finite number read {@code 00:00.0}
     */
    public static String stopwatch(float seconds) {
        float clamped = !Float.isFinite(seconds) || seconds < 0f ? 0f : seconds;
        long totalTenths = (long) Math.floor(clamped * 10.0);
        long mins = totalTenths / 600L;
        int secs = (int) ((totalTenths % 600L) / 10L);
        int tenths = (int) (totalTenths % 10L);
        return String.format(Locale.ROOT, "%02d:%02d.%d", mins, secs, tenths);
    }
}
