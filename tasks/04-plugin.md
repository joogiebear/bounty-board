Task 4: the plugin: commands, listeners, economy and the expiry task. Read DESIGN.md, sections "Commands and messages", "Built-in economy",
"Listeners and tasks" and "Wiring", and follow its house rules.

The tests for this task already exist and are read-only: src/test/java/dev/joogiebear/bountyboard/bukkit/PluginTestBase.java, CommandsTest.java,
ClaimsTest.java, EconomyAndFilesTest.java and TabCompleteTest.java (plus the earlier stages' tests, which must keep passing). They load the whole
plugin on a mock server and are the contract. Write the code so they pass. Never edit, add, skip or delete a test.

`BountyBoardPlugin` exists as a stub; keep its `setClockForTests` and `clock()` exactly as they are and implement `onEnable`, `onDisable`, `service()` and
`economy()`. Put the rest in these new classes in the package `dev.joogiebear.bountyboard.bukkit` (one class per file):
- `BountyCommand` implements `CommandExecutor` and `TabCompleter` (the `/bounty` command and its completion).
- `BountyListener` implements `Listener`: `PlayerDeathEvent` and `PlayerJoinEvent`.
- `BuiltinEconomy` implements the core `Economy` interface; balances in memory, saved to `balances.yml` after every change; new players start with
  `starting-balance` from the config.
- `NoticeDelivery`: delivers a player's pending notices as messages (used by the join listener, the expiry task and after a claim).
- `ExpiryTask` implements `Runnable`: calls `service.expireDue()` and delivers notices to online players; schedule it every 600 ticks.

Notes:
- Look messages up through `Messages.render(key, placeholders)` and send `prefix + message` as Adventure components. Build the `Messages`, `Settings`
  (`BountySettings.fromConfig`) and the service in `onEnable`; log each settings warning once with the plugin logger. `plugin.yml` and `config.yml` are
  read-only and already correct.
- The service uses `BountyBoardPlugin.clock()`. The data file is `data.yml` in the plugin's data folder (`FileBountyStore`).
- Resolve command targets with `Bukkit.getPlayerExact(name)` first, then an offline player whose `hasPlayedBefore()` is true, else `unknown-player`.
- Keep everything on the main thread; do not start your own threads.
