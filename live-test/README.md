# Live test on a real Paper 1.21.11 server (2026-10-19)

Server: SpawnLoft test instance on VM 104 (`spawnloft-test`), Paper 1.21.11 build 132. The jar was built in the llm-test container
(`gradle jar`) and dropped into the instance's `plugins` folder; the plugin was removed and the VM shut down afterwards.

- `scenarios/*.json` run with `node spawnloft.mjs bot run <file>` (the SpawnLoft bot harness).
  - `1-post.json`: steps 1-13 (balance, post, broadcast, list, mine) pass. **Step 14 (`waitWindow`) fails because of a bug in the bot host,
    not in the plugin:** with Minecraft 1.21.11 the window title arrives as an NBT-style object (`{"type":"string","value":"..."}`),
    `windowTitle()` in `bot-host.cjs` cannot turn it into text, so `waitWindow` never matches. The window does open (see the probes).
  - `2-claim.json`: a real PvP kill claim (`/damage bob 1000 minecraft:player_attack by carol`; the bots must be in survival mode, the instance
    defaults to creative which makes them invulnerable). 13/13 pass.
  - `3-reload.json`: change `max-amount` in `config.yml`, `/bounty admin reload`, the new limit applies. 11/11 pass.
- `*-probe.cjs`: direct mineflayer clients (run from the bot-host's `node_modules`), used where the harness could not see enough:
  the raw `open_window` packet, the board's items and lore, click cancellation (the slot resyncs a moment later, the cursor stays empty, nothing
  reaches the player's inventory) and the messages a player gets on joining (the harness misses messages that arrive before the first step).
- Restart scenarios were run by hand: back-dating `expires-at` in `data.yml` and restarting (the expiry task refunded and the notice reached the
  poster on join) and writing garbage into `data.yml` (it was moved to `data.yml.corrupt-<epoch ms>` and the plugin carried on).
