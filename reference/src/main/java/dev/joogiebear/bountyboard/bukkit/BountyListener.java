package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.Bounty;
import dev.joogiebear.bountyboard.core.ClaimResult;
import dev.joogiebear.bountyboard.core.Format;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Death and join handling. */
public class BountyListener implements Listener {

    private final BountyBoardPlugin plugin;

    public BountyListener(BountyBoardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        ClaimResult result = plugin.service().onKill(killer.getUniqueId(), killer.getName(), victim.getUniqueId());
        Messages messages = plugin.messages();
        if (result.onCooldown()) {
            killer.sendMessage(messages.prefixed("claim-cooldown", Map.of("victim", victim.getName())));
            return;
        }
        if (result.claimed().isEmpty()) {
            return;
        }
        long total = 0;
        Set<UUID> posters = new HashSet<>();
        for (Bounty b : result.claimed()) {
            total += b.amount();
            posters.add(b.poster());
        }
        long tax = total - result.payout();
        killer.sendMessage(messages.prefixed("claim-killer", Map.of(
                "payout", Format.amount(result.payout()),
                "tax", Format.amount(tax))));
        Bukkit.getServer().broadcast(messages.prefixed("claim-broadcast", Map.of(
                "killer", killer.getName(),
                "amount", Format.amount(total),
                "victim", victim.getName())));
        for (UUID poster : posters) {
            Player online = Bukkit.getPlayer(poster);
            if (online != null) {
                plugin.deliverNotices(online);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.deliverNotices(player);
        long total = plugin.service().totalOn(player.getUniqueId());
        if (total > 0) {
            player.sendMessage(plugin.messages().prefixed("join-bounty", Map.of("amount", Format.amount(total))));
        }
    }
}
