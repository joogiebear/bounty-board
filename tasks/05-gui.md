Task 5: the board GUI. Read DESIGN.md, section "The board GUI" (and the `gui` row of the command table), and follow its house rules.

The test for this task already exists and is read-only: src/test/java/dev/joogiebear/bountyboard/bukkit/GuiTest.java (plus all earlier stages' tests,
which must keep passing). It is the contract. Write the code so it passes. Never edit, add, skip or delete a test.

Create, in the package `dev.joogiebear.bountyboard.bukkit`:
- `BountyGui implements InventoryHolder`: builds the 54 slot inventory for a page (heads, arrows, page label, title) and knows which page it shows.
- `GuiListener implements Listener`: cancels every click in a board, every click that affects a board's inventory (including shift-clicks from the
  player's own inventory) and every drag over it, and turns the arrow clicks into a page change for that viewer. It must ignore every inventory whose holder is not a `BountyGui`.

Then wire it in: the `gui` subcommand of `BountyCommand` opens page 1 for the player, and `BountyBoardPlugin` registers the listener. Edit only what is needed in those two existing classes.

Notes:
- Use `Material.PLAYER_HEAD` with a `SkullMeta` whose owning player is the target (`Bukkit.getOfflinePlayer(uuid)`), Adventure components for names and lore
  (`LegacyComponentSerializer.legacyAmpersand()` for the `&` codes in DESIGN.md), and `Format.amount` / `Format.duration` for the numbers.
- The remaining time is `expiresAt` minus the service's clock (use `BountyBoardPlugin.clock()`), not the system time.
- Each viewer has their own page: keep the page inside the `BountyGui` instance. Re-render by building a new board for the new page and opening it for the viewer.
