package dev.joogiebear.bountyboard.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BountySettingsTest {

    static BountySettings.Loaded load(Object... keyValues) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int i = 0; i < keyValues.length; i += 2) {
            yaml.set((String) keyValues[i], keyValues[i + 1]);
        }
        return BountySettings.fromConfig(yaml);
    }

    @Test
    void anEmptySectionGivesTheDefaults() {
        BountySettings.Loaded l = load();
        assertEquals(Settings.defaults(), l.settings());
        assertTrue(l.warnings().isEmpty());
    }

    @Test
    void readsEveryKey() {
        BountySettings.Loaded l = load("min-amount", 20, "max-amount", 5_000_000_000L, "max-active-per-player", 7, "default-hours", 12,
                "max-hours", 48, "tax-percent", 10, "claim-cooldown-minutes", 3, "cancel-full-refund-minutes", 2,
                "cancel-refund-percent", 50, "purge-resolved-after-days", 30);
        assertEquals(new Settings(20, 5_000_000_000L, 7, 12, 48, 10, 3, 2, 50, 30), l.settings());
        assertTrue(l.warnings().isEmpty());
    }

    @Test
    void aWrongTypeFallsBackWithAWarning() {
        BountySettings.Loaded l = load("min-amount", "abc", "max-active-per-player", true);
        assertEquals(10, l.settings().minAmount());
        assertEquals(3, l.settings().maxActivePerPoster());
        assertEquals(List.of("min-amount: invalid value, using 10", "max-active-per-player: invalid value, using 3"), l.warnings());
    }

    @Test
    void outOfRangeValuesFallBack() {
        BountySettings.Loaded l = load("max-amount", 0, "default-hours", 0, "max-hours", -5, "tax-percent", 101, "cancel-refund-percent", -1,
                "claim-cooldown-minutes", -1, "purge-resolved-after-days", -2, "cancel-full-refund-minutes", -3, "max-active-per-player", 0, "min-amount", 0);
        Settings d = Settings.defaults();
        assertEquals(d, l.settings());
        assertEquals(10, l.warnings().size());
        assertTrue(l.warnings().contains("tax-percent: invalid value, using 5"));
        assertTrue(l.warnings().contains("cancel-refund-percent: invalid value, using 80"));
        assertTrue(l.warnings().contains("max-hours: invalid value, using 72"));
    }

    @Test
    void zeroIsValidForPercentsMinutesAndDays() {
        BountySettings.Loaded l = load("tax-percent", 0, "cancel-refund-percent", 0, "claim-cooldown-minutes", 0,
                "cancel-full-refund-minutes", 0, "purge-resolved-after-days", 0);
        assertEquals(new Settings(10, 1_000_000, 3, 24, 72, 0, 0, 0, 0, 0), l.settings());
        assertTrue(l.warnings().isEmpty());
    }

    @Test
    void oneHundredPercentIsValid() {
        BountySettings.Loaded l = load("tax-percent", 100, "cancel-refund-percent", 100);
        assertEquals(100, l.settings().taxPercent());
        assertEquals(100, l.settings().cancelRefundPercent());
        assertTrue(l.warnings().isEmpty());
    }

    @Test
    void warningsFollowTheKeyOrder() {
        BountySettings.Loaded l = load("purge-resolved-after-days", "x", "tax-percent", "x", "min-amount", "x");
        assertEquals(List.of("min-amount: invalid value, using 10", "tax-percent: invalid value, using 5",
                "purge-resolved-after-days: invalid value, using 7"), l.warnings());
    }

    @Test
    void aMinimumAboveTheMaximumResetsBoth() {
        BountySettings.Loaded l = load("min-amount", 500, "max-amount", 100);
        assertEquals(10, l.settings().minAmount());
        assertEquals(1_000_000, l.settings().maxAmount());
        assertEquals(List.of("min-amount is above max-amount, using defaults"), l.warnings());
    }

    @Test
    void aDefaultDurationAboveTheMaximumUsesTheMaximum() {
        BountySettings.Loaded l = load("default-hours", 100, "max-hours", 48);
        assertEquals(48, l.settings().defaultHours());
        assertEquals(48, l.settings().maxHours());
        assertEquals(List.of("default-hours is above max-hours, using 48"), l.warnings());
    }

    @Test
    void crossChecksComeAfterTheKeyWarnings() {
        BountySettings.Loaded l = load("min-amount", 500, "max-amount", 100, "tax-percent", 500);
        assertEquals(List.of("tax-percent: invalid value, using 5", "min-amount is above max-amount, using defaults"), l.warnings());
    }

    @Test
    void equalMinimumAndMaximumAreFine() {
        BountySettings.Loaded l = load("min-amount", 100, "max-amount", 100);
        assertEquals(100, l.settings().minAmount());
        assertEquals(100, l.settings().maxAmount());
        assertTrue(l.warnings().isEmpty());
    }
}
