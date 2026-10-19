package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.Bounty;
import dev.joogiebear.bountyboard.core.BountyService;
import dev.joogiebear.bountyboard.core.Format;
import dev.joogiebear.bountyboard.core.Page;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class BountyGui implements InventoryHolder {

    static final int PAGE_SIZE = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int INDICATOR_SLOT = 49;
    static final int NEXT_SLOT = 53;

    private final Inventory inventory;
    private final int page;
    private final int pages;

    public BountyGui(BountyService service, int page, int pages) {
        this.page = page;
        this.pages = pages;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Bounty Board - Page " + page + "/" + pages));
        build(service);
    }

    private void build(BountyService service) {
        Page<Bounty> data = service.page(page, PAGE_SIZE);
        List<Bounty> bounties = data.items();
        for (int i = 0; i < bounties.size(); i++) {
            inventory.setItem(i, headItem(bounties.get(i)));
        }
        if (page > 1) {
            inventory.setItem(PREVIOUS_SLOT, arrowItem("Previous page"));
        }
        inventory.setItem(INDICATOR_SLOT, pageLabel());
        if (page < pages) {
            inventory.setItem(NEXT_SLOT, arrowItem("Next page"));
        }
    }

    private ItemStack headItem(Bounty b) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(b.target()));
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&c" + b.targetName()));
        meta.lore(List.of(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&7Reward: &a" + Format.amount(b.amount()) + " coins"),
                LegacyComponentSerializer.legacyAmpersand().deserialize("&7Posted by: &f" + b.posterName()),
                LegacyComponentSerializer.legacyAmpersand().deserialize("&7Expires in: &f" + Format.duration(Duration.between(BountyBoardPlugin.clock().instant(), b.expiresAt()))),
                LegacyComponentSerializer.legacyAmpersand().deserialize("&8ID " + b.id())
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack arrowItem(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pageLabel() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Page " + page + "/" + pages));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return pages;
    }
}
