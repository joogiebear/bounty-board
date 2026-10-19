package dev.joogiebear.bountyboard.bukkit;

import static dev.joogiebear.bountyboard.core.HeldSupport.T0;

import dev.joogiebear.bountyboard.core.HeldSupport.MutableClock;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Boots a mock server with the plugin and a controllable clock. */
abstract class HeldBase {
    MutableClock clock;
    ServerMock server;
    BountyBoardPlugin plugin;

    @BeforeEach
    void boot() {
        clock = new MutableClock(T0);
        BountyBoardPlugin.setClockForTests(clock);
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BountyBoardPlugin.class);
    }

    @AfterEach
    void shutdown() {
        MockBukkit.unmock();
        BountyBoardPlugin.setClockForTests(null);
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Every pending message of the player as plain text. */
    static List<String> drain(PlayerMock p) {
        List<String> out = new ArrayList<>();
        Component c;
        while ((c = p.nextComponentMessage()) != null) out.add(plain(c));
        return out;
    }

    /** Pending messages that came from the plugin (they start with the default prefix). */
    static List<String> bounty(PlayerMock p) {
        return drain(p).stream().filter(s -> s.startsWith("[Bounty] ")).toList();
    }

    PlayerMock player(String name) {
        return server.addPlayer(name);
    }

    /** Runs a command line without the leading slash and returns the plugin messages it produced. */
    List<String> run(PlayerMock p, String line) {
        drain(p);
        p.performCommand(line);
        return bounty(p);
    }

    void kill(PlayerMock killer, PlayerMock victim) {
        victim.setHealth(victim.getMaxHealth());
        victim.setKiller(killer);
        victim.setHealth(0);
    }
}
