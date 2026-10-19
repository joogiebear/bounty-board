package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.Format;
import dev.joogiebear.bountyboard.core.Notice;
import dev.joogiebear.bountyboard.core.NoticeType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class NoticeDelivery {

    private final BountyBoardPlugin plugin;

    public NoticeDelivery(BountyBoardPlugin plugin) {
        this.plugin = plugin;
    }

    public void deliver(UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return;
        }
        List<Notice> notices = plugin.service().takeNotices(player);
        for (Notice n : notices) {
            String key = n.type() == NoticeType.EXPIRED_REFUNDED ? "notice-expired" : "notice-claimed";
            Map<String, String> placeholders = Map.of(
                    "id", String.valueOf(n.bountyId()),
                    "amount", Format.amount(n.amount()),
                    "other", n.other()
            );
            online.sendMessage(plugin.messages().render("prefix", Map.of()).append(plugin.messages().render(key, placeholders)));
        }
    }
}
