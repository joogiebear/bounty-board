package dev.joogiebear.bountyboard.core;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/** Loads Settings from a config section. See DESIGN.md, section "Config, messages and formatting". */
public final class BountySettings {

    public record Loaded(Settings settings, List<String> warnings) {}

    private BountySettings() {}

    public static Loaded fromConfig(ConfigurationSection section) {
        Settings d = Settings.defaults();
        List<String> warnings = new ArrayList<>();
        long minAmount = read(section, "min-amount", d.minAmount(), 1, Long.MAX_VALUE, warnings);
        long maxAmount = read(section, "max-amount", d.maxAmount(), 1, Long.MAX_VALUE, warnings);
        int maxActive = (int) read(section, "max-active-per-player", d.maxActivePerPoster(), 1, Integer.MAX_VALUE, warnings);
        int defaultHours = (int) read(section, "default-hours", d.defaultHours(), 1, Integer.MAX_VALUE, warnings);
        int maxHours = (int) read(section, "max-hours", d.maxHours(), 1, Integer.MAX_VALUE, warnings);
        int tax = (int) read(section, "tax-percent", d.taxPercent(), 0, 100, warnings);
        int cooldown = (int) read(section, "claim-cooldown-minutes", d.claimCooldownMinutes(), 0, Integer.MAX_VALUE, warnings);
        int fullRefund = (int) read(section, "cancel-full-refund-minutes", d.cancelFullRefundMinutes(), 0, Integer.MAX_VALUE, warnings);
        int refundPercent = (int) read(section, "cancel-refund-percent", d.cancelRefundPercent(), 0, 100, warnings);
        int purgeDays = (int) read(section, "purge-resolved-after-days", d.purgeResolvedAfterDays(), 0, Integer.MAX_VALUE, warnings);
        if (minAmount > maxAmount) {
            minAmount = d.minAmount();
            maxAmount = d.maxAmount();
            warnings.add("min-amount is above max-amount, using defaults");
        }
        if (defaultHours > maxHours) {
            defaultHours = maxHours;
            warnings.add("default-hours is above max-hours, using " + maxHours);
        }
        return new Loaded(new Settings(minAmount, maxAmount, maxActive, defaultHours, maxHours, tax, cooldown,
                fullRefund, refundPercent, purgeDays), warnings);
    }

    private static long read(ConfigurationSection section, String key, long def, long min, long max, List<String> warnings) {
        Object value = section == null ? null : section.get(key);
        if (value == null) {
            return def;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            long v = ((Number) value).longValue();
            if (v >= min && v <= max) {
                return v;
            }
        }
        warnings.add(key + ": invalid value, using " + def);
        return def;
    }
}
