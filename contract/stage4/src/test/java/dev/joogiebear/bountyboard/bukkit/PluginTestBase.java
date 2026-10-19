package dev.joogiebear.bountyboard.bukkit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Loads the plugin on a mock server with a controllable clock and three players. */
abstract class PluginTestBase {

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    static final class TestClock extends Clock {
        private Instant now = T0;

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    ServerMock server;
    BountyBoardPlugin plugin;
    TestClock clock;
    PlayerMock alex;
    PlayerMock steve;
    PlayerMock kim;

    @BeforeEach
    void loadPlugin() {
        clock = new TestClock();
        BountyBoardPlugin.setClockForTests(clock);
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BountyBoardPlugin.class);
        alex = server.addPlayer("Alex");
        steve = server.addPlayer("Steve");
        kim = server.addPlayer("Kim");
        drain(alex);
        drain(steve);
        drain(kim);
    }

    @AfterEach
    void unloadPlugin() {
        MockBukkit.unmock();
        BountyBoardPlugin.setClockForTests(null);
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** The next message the player received as plain text, or null if there is none. */
    static String next(PlayerMock p) {
        Component c = p.nextComponentMessage();
        return c == null ? null : plain(c);
    }

    /** Every pending message, as plain text, oldest first. */
    static List<String> drain(PlayerMock p) {
        List<String> out = new ArrayList<>();
        String m;
        while ((m = next(p)) != null) {
            out.add(m);
        }
        return out;
    }

    /** Runs a command as the player and returns the messages the player received. */
    List<String> run(PlayerMock player, String command) {
        drain(player);
        player.performCommand(command);
        return drain(player);
    }

    /** Runs a command as the player, ignoring what everybody else received. */
    void cmd(PlayerMock player, String command) {
        player.performCommand(command);
        drain(alex);
        drain(steve);
        drain(kim);
    }

    /** Fires a death of victim killed by killer (null killer: died of something else). */
    void kill(PlayerMock victim, PlayerMock killer) {
        victim.setHealth(victim.getMaxHealth());
        if (killer != null) {
            victim.setKiller(killer);
        }
        victim.setHealth(0);
    }

    /** Creates a bounty directly through the service, with made-up poster and target UUIDs. */
    void seed(int count, long amount) {
        for (int i = 0; i < count; i++) {
            UUID poster = UUID.nameUUIDFromBytes(("poster" + i).getBytes());
            UUID target = UUID.nameUUIDFromBytes(("target" + i).getBytes());
            plugin.economy().deposit(poster, 1_000_000);
            plugin.service().create(poster, "Poster" + i, target, "Target" + i, amount + i, null);
        }
    }

    void tick(int ticks) {
        server.getScheduler().performTicks(ticks);
    }
}
