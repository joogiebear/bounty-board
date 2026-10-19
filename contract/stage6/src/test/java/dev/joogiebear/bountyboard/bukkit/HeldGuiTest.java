package dev.joogiebear.bountyboard.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.joogiebear.bountyboard.core.CreateStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HeldGuiTest extends HeldBase {

    private void fill(int count, Integer firstHours) {
        for (int i = 0; i < count; i++) {
            UUID poster = new UUID(7, i);
            plugin.economy().deposit(poster, 1000);
            Integer hours = i == 0 ? firstHours : null;
            assertEquals(CreateStatus.OK, plugin.service().create(poster, "Poster" + i, new UUID(8, i), "Target" + i, 100 + i, hours).status());
        }
    }

    private Inventory open(PlayerMock p) {
        p.performCommand("bounty gui");
        return p.getOpenInventory().getTopInventory();
    }

    private String title(PlayerMock p) {
        return plain(p.getOpenInventory().title());
    }

    private static String name(ItemStack item) {
        return plain(item.getItemMeta().displayName());
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    @Test
    void emptyBoardIsPageOneOfOne() {
        PlayerMock p = player("Viewer");
        Inventory inv = open(p);
        assertEquals(54, inv.getSize());
        assertInstanceOf(BountyGui.class, inv.getHolder());
        assertEquals("Bounty Board - Page 1/1", title(p));
        for (int slot = 0; slot < 54; slot++) {
            if (slot == 49) continue;
            assertTrue(empty(inv.getItem(slot)), "slot " + slot);
        }
        assertEquals(Material.PAPER, inv.getItem(49).getType());
        assertEquals("Page 1/1", name(inv.getItem(49)));
    }

    @Test
    void bountyHeadsCarryTheDocumentedNameLoreAndOwnerSortedByAmount() {
        PlayerMock p = player("Viewer");
        fill(3, null);
        Inventory inv = open(p);
        ItemStack top = inv.getItem(0);
        assertEquals(Material.PLAYER_HEAD, top.getType());
        assertEquals("Target2", name(top));
        List<Component> lore = top.getItemMeta().lore();
        assertNotNull(lore);
        assertEquals(List.of("Reward: 102 coins", "Posted by: Poster2", "Expires in: 1d", "ID 3"), lore.stream().map(HeldBase::plain).toList());
        assertEquals("Target1", name(inv.getItem(1)));
        assertEquals("Target0", name(inv.getItem(2)));
        assertTrue(empty(inv.getItem(3)));
    }

    @Test
    void headOwnerIsLookedUpByTargetUuidNotByName() {
        PlayerMock viewer = player("Viewer");
        PlayerMock bob = player("Bob");
        UUID poster = new UUID(7, 7);
        plugin.economy().deposit(poster, 1000);
        assertEquals(CreateStatus.OK, plugin.service().create(poster, "Poster", bob.getUniqueId(), "SomeOtherName", 100, null).status());
        Inventory inv = open(viewer);
        assertEquals("SomeOtherName", name(inv.getItem(0)));
        SkullMeta meta = (SkullMeta) inv.getItem(0).getItemMeta();
        assertEquals(bob.getUniqueId(), meta.getPlayerProfile().getId(), "the head must be owned by the target's UUID");
    }

    @Test
    void exactly45BountiesFillOnePageWithoutNavigation() {
        PlayerMock p = player("Viewer");
        fill(45, null);
        Inventory inv = open(p);
        assertEquals("Bounty Board - Page 1/1", title(p));
        for (int slot = 0; slot < 45; slot++) assertEquals(Material.PLAYER_HEAD, inv.getItem(slot).getType(), "slot " + slot);
        assertTrue(empty(inv.getItem(45)));
        assertTrue(empty(inv.getItem(53)));
        assertEquals("Page 1/1", name(inv.getItem(49)));
    }

    @Test
    void the46thBountyCreatesASecondPageWithWorkingArrows() {
        PlayerMock p = player("Viewer");
        fill(46, null);
        Inventory inv = open(p);
        assertEquals("Bounty Board - Page 1/2", title(p));
        assertTrue(empty(inv.getItem(45)));
        assertEquals(Material.ARROW, inv.getItem(53).getType());
        assertEquals("Next page", name(inv.getItem(53)));
        assertEquals("Page 1/2", name(inv.getItem(49)));
        assertEquals("Target1", name(inv.getItem(44)), "slot 44 holds the 45th biggest bounty; the smallest is on page 2");

        InventoryClickEvent next = p.simulateInventoryClick(p.getOpenInventory(), ClickType.LEFT, 53);
        assertTrue(next.isCancelled());
        Inventory second = p.getOpenInventory().getTopInventory();
        assertInstanceOf(BountyGui.class, second.getHolder());
        assertEquals("Bounty Board - Page 2/2", title(p));
        assertEquals(Material.PLAYER_HEAD, second.getItem(0).getType());
        assertTrue(empty(second.getItem(1)));
        assertEquals(Material.ARROW, second.getItem(45).getType());
        assertEquals("Previous page", name(second.getItem(45)));
        assertTrue(empty(second.getItem(53)));
        assertEquals("Page 2/2", name(second.getItem(49)));

        InventoryClickEvent prev = p.simulateInventoryClick(p.getOpenInventory(), ClickType.LEFT, 45);
        assertTrue(prev.isCancelled());
        assertEquals("Bounty Board - Page 1/2", title(p));
    }

    @Test
    void pageShrinksWhenBountiesExpire() {
        PlayerMock p = player("Viewer");
        fill(46, 1);
        open(p);
        assertEquals("Bounty Board - Page 1/2", title(p));
        p.closeInventory();
        clock.advance(Duration.ofHours(1)); // bounty #1 passes its expiry; nobody ran the expiry task yet
        Inventory inv = open(p);
        assertEquals("Bounty Board - Page 1/1", title(p));
        assertTrue(empty(inv.getItem(53)));
        long heads = java.util.stream.IntStream.range(0, 45).filter(i -> !empty(inv.getItem(i))).count();
        assertEquals(45, heads);
    }

    @Test
    void everyClickInTheBoardIsCancelled() {
        PlayerMock p = player("Viewer");
        fill(2, null);
        Inventory inv = open(p);
        for (int slot : new int[] {0, 1, 2, 20, 44, 49, 50, 53}) {
            InventoryClickEvent e = p.simulateInventoryClick(p.getOpenInventory(), ClickType.LEFT, slot);
            assertTrue(e.isCancelled(), "left click slot " + slot);
        }
        assertTrue(p.simulateInventoryClick(p.getOpenInventory(), ClickType.RIGHT, 0).isCancelled());
        assertTrue(p.simulateInventoryClick(p.getOpenInventory(), ClickType.SHIFT_LEFT, 0).isCancelled());
        assertEquals(Material.PLAYER_HEAD, inv.getItem(0).getType(), "the head must still be there");
        assertEquals("Bounty Board - Page 1/1", title(p));
    }

    @Test
    void clickingTheOwnInventoryAndShiftClickingIntoTheBoardIsCancelled() {
        PlayerMock p = player("Viewer");
        p.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
        Inventory inv = open(p);
        InventoryView view = p.getOpenInventory();
        int playerSlot = 54 + 27; // first hotbar slot in the raw slot numbering of a 54 slot chest view
        assertTrue(p.simulateInventoryClick(view, ClickType.SHIFT_LEFT, playerSlot).isCancelled());
        assertTrue(p.simulateInventoryClick(view, ClickType.LEFT, playerSlot).isCancelled());
        for (int slot = 0; slot < 54; slot++) {
            if (slot != 49) assertTrue(empty(inv.getItem(slot)), "slot " + slot + " must stay empty");
        }
    }

    @Test
    void dragsAcrossTheBoardAreCancelled() {
        PlayerMock p = player("Viewer");
        open(p);
        InventoryView view = p.getOpenInventory();
        ItemStack stone = new ItemStack(Material.STONE, 8);
        InventoryDragEvent drag = new InventoryDragEvent(view, stone, stone, false, Map.of(5, new ItemStack(Material.STONE, 4), 6, new ItemStack(Material.STONE, 4)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
    }

    @Test
    void arrowThatIsNotThereDoesNothing() {
        PlayerMock p = player("Viewer");
        fill(46, null);
        open(p);
        p.simulateInventoryClick(p.getOpenInventory(), ClickType.LEFT, 45);
        assertEquals("Bounty Board - Page 1/2", title(p));
    }
}
