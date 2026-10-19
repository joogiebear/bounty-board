package dev.joogiebear.bountyboard.bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

/** Configurable messages with defaults. See DESIGN.md, sections "Config, messages and formatting" and "Commands and messages". */
public class Messages {

    private static final Map<String, String> DEFAULTS = new HashMap<>();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)\\}");

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

    private final ConfigurationSection section;

    public Messages(ConfigurationSection section) {
        this.section = section;
    }

    public Component render(String key, Map<String, String> placeholders) {
        String text = section == null ? null : section.getString(key);
        if (text == null) {
            text = DEFAULTS.get(key);
        }
        if (text == null) {
            return Component.text("<missing:" + key + ">");
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = placeholders.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(sb);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(sb.toString());
    }

    /** The prefix followed by the rendered message. */
    public Component prefixed(String key, Map<String, String> placeholders) {
        return Component.text().append(render("prefix", Map.of())).append(render(key, placeholders)).build();
    }
}
