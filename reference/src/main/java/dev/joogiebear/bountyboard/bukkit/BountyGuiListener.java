package dev.joogiebear.bountyboard.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Keeps the board read-only and handles the page arrows. */
public class BountyGuiListener implements Listener {

    private final BountyBoardPlugin plugin;

    public BountyGuiListener(BountyBoardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BountyGui gui)) {
            return;
        }
        event.setCancelled(true);
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getView().getTopInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == BountyGui.PREVIOUS_SLOT && gui.page() > 1) {
            BountyGui.open(plugin, player, gui.page() - 1);
        } else if (slot == BountyGui.NEXT_SLOT && gui.page() < gui.pages()) {
            BountyGui.open(plugin, player, gui.page() + 1);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BountyGui)) {
            return;
        }
        int size = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < size) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
