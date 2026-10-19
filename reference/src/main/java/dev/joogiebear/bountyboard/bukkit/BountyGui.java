package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.Bounty;
import dev.joogiebear.bountyboard.core.Format;
import dev.joogiebear.bountyboard.core.Page;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** The bounty board inventory: 54 slots, 45 bounties per page. */
public class BountyGui implements InventoryHolder {

    static final int PAGE_SIZE = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int INDICATOR_SLOT = 49;
    static final int NEXT_SLOT = 53;

    private final Inventory inventory;
    private final int page;
    private final int pages;

    public BountyGui(BountyBoardPlugin plugin, int requestedPage) {
        Page<Bounty> data = plugin.service().page(requestedPage, PAGE_SIZE);
        this.page = data.page();
        this.pages = data.pages();
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Bounty Board - Page " + page + "/" + pages));
        int slot = 0;
        for (Bounty b : data.items()) {
            inventory.setItem(slot++, head(plugin, b));
        }
        if (page > 1) {
            inventory.setItem(PREVIOUS_SLOT, named(Material.ARROW, "Previous page"));
        }
        inventory.setItem(INDICATOR_SLOT, named(Material.PAPER, "Page " + page + "/" + pages));
        if (page < pages) {
            inventory.setItem(NEXT_SLOT, named(Material.ARROW, "Next page"));
        }
    }

    public static void open(BountyBoardPlugin plugin, Player player, int page) {
        player.openInventory(new BountyGui(plugin, page).getInventory());
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack head(BountyBoardPlugin plugin, Bounty b) {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(b.target()));
        meta.displayName(legacy.deserialize("&c" + b.targetName()));
        String remaining = Format.duration(Duration.between(plugin.activeClock().instant(), b.expiresAt()));
        meta.lore(List.of(
                legacy.deserialize("&7Reward: &a" + Format.amount(b.amount()) + " coins"),
                legacy.deserialize("&7Posted by: &f" + b.posterName()),
                legacy.deserialize("&7Expires in: &f" + remaining),
                legacy.deserialize("&8ID " + b.id())));
        item.setItemMeta(meta);
        return item;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return pages;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
