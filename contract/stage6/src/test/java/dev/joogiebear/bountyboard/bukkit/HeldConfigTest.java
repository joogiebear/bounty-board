package dev.joogiebear.bountyboard.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HeldConfigTest extends HeldBase {

    private static final String P = "[Bounty] ";

    private File configFile() {
        return new File(plugin.getDataFolder(), "config.yml");
    }

    private void edit(java.util.function.Consumer<YamlConfiguration> change) throws IOException {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile());
        change.accept(cfg);
        cfg.save(configFile());
    }

    @Test
    void firstEnableWritesTheDefaultConfigIntoTheDataFolder() {
        assertTrue(configFile().isFile(), "config.yml must exist after the first enable");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile());
        assertEquals(10, cfg.getInt("min-amount"));
        assertEquals(1_000_000L, cfg.getLong("max-amount"));
        assertEquals(3, cfg.getInt("max-active-per-player"));
        assertEquals(24, cfg.getInt("default-hours"));
        assertEquals(72, cfg.getInt("max-hours"));
        assertEquals(5, cfg.getInt("tax-percent"));
        assertEquals(10, cfg.getInt("claim-cooldown-minutes"));
        assertEquals(5, cfg.getInt("cancel-full-refund-minutes"));
        assertEquals(80, cfg.getInt("cancel-refund-percent"));
        assertEquals(7, cfg.getInt("purge-resolved-after-days"));
        assertEquals(1000, cfg.getInt("starting-balance"));
        assertTrue(cfg.contains("messages"));
    }

    @Test
    void reloadAppliesAChangedLimitImmediately() throws IOException {
        PlayerMock alice = player("Alice");
        player("Bob");
        PlayerMock root = player("Root");
        root.setOp(true);
        assertEquals(P + "Bounty #1 posted on Bob for 400 coins.", run(alice, "bounty set Bob 400").get(0));
        edit(c -> c.set("max-amount", 300));
        assertEquals(List.of(P + "Configuration reloaded."), run(root, "bounty admin reload"));
        assertEquals(List.of(P + "The maximum bounty is 300 coins."), run(alice, "bounty set Bob 400"));
        assertEquals(P + "Bounty #2 posted on Bob for 300 coins.", run(alice, "bounty set Bob 300").get(0));
    }

    @Test
    void reloadKeepsExistingBountiesAndEconomy() throws IOException {
        PlayerMock alice = player("Alice");
        player("Bob");
        PlayerMock root = player("Root");
        root.setOp(true);
        run(alice, "bounty set Bob 100");
        edit(c -> c.set("tax-percent", 50));
        run(root, "bounty admin reload");
        assertEquals(List.of(P + "#1 Bob - 100 coins (1d left)"), run(alice, "bounty mine"));
        assertEquals(900, plugin.economy().balance(alice.getUniqueId()));
        PlayerMock carol = player("Carol");
        PlayerMock bob = (PlayerMock) server.getPlayerExact("Bob");
        drain(carol);
        kill(carol, bob);
        assertEquals(1050, plugin.economy().balance(carol.getUniqueId()), "the reloaded 50 percent tax applies to the old bounty");
    }

    @Test
    void reloadAppliesChangedMessagesAndPrefix() throws IOException {
        PlayerMock alice = player("Alice");
        PlayerMock root = player("Root");
        root.setOp(true);
        edit(c -> {
            c.set("messages.balance", "Coins: {balance}");
            c.set("messages.prefix", "<<B>> ");
        });
        run(root, "bounty admin reload");
        alice.performCommand("bounty balance");
        List<String> msgs = drain(alice);
        assertEquals(1, msgs.size());
        assertTrue(msgs.get(0).matches("<<B>> Coins: 1,?000"), msgs.get(0));
    }

    @Test
    void reloadWithBadValuesFallsBackToDefaultsAndStillWorks() throws IOException {
        PlayerMock alice = player("Alice");
        player("Bob");
        PlayerMock root = player("Root");
        root.setOp(true);
        edit(c -> {
            c.set("min-amount", 500);
            c.set("max-amount", 100);
            c.set("tax-percent", "lots");
        });
        assertEquals(List.of(P + "Configuration reloaded."), run(root, "bounty admin reload"));
        assertEquals(P + "Bounty #1 posted on Bob for 10 coins.", run(alice, "bounty set Bob 10").get(0), "min-amount fell back to 10");
        assertEquals(P + "Bounty #2 posted on Bob for 600 coins.", run(alice, "bounty set Bob 600").get(0), "max-amount fell back to 1000000");
    }

    @Test
    void reloadDoesNotTouchTheClockOrLoseNotices() throws IOException {
        PlayerMock alice = player("Alice");
        player("Bob");
        PlayerMock root = player("Root");
        root.setOp(true);
        run(alice, "bounty set Bob 100 1");
        clock.advance(java.time.Duration.ofHours(1));
        edit(c -> c.set("default-hours", 10));
        run(root, "bounty admin reload");
        assertEquals(1, plugin.service().expireDue(), "the injected clock still drives the reloaded service");
        assertEquals(1000, plugin.economy().balance(alice.getUniqueId()));
    }

    @Test
    void dataIsWrittenToTheDataFolder() {
        PlayerMock alice = player("Alice");
        player("Bob");
        run(alice, "bounty set Bob 100");
        File data = new File(plugin.getDataFolder(), "data.yml");
        File balances = new File(plugin.getDataFolder(), "balances.yml");
        assertTrue(data.isFile(), "data.yml");
        assertTrue(balances.isFile(), "balances.yml");
        assertTrue(YamlConfiguration.loadConfiguration(balances).saveToString().contains(alice.getUniqueId().toString()));
    }

    // ---------------------------------------------------------------- built-in economy

    @Test
    void builtinEconomyWithdrawAndDepositRules() {
        UUID u = UUID.randomUUID();
        UUID v = UUID.randomUUID();
        var eco = plugin.economy();
        assertEquals(1000, eco.balance(u), "a new player starts with starting-balance");
        assertFalse(eco.withdraw(u, 1001));
        assertEquals(1000, eco.balance(u));
        assertTrue(eco.withdraw(u, 1000));
        assertEquals(0, eco.balance(u));
        assertFalse(eco.withdraw(u, 1));
        eco.deposit(u, 5_000_000_000L);
        assertEquals(5_000_000_000L, eco.balance(u));
        assertEquals(1000, eco.balance(v), "players are independent");
    }

    // ---------------------------------------------------------------- tab completion

    private List<String> tab(PlayerMock p, String... args) {
        Command cmd = plugin.getCommand("bounty");
        List<String> r = cmd.tabComplete(p, "bounty", args);
        return r == null ? List.of() : r.stream().sorted().toList();
    }

    @Test
    void firstArgumentOffersOnlyPermittedSubcommandsFilteredByPrefix() {
        PlayerMock alice = player("Alice");
        PlayerMock root = player("Root");
        root.setOp(true);
        assertEquals(Set.of("set", "list", "mine", "cancel", "balance", "gui"), Set.copyOf(tab(alice, "")));
        assertEquals(Set.of("set", "list", "mine", "cancel", "balance", "gui", "admin"), Set.copyOf(tab(root, "")));
        assertEquals(List.of("set"), tab(alice, "s"));
        assertEquals(List.of("set"), tab(alice, "S"));
        assertEquals(List.of("admin"), tab(root, "ad"));
        assertTrue(tab(alice, "ad").isEmpty());
        assertTrue(tab(alice, "zzz").isEmpty());
    }

    @Test
    void setCompletesPlayersThenAmountsThenHours() {
        PlayerMock alice = player("Alice");
        player("Bob");
        player("Bobby");
        assertEquals(List.of("Bob", "Bobby"), tab(alice, "set", ""));
        assertEquals(List.of("Bob", "Bobby"), tab(alice, "set", "bo"));
        assertEquals(List.of("Bobby"), tab(alice, "set", "Bobb"));
        assertEquals(List.of("100", "1000"), tab(alice, "set", "Bob", "1"));
        assertEquals(List.of("100", "1000", "500"), tab(alice, "set", "Bob", ""));
        assertEquals(List.of("1", "24", "6", "72").stream().sorted().toList(), tab(alice, "set", "Bob", "100", ""));
        assertEquals(List.of("24"), tab(alice, "set", "Bob", "100", "2"));
    }

    @Test
    void cancelAndAdminCompletions() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        PlayerMock root = player("Root");
        root.setOp(true);
        run(alice, "bounty set Bob 100");
        run(alice, "bounty set Bob 100");
        run(bob, "bounty set Alice 100");
        assertEquals(List.of("1", "2"), tab(alice, "cancel", ""));
        assertEquals(List.of("3"), tab(bob, "cancel", ""));
        assertEquals(List.of("2"), tab(alice, "cancel", "2"));
        assertEquals(List.of("give", "reload"), tab(root, "admin", ""));
        assertEquals(List.of("reload"), tab(root, "admin", "R"));
        assertEquals(List.of("Alice", "Bob", "Root"), tab(root, "admin", "give", ""));
        assertEquals(List.of("Alice"), tab(root, "admin", "give", "al"));
    }
}
