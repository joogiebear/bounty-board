package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.ClaimResult;
import dev.joogiebear.bountyboard.core.Format;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class BountyListener implements Listener {

    private final BountyBoardPlugin plugin;
    private final NoticeDelivery delivery;

    public BountyListener(BountyBoardPlugin plugin) {
        this.plugin = plugin;
        this.delivery = new NoticeDelivery(plugin);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!(victim.getKiller() instanceof Player killer)) {
            return;
        }
        ClaimResult result = plugin.service().onKill(killer.getUniqueId(), killer.getName(), victim.getUniqueId());
        if (result.onCooldown()) {
            killer.sendMessage(plugin.messages().render("prefix", Map.of())
                    .append(plugin.messages().render("claim-cooldown", Map.of("victim", victim.getName()))));
            return;
        }
        if (result.claimed().isEmpty()) {
            return;
        }
        long total = result.claimed().stream().mapToLong(b -> b.amount()).sum();
        long tax = total - result.payout();
        killer.sendMessage(plugin.messages().render("prefix", Map.of())
                .append(plugin.messages().render("claim-killer", Map.of(
                        "payout", Format.amount(result.payout()),
                        "tax", Format.amount(tax)
                ))));
        Bukkit.broadcast(plugin.messages().render("prefix", Map.of())
                .append(plugin.messages().render("claim-broadcast", Map.of(
                        "killer", killer.getName(),
                        "amount", Format.amount(total),
                        "victim", victim.getName()
                ))));
        for (var bounty : result.claimed()) {
            delivery.deliver(bounty.poster());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        delivery.deliver(player.getUniqueId());
        long total = plugin.service().totalOn(player.getUniqueId());
        if (total > 0) {
            player.sendMessage(plugin.messages().render("prefix", Map.of())
                    .append(plugin.messages().render("join-bounty", Map.of("amount", Format.amount(total)))));
        }
    }
}
