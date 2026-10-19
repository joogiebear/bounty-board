package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.BountyService;
import dev.joogiebear.bountyboard.core.Economy;
import java.time.Clock;
import org.bukkit.plugin.java.JavaPlugin;

/** The plugin entry point. See DESIGN.md, sections "Listeners and tasks" and "Wiring". */
public class BountyBoardPlugin extends JavaPlugin {

    private static Clock clockForTests;

    /** Test seam: set before the plugin loads; null restores the system clock. */
    public static void setClockForTests(Clock clock) {
        clockForTests = clock;
    }

    static Clock clock() {
        return clockForTests != null ? clockForTests : Clock.systemUTC();
    }

    public BountyService service() {
        throw new UnsupportedOperationException("TODO");
    }

    public Economy economy() {
        throw new UnsupportedOperationException("TODO");
    }
}
