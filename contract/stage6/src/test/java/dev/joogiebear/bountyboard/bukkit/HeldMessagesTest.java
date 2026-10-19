package dev.joogiebear.bountyboard.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.MockBukkit;

class HeldMessagesTest {

    @BeforeEach
    void up() { MockBukkit.mock(); }

    @AfterEach
    void down() { MockBukkit.unmock(); }

    private static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }

    private static String amp(Component c) { return LegacyComponentSerializer.legacyAmpersand().serialize(c); }

    private static Messages messages(String yaml) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return new Messages(cfg);
    }

    static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("prefix", "&6[Bounty] &r");
        m.put("usage", "Usage: /bounty <set|list|mine|cancel|balance|gui>");
        m.put("console-only-player", "This command can only be used by a player.");
        m.put("unknown-player", "&cUnknown player: {player}");
        m.put("invalid-number", "&cThat is not a valid number.");
        m.put("bounty-set", "&aBounty #{id} posted on {target} for {amount} coins.");
        m.put("bounty-posted", "&e{poster} posted a {amount} coin bounty on {target}!");
        m.put("self-target", "&cYou cannot place a bounty on yourself.");
        m.put("amount-too-low", "&cThe minimum bounty is {min} coins.");
        m.put("amount-too-high", "&cThe maximum bounty is {max} coins.");
        m.put("invalid-duration", "&cDuration must be between 1 and {max} hours.");
        m.put("too-many-active", "&cYou already have {max} active bounties.");
        m.put("insufficient-funds", "&cYou need {amount} coins, you have {balance}.");
        m.put("list-header", "&6Bounties (page {page}/{pages}, {total} total)");
        m.put("list-line", "&e#{id} &f{target} &7- &a{amount} coins &7(by {poster}, {remaining} left)");
        m.put("list-empty", "&7There are no active bounties.");
        m.put("mine-line", "&e#{id} &f{target} &7- &a{amount} coins &7({remaining} left)");
        m.put("mine-empty", "&7You have no active bounties.");
        m.put("cancel-ok", "&aBounty #{id} cancelled, {refunded} coins refunded.");
        m.put("cancel-not-found", "&cThere is no bounty #{id}.");
        m.put("cancel-not-active", "&cBounty #{id} is no longer active.");
        m.put("cancel-not-owner", "&cBounty #{id} is not yours.");
        m.put("balance", "&7Balance: &a{balance} coins");
        m.put("admin-given", "&aGave {amount} coins to {player}.");
        m.put("reloaded", "&aConfiguration reloaded.");
        m.put("no-permission", "&cYou do not have permission.");
        m.put("claim-broadcast", "&c{killer} claimed {amount} coins of bounties on {victim}!");
        m.put("claim-killer", "&aYou collected {payout} coins ({tax} tax).");
        m.put("claim-cooldown", "&7You recently claimed a bounty on {victim}; try again later.");
        m.put("notice-expired", "&7Your bounty #{id} on {other} expired; {amount} coins refunded.");
        m.put("notice-claimed", "&7Your bounty #{id} was claimed by {other}.");
        m.put("join-bounty", "&cThere is a {amount} coin bounty on your head!");
        return m;
    }

    static java.util.stream.Stream<Map.Entry<String, String>> defaultEntries() { return defaults().entrySet().stream(); }

    @ParameterizedTest
    @MethodSource("defaultEntries")
    void everyDefaultTextIsUsedWhenTheConfigHasNoMessage(Map.Entry<String, String> e) {
        Messages m = new Messages(new YamlConfiguration());
        Component c = m.render(e.getKey(), Map.of());
        assertEquals(e.getValue().replaceAll("&[0-9a-fk-or]", ""), plain(c), e.getKey());
    }

    @Test
    void placeholdersAreReplacedEverywhereTheyAppear() {
        Messages m = messages("bye: \"{a}-{a}-{b}\"\n");
        assertEquals("1-1-2", plain(m.render("bye", Map.of("a", "1", "b", "2"))));
    }

    @Test
    void placeholderWithoutAValueIsLeftAsIs() {
        Messages m = new Messages(new YamlConfiguration());
        assertEquals("Bounty #7 posted on {target} for {amount} coins.", plain(m.render("bounty-set", Map.of("id", "7"))));
        assertEquals("Gave {amount} coins to {player}.", plain(m.render("admin-given", Map.of())));
    }

    @Test
    void unknownKeyRendersAsMissingMarker() {
        Messages m = new Messages(new YamlConfiguration());
        assertEquals("<missing:no-such-key>", plain(m.render("no-such-key", Map.of())));
    }

    @Test
    void configOverrideWinsAndOthersKeepTheirDefault() {
        Messages m = messages("balance: \"&bCoins: {balance}\"\n");
        assertEquals("Coins: 5", plain(m.render("balance", Map.of("balance", "5"))));
        assertTrue(amp(m.render("balance", Map.of("balance", "5"))).contains("&b"));
        assertEquals("There are no active bounties.", plain(m.render("list-empty", Map.of())));
    }

    @Test
    void colourCodesInPlaceholderValuesAreConvertedBecauseReplacementComesFirst() {
        Messages m = messages("x: \"Hi {name}\"\n");
        Component c = m.render("x", Map.of("name", "&cRed"));
        assertEquals("Hi Red", plain(c));
        assertTrue(amp(c).contains("&c"), amp(c));
    }

    @Test
    void ampersandCodesProduceStyledComponents() {
        Messages m = messages("x: \"&lBold &r&aGreen\"\n");
        Component c = m.render("x", Map.of());
        assertEquals("Bold Green", plain(c));
        assertTrue(amp(c).contains("&l") && amp(c).contains("&a"), amp(c));
    }
}
