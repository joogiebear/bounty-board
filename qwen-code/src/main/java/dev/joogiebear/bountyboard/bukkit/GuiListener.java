package dev.joogiebear.bountyboard.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class GuiListener implements Listener {

    private final BountyBoardPlugin plugin;

    public GuiListener(BountyBoardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BountyGui gui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == top) {
            int slot = event.getSlot();
            if (slot == BountyGui.NEXT_SLOT && gui.page() < gui.pages()) {
                openPage((Player) event.getWhoClicked(), gui.page() + 1, gui.pages());
            } else if (slot == BountyGui.PREVIOUS_SLOT && gui.page() > 1) {
                openPage((Player) event.getWhoClicked(), gui.page() - 1, gui.pages());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BountyGui)) {
            return;
        }
        event.setCancelled(true);
    }

    private void openPage(Player player, int page, int pages) {
        BountyGui gui = new BountyGui(plugin.service(), page, pages);
        player.openInventory(gui.getInventory());
    }
}
