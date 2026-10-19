package dev.joogiebear.bountyboard.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ExpiryTask implements Runnable {

    private final BountyBoardPlugin plugin;
    private final NoticeDelivery delivery;

    public ExpiryTask(BountyBoardPlugin plugin) {
        this.plugin = plugin;
        this.delivery = new NoticeDelivery(plugin);
    }

    @Override
    public void run() {
        plugin.service().expireDue();
        for (Player player : Bukkit.getOnlinePlayers()) {
            delivery.deliver(player.getUniqueId());
        }
    }
}
