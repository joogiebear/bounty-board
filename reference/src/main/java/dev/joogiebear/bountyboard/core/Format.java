package dev.joogiebear.bountyboard.core;

import java.time.Duration;
import java.util.Locale;

/** Number and duration formatting. See DESIGN.md, section "Config, messages and formatting". */
public final class Format {

    private Format() {}

    public static String amount(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    public static String duration(Duration duration) {
        long total = Math.max(0, duration.getSeconds());
        long days = total / 86_400;
        long hours = (total % 86_400) / 3_600;
        long minutes = (total % 3_600) / 60;
        long seconds = total % 60;
        if (total >= 86_400) {
            return two(days, "d", hours, "h");
        }
        if (total >= 3_600) {
            return two(hours, "h", minutes, "m");
        }
        if (total >= 60) {
            return two(minutes, "m", seconds, "s");
        }
        return seconds + "s";
    }

    private static String two(long major, String majorUnit, long minor, String minorUnit) {
        if (minor == 0) {
            return major + majorUnit;
        }
        return major + majorUnit + " " + minor + minorUnit;
    }
}
