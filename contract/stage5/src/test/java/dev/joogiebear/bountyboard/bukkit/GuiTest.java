package dev.joogiebear.bountyboard.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class GuiTest extends PluginTestBase {

    static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    Inventory top(PlayerMock p) {
        return p.getOpenInventory().getTopInventory();
    }

    String title(PlayerMock p) {
        return plain(p.getOpenInventory().title());
    }

    String name(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertNotNull(meta.displayName(), "the item needs a display name");
        return plain(meta.displayName());
    }

    List<String> lore(ItemStack item) {
        List<net.kyori.adventure.text.Component> lines = item.getItemMeta().lore();
        assertNotNull(lines);
        return lines.stream().map(PluginTestBase::plain).toList();
    }

    void open(PlayerMock p) {
        p.performCommand("bounty gui");
    }

    // ---- opening ----

    @Test
    void opensA54SlotBoardHeldByBountyGui() {
        open(alex);
        Inventory inv = top(alex);
        assertEquals(54, inv.getSize());
        assertInstanceOf(BountyGui.class, inv.getHolder());
    }

    @Test
    void anEmptyBoardIsPageOneOfOne() {
        open(alex);
        assertEquals("Bounty Board - Page 1/1", title(alex));
        Inventory inv = top(alex);
        for (int i = 0; i < 54; i++) {
            if (i == 49) {
                continue;
            }
            assertTrue(isEmpty(inv.getItem(i)), "slot " + i);
        }
        assertEquals(Material.PAPER, inv.getItem(49).getType());
        assertEquals("Page 1/1", name(inv.getItem(49)));
    }

    @Test
    void showsEachBountyAsAHeadWithItsDetails() {
        cmd(alex, "bounty set Steve 100");
        cmd(kim, "bounty set Steve 300 2");
        open(steve);
        Inventory inv = top(steve);
        ItemStack first = inv.getItem(0);
        assertEquals(Material.PLAYER_HEAD, first.getType());
        assertEquals("Steve", name(first));
        assertEquals(List.of("Reward: 300 coins", "Posted by: Kim", "Expires in: 2h", "ID 2"), lore(first));
        ItemStack second = inv.getItem(1);
        assertEquals(Material.PLAYER_HEAD, second.getType());
        assertEquals(List.of("Reward: 100 coins", "Posted by: Alex", "Expires in: 1d", "ID 1"), lore(second));
        assertTrue(isEmpty(inv.getItem(2)));
        assertEquals("Bounty Board - Page 1/1", title(steve));
    }

    @Test
    void theHeadBelongsToTheTarget() {
        cmd(alex, "bounty set Steve 100");
        open(alex);
        SkullMeta meta = (SkullMeta) top(alex).getItem(0).getItemMeta();
        assertNotNull(meta.getOwningPlayer());
        assertEquals("Steve", meta.getOwningPlayer().getName()); // MockBukkit does not keep the UUID of an owning player, so check the name
    }

    @Test
    void theTimeLeftIsLive() {
        cmd(alex, "bounty set Steve 100 3");
        clock.advance(Duration.ofMinutes(30));
        open(alex);
        assertEquals("Expires in: 2h 30m", lore(top(alex).getItem(0)).get(2));
    }

    @Test
    void anExpiredBountyIsNotShown() {
        cmd(alex, "bounty set Steve 100 1");
        clock.advance(Duration.ofHours(2));
        open(alex);
        assertTrue(isEmpty(top(alex).getItem(0)));
        assertEquals("Bounty Board - Page 1/1", title(alex));
    }

    // ---- paging ----

    @Test
    void pagesFortyFiveAtATime() {
        seed(50, 100);
        open(alex);
        assertEquals("Bounty Board - Page 1/2", title(alex));
        Inventory inv = top(alex);
        for (int i = 0; i < 45; i++) {
            assertEquals(Material.PLAYER_HEAD, inv.getItem(i).getType(), "slot " + i);
        }
        assertTrue(isEmpty(inv.getItem(45)));
        assertEquals("Page 1/2", name(inv.getItem(49)));
        assertEquals(Material.ARROW, inv.getItem(53).getType());
        assertEquals("Next page", name(inv.getItem(53)));
    }

    @Test
    void theArrowsChangePage() {
        seed(50, 100);
        open(alex);
        alex.simulateInventoryClick(53);
        assertEquals("Bounty Board - Page 2/2", title(alex));
        Inventory inv = top(alex);
        for (int i = 0; i < 5; i++) {
            assertEquals(Material.PLAYER_HEAD, inv.getItem(i).getType(), "slot " + i);
        }
        assertTrue(isEmpty(inv.getItem(5)));
        assertEquals(Material.ARROW, inv.getItem(45).getType());
        assertEquals("Previous page", name(inv.getItem(45)));
        assertEquals("Page 2/2", name(inv.getItem(49)));
        assertTrue(isEmpty(inv.getItem(53)));
        alex.simulateInventoryClick(45);
        assertEquals("Bounty Board - Page 1/2", title(alex));
    }

    @Test
    void clickingWhereThereIsNoArrowDoesNotChangeThePage() {
        seed(50, 100);
        open(alex);
        alex.simulateInventoryClick(45);
        assertEquals("Bounty Board - Page 1/2", title(alex));
        alex.simulateInventoryClick(53);
        alex.simulateInventoryClick(53);
        assertEquals("Bounty Board - Page 2/2", title(alex));
    }

    @Test
    void eachViewerHasTheirOwnPage() {
        seed(50, 100);
        open(alex);
        alex.simulateInventoryClick(53);
        open(steve);
        assertEquals("Bounty Board - Page 1/2", title(steve));
        assertEquals("Bounty Board - Page 2/2", title(alex));
    }

    // ---- clicks are cancelled ----

    @Test
    void everyClickInTheBoardIsCancelled() {
        cmd(alex, "bounty set Steve 100");
        open(steve);
        for (int slot : new int[] {0, 1, 20, 44, 49}) {
            InventoryClickEvent event = steve.simulateInventoryClick(slot);
            assertTrue(event.isCancelled(), "slot " + slot);
        }
        assertEquals(Material.PLAYER_HEAD, top(steve).getItem(0).getType());
    }

    @Test
    void clicksInThePlayersOwnInventoryWhileTheBoardIsOpenAreCancelled() {
        steve.getInventory().addItem(new ItemStack(Material.DIAMOND));
        cmd(alex, "bounty set Steve 100");
        open(steve);
        InventoryClickEvent plainClick = steve.simulateInventoryClick(81);
        assertTrue(plainClick.isCancelled());
        InventoryClickEvent shiftClick = steve.simulateInventoryClick(steve.getOpenInventory(), ClickType.SHIFT_LEFT, 81);
        assertTrue(shiftClick.isCancelled());
        for (int i = 0; i < 54; i++) {
            ItemStack item = top(steve).getItem(i);
            assertFalse(item != null && item.getType() == Material.DIAMOND, "slot " + i);
        }
        assertTrue(steve.getInventory().contains(Material.DIAMOND));
    }

    @Test
    void otherInventoriesAreNotAffected() {
        Inventory chest = server.createInventory(null, 27, net.kyori.adventure.text.Component.text("Chest"));
        alex.openInventory(chest);
        InventoryClickEvent event = alex.simulateInventoryClick(0);
        assertFalse(event.isCancelled());
    }
}
