package dev.joogiebear.bountyboard.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.MockBukkit;

class HeldSettingsFormatTest {

    @BeforeEach
    void up() { MockBukkit.mock(); }

    @AfterEach
    void down() { MockBukkit.unmock(); }

    private static BountySettings.Loaded load(String yaml) {
        YamlConfiguration c = new YamlConfiguration();
        try {
            c.loadFromString(yaml);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return BountySettings.fromConfig(c);
    }

    // ---------------------------------------------------------------- settings

    @Test
    void emptySectionGivesDefaultsWithoutWarnings() {
        BountySettings.Loaded l = BountySettings.fromConfig(new YamlConfiguration());
        assertEquals(Settings.defaults(), l.settings());
        assertTrue(l.warnings().isEmpty());
        assertEquals(new Settings(10, 1_000_000, 3, 24, 72, 5, 10, 5, 80, 7), l.settings());
    }

    @Test
    void everyKeyIsReadFromTheSection() {
        BountySettings.Loaded l = load("""
                min-amount: 20
                max-amount: 5000000000
                max-active-per-player: 9
                default-hours: 12
                max-hours: 48
                tax-percent: 0
                claim-cooldown-minutes: 0
                cancel-full-refund-minutes: 0
                cancel-refund-percent: 100
                purge-resolved-after-days: 0
                """);
        assertEquals(new Settings(20, 5_000_000_000L, 9, 12, 48, 0, 0, 0, 100, 0), l.settings());
        assertTrue(l.warnings().isEmpty());
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("min-amount", "0", "10"),
                Arguments.of("min-amount", "-5", "10"),
                Arguments.of("min-amount", "lots", "10"),
                Arguments.of("max-amount", "0", "1000000"),
                Arguments.of("max-amount", "true", "1000000"),
                Arguments.of("max-active-per-player", "0", "3"),
                Arguments.of("max-active-per-player", "[1, 2]", "3"),
                Arguments.of("default-hours", "0", "24"),
                Arguments.of("default-hours", "abc", "24"),
                Arguments.of("max-hours", "0", "72"),
                Arguments.of("tax-percent", "101", "5"),
                Arguments.of("tax-percent", "-1", "5"),
                Arguments.of("tax-percent", "abc", "5"),
                Arguments.of("tax-percent", "4294967297", "5"),
                Arguments.of("claim-cooldown-minutes", "-1", "10"),
                Arguments.of("cancel-full-refund-minutes", "-1", "5"),
                Arguments.of("cancel-refund-percent", "101", "80"),
                Arguments.of("cancel-refund-percent", "-1", "80"),
                Arguments.of("purge-resolved-after-days", "-1", "7"),
                Arguments.of("purge-resolved-after-days", "never", "7"));
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void invalidValueFallsBackToDefaultWithExactlyOneWarning(String key, String value, String def) {
        BountySettings.Loaded l = load(key + ": " + value + "\n");
        assertEquals(Settings.defaults(), l.settings());
        assertEquals(List.of(key + ": invalid value, using " + def), l.warnings());
    }

    @Test
    void rangeEdgesAreAccepted() {
        BountySettings.Loaded l = load("tax-percent: 100\ncancel-refund-percent: 0\nclaim-cooldown-minutes: 0\npurge-resolved-after-days: 0\nmin-amount: 1\nmax-hours: 1\ndefault-hours: 1\n");
        assertEquals(new Settings(1, 1_000_000, 3, 1, 1, 100, 0, 5, 0, 0), l.settings());
        assertTrue(l.warnings().isEmpty());
    }

    @Test
    void severalBadKeysGiveOneWarningEach() {
        BountySettings.Loaded l = load("tax-percent: 500\nmax-hours: 0\nmin-amount: x\ncancel-refund-percent: 20\n");
        assertEquals(3, l.warnings().size());
        assertTrue(l.warnings().contains("tax-percent: invalid value, using 5"));
        assertTrue(l.warnings().contains("max-hours: invalid value, using 72"));
        assertTrue(l.warnings().contains("min-amount: invalid value, using 10"));
        assertEquals(20, l.settings().cancelRefundPercent());
    }

    @Test
    void minAboveMaxResetsBothToDefaults() {
        BountySettings.Loaded l = load("min-amount: 500\nmax-amount: 100\ntax-percent: 20\n");
        assertEquals(10, l.settings().minAmount());
        assertEquals(1_000_000, l.settings().maxAmount());
        assertEquals(20, l.settings().taxPercent());
        assertEquals(List.of("min-amount is above max-amount, using defaults"), l.warnings());
    }

    @Test
    void minEqualToMaxIsFine() {
        BountySettings.Loaded l = load("min-amount: 50\nmax-amount: 50\n");
        assertEquals(50, l.settings().minAmount());
        assertEquals(50, l.settings().maxAmount());
        assertTrue(l.warnings().isEmpty());
    }

    @Test
    void defaultHoursAboveMaxHoursFallsBackToMaxHours() {
        BountySettings.Loaded l = load("default-hours: 100\nmax-hours: 48\n");
        assertEquals(48, l.settings().defaultHours());
        assertEquals(48, l.settings().maxHours());
        assertEquals(List.of("default-hours is above max-hours, using 48"), l.warnings());
    }

    @Test
    void defaultHoursEqualToMaxHoursIsFineAndLoweredMaxAlsoTriggersFallback() {
        BountySettings.Loaded same = load("default-hours: 10\nmax-hours: 10\n");
        assertEquals(10, same.settings().defaultHours());
        assertTrue(same.warnings().isEmpty());
        BountySettings.Loaded lowered = load("max-hours: 12\n"); // default-hours stays 24, which is above 12
        assertEquals(12, lowered.settings().defaultHours());
        assertEquals(List.of("default-hours is above max-hours, using 12"), lowered.warnings());
    }

    // ---------------------------------------------------------------- Format.amount

    @ParameterizedTest
    @MethodSource("amounts")
    void amountUsesThousandsSeparators(long value, String expected) {
        assertEquals(expected, Format.amount(value));
    }

    static Stream<Arguments> amounts() {
        return Stream.of(
                Arguments.of(0L, "0"), Arguments.of(7L, "7"), Arguments.of(999L, "999"), Arguments.of(1_000L, "1,000"),
                Arguments.of(12_345L, "12,345"), Arguments.of(100_000L, "100,000"), Arguments.of(1_234_567L, "1,234,567"),
                Arguments.of(4_294_967_297L, "4,294,967,297"), Arguments.of(Long.MAX_VALUE, "9,223,372,036,854,775,807"));
    }

    // ---------------------------------------------------------------- Format.duration

    @ParameterizedTest
    @MethodSource("durations")
    void durationFormats(Duration d, String expected) {
        assertEquals(expected, Format.duration(d));
    }

    static Stream<Arguments> durations() {
        return Stream.of(
                Arguments.of(Duration.ZERO, "0s"),
                Arguments.of(Duration.ofMillis(999), "0s"),
                Arguments.of(Duration.ofMillis(1999), "1s"),
                Arguments.of(Duration.ofSeconds(1), "1s"),
                Arguments.of(Duration.ofSeconds(59), "59s"),
                Arguments.of(Duration.ofMillis(59_999), "59s"),
                Arguments.of(Duration.ofSeconds(60), "1m"),
                Arguments.of(Duration.ofSeconds(61), "1m 1s"),
                Arguments.of(Duration.ofMinutes(5), "5m"),
                Arguments.of(Duration.ofSeconds(3599), "59m 59s"),
                Arguments.of(Duration.ofHours(1), "1h"),
                Arguments.of(Duration.ofSeconds(3601), "1h"),
                Arguments.of(Duration.ofSeconds(3660), "1h 1m"),
                Arguments.of(Duration.ofSeconds(3661), "1h 1m"),
                Arguments.of(Duration.ofSeconds(86_399), "23h 59m"),
                Arguments.of(Duration.ofDays(1), "1d"),
                Arguments.of(Duration.ofSeconds(86_400 + 59 * 60), "1d"),
                Arguments.of(Duration.ofHours(25), "1d 1h"),
                Arguments.of(Duration.ofHours(51), "2d 3h"),
                Arguments.of(Duration.ofDays(365), "365d"),
                Arguments.of(Duration.ofSeconds(-5), "0s"),
                Arguments.of(Duration.ofDays(-3), "0s"),
                Arguments.of(Duration.ofMillis(-1), "0s"));
    }
}
