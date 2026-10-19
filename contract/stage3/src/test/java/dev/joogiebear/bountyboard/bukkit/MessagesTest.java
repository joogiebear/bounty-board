package dev.joogiebear.bountyboard.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessagesTest {

    /** The default messages from DESIGN.md, with their & colour codes as written there. */
    static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("prefix", "&6[Bounty] &r");
        DEFAULTS.put("usage", "Usage: /bounty <set|list|mine|cancel|balance|gui>");
        DEFAULTS.put("console-only-player", "This command can only be used by a player.");
        DEFAULTS.put("unknown-player", "&cUnknown player: {player}");
        DEFAULTS.put("invalid-number", "&cThat is not a valid number.");
        DEFAULTS.put("bounty-set", "&aBounty #{id} posted on {target} for {amount} coins.");
        DEFAULTS.put("bounty-posted", "&e{poster} posted a {amount} coin bounty on {target}!");
        DEFAULTS.put("self-target", "&cYou cannot place a bounty on yourself.");
        DEFAULTS.put("amount-too-low", "&cThe minimum bounty is {min} coins.");
        DEFAULTS.put("amount-too-high", "&cThe maximum bounty is {max} coins.");
        DEFAULTS.put("invalid-duration", "&cDuration must be between 1 and {max} hours.");
        DEFAULTS.put("too-many-active", "&cYou already have {max} active bounties.");
        DEFAULTS.put("insufficient-funds", "&cYou need {amount} coins, you have {balance}.");
        DEFAULTS.put("list-header", "&6Bounties (page {page}/{pages}, {total} total)");
        DEFAULTS.put("list-line", "&e#{id} &f{target} &7- &a{amount} coins &7(by {poster}, {remaining} left)");
        DEFAULTS.put("list-empty", "&7There are no active bounties.");
        DEFAULTS.put("mine-line", "&e#{id} &f{target} &7- &a{amount} coins &7({remaining} left)");
        DEFAULTS.put("mine-empty", "&7You have no active bounties.");
        DEFAULTS.put("cancel-ok", "&aBounty #{id} cancelled, {refunded} coins refunded.");
        DEFAULTS.put("cancel-not-found", "&cThere is no bounty #{id}.");
        DEFAULTS.put("cancel-not-active", "&cBounty #{id} is no longer active.");
        DEFAULTS.put("cancel-not-owner", "&cBounty #{id} is not yours.");
        DEFAULTS.put("balance", "&7Balance: &a{balance} coins");
        DEFAULTS.put("admin-given", "&aGave {amount} coins to {player}.");
        DEFAULTS.put("reloaded", "&aConfiguration reloaded.");
        DEFAULTS.put("no-permission", "&cYou do not have permission.");
        DEFAULTS.put("claim-broadcast", "&c{killer} claimed {amount} coins of bounties on {victim}!");
        DEFAULTS.put("claim-killer", "&aYou collected {payout} coins ({tax} tax).");
        DEFAULTS.put("claim-cooldown", "&7You recently claimed a bounty on {victim}; try again later.");
        DEFAULTS.put("notice-expired", "&7Your bounty #{id} on {other} expired; {amount} coins refunded.");
        DEFAULTS.put("notice-claimed", "&7Your bounty #{id} was claimed by {other}.");
        DEFAULTS.put("join-bounty", "&cThere is a {amount} coin bounty on your head!");
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    static String stripCodes(String s) {
        return s.replaceAll("&[0-9a-fk-or]", "");
    }

    Messages defaults() {
        return new Messages(new YamlConfiguration());
    }

    @Test
    void everyDefaultMessageIsPresentAndUnchanged() {
        Messages m = defaults();
        for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
            assertEquals(stripCodes(e.getValue()), plain(m.render(e.getKey(), Map.of())), e.getKey());
        }
    }

    @Test
    void defaultsKeepTheirColourCodes() {
        Messages m = defaults();
        for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
            if (e.getKey().equals("prefix")) {
                continue; // a trailing reset code has no text to apply to, so it does not survive a round trip
            }
            assertEquals(e.getValue(), LegacyComponentSerializer.legacyAmpersand().serialize(m.render(e.getKey(), Map.of())), e.getKey());
        }
    }

    @Test
    void replacesPlaceholders() {
        Component c = defaults().render("bounty-set", Map.of("id", "3", "target", "Steve", "amount", "1,000"));
        assertEquals("Bounty #3 posted on Steve for 1,000 coins.", plain(c));
    }

    @Test
    void leavesPlaceholdersWithoutAValueAsTheyAre() {
        Component c = defaults().render("bounty-set", Map.of("id", "3"));
        assertEquals("Bounty #3 posted on {target} for {amount} coins.", plain(c));
    }

    @Test
    void ignoresPlaceholdersTheMessageDoesNotUse() {
        Component c = defaults().render("reloaded", Map.of("unused", "x"));
        assertEquals("Configuration reloaded.", plain(c));
    }

    @Test
    void replacesARepeatedPlaceholderEverywhere() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reloaded", "{x} and {x} and {y}");
        Messages m = new Messages(yaml);
        assertEquals("1 and 1 and 2", plain(m.render("reloaded", Map.of("x", "1", "y", "2"))));
    }

    @Test
    void configValuesOverrideTheDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reloaded", "&bDone {n}");
        yaml.set("prefix", "&8>> ");
        Messages m = new Messages(yaml);
        assertEquals("Done 5", plain(m.render("reloaded", Map.of("n", "5"))));
        assertEquals("&bDone 5", LegacyComponentSerializer.legacyAmpersand().serialize(m.render("reloaded", Map.of("n", "5"))));
        assertEquals(">> ", plain(m.render("prefix", Map.of())));
        // untouched keys still use their defaults
        assertEquals("You do not have permission.", plain(m.render("no-permission", Map.of())));
    }

    @Test
    void anUnknownKeyRendersAsMissing() {
        assertEquals("<missing:nope>", plain(defaults().render("nope", Map.of())));
    }
}
