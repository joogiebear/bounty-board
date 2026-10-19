package dev.joogiebear.bountyboard.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FormatTest {

    @Test
    void formatsAmountsWithThousandsSeparators() {
        assertEquals("0", Format.amount(0));
        assertEquals("7", Format.amount(7));
        assertEquals("999", Format.amount(999));
        assertEquals("1,000", Format.amount(1000));
        assertEquals("12,345", Format.amount(12_345));
        assertEquals("1,234,567", Format.amount(1_234_567));
        assertEquals("-1,234", Format.amount(-1234));
        assertEquals("9,223,372,036,854,775,807", Format.amount(Long.MAX_VALUE));
    }

    @Test
    void formatsShortDurations() {
        assertEquals("0s", Format.duration(Duration.ZERO));
        assertEquals("1s", Format.duration(Duration.ofSeconds(1)));
        assertEquals("59s", Format.duration(Duration.ofSeconds(59)));
        assertEquals("59s", Format.duration(Duration.ofMillis(59_999)));
        assertEquals("0s", Format.duration(Duration.ofMillis(999)));
    }

    @Test
    void formatsMinutesAndSeconds() {
        assertEquals("1m", Format.duration(Duration.ofSeconds(60)));
        assertEquals("1m 5s", Format.duration(Duration.ofSeconds(65)));
        assertEquals("5m", Format.duration(Duration.ofMinutes(5)));
        assertEquals("59m 59s", Format.duration(Duration.ofSeconds(3599)));
    }

    @Test
    void formatsHoursAndMinutesAndDropsSeconds() {
        assertEquals("1h", Format.duration(Duration.ofHours(1)));
        assertEquals("1h", Format.duration(Duration.ofSeconds(3601)));
        assertEquals("1h 1m", Format.duration(Duration.ofSeconds(3660)));
        assertEquals("2h 5m", Format.duration(Duration.ofMinutes(125)));
        assertEquals("23h 59m", Format.duration(Duration.ofSeconds(86_399)));
    }

    @Test
    void formatsDaysAndHoursAndDropsMinutes() {
        assertEquals("1d", Format.duration(Duration.ofDays(1)));
        assertEquals("1d", Format.duration(Duration.ofHours(24).plusMinutes(59)));
        assertEquals("1d 1h", Format.duration(Duration.ofHours(25).plusMinutes(30)));
        assertEquals("2d 3h", Format.duration(Duration.ofHours(51)));
        assertEquals("3d", Format.duration(Duration.ofHours(72)));
    }

    @Test
    void negativeDurationsCountAsZero() {
        assertEquals("0s", Format.duration(Duration.ofSeconds(-5)));
        assertEquals("0s", Format.duration(Duration.ofDays(-2)));
    }
}
