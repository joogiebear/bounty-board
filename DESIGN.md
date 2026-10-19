# BountyBoard: design brief

A Paper 1.21.11 plugin. Players post bounties on other players with coins held in escrow; killing a player with
active bounties pays the killer; unclaimed bounties expire and refund. Java 21, Gradle (Kotlin DSL), Paper API
`io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` (compile only), no other runtime dependencies. Package
`dev.joogiebear.bountyboard`. Tests: JUnit 5 and MockBukkit (`org.mockbukkit.mockbukkit:mockbukkit-v1.21`).

## House rules (every task)

1. **Exact behaviour.** Follow this brief word for word, including the exact default message texts. Add no features,
   commands, config keys or messages it does not list.
2. **Tests are the contract and are read-only.** Never edit, add, skip, `@Disabled` or delete a test.
3. **Pure logic stays out of Bukkit.** Classes in `...bountyboard.core` (domain and service) must not import
   `org.bukkit` or `net.kyori`. Bukkit-facing code is in `...bountyboard.bukkit`.
4. **Time comes from the injected `java.time.Clock`**, never `Instant.now()` or `System.currentTimeMillis()` in core code.
5. **Money is a `long` number of coins.** Never use floating point for money. Percentages round down (`floor`).
6. **Never block or touch the Bukkit API off the main thread**; file I/O may happen on the calling thread in this
   version, so keep it simple and synchronous.
7. No wildcard imports, no `null` returned where an `Optional` or an empty list is the contract, no raw types, no
   `System.out`; use the plugin logger in Bukkit code.

## Core types (`...bountyboard.core`)

```java
public enum BountyStatus { ACTIVE, CLAIMED, EXPIRED, CANCELLED }

public record Bounty(int id, UUID target, String targetName, UUID poster, String posterName, long amount,
                     Instant createdAt, Instant expiresAt, BountyStatus status, UUID claimedBy, Instant resolvedAt) {}
// claimedBy and resolvedAt are null while ACTIVE; resolvedAt is set for every other status; claimedBy only for CLAIMED.
// Bounty is immutable: state changes produce a new record (use `withResolved(status, claimedBy, resolvedAt)`).

public enum NoticeType { EXPIRED_REFUNDED, CLAIMED }
public record Notice(UUID player, NoticeType type, int bountyId, long amount, String other) {}
// EXPIRED_REFUNDED: "your bounty #id expired and `amount` was refunded" (other = target name).
// CLAIMED: "your bounty #id was claimed by `other`" (other = killer name, amount = the bounty's amount).

public record StoreData(List<Bounty> bounties, List<Notice> notices, int nextId) {}   // nextId starts at 1

public interface BountyStore { StoreData load(); void save(StoreData data); }
public interface Economy { long balance(UUID player); boolean withdraw(UUID player, long amount); void deposit(UUID player, long amount); }
// withdraw returns false and changes nothing when the balance is too low.

public record Settings(long minAmount, long maxAmount, int maxActivePerPoster, int defaultHours, int maxHours,
                       int taxPercent, int claimCooldownMinutes, int cancelFullRefundMinutes, int cancelRefundPercent,
                       int purgeResolvedAfterDays) {
    public static Settings defaults() { /* 10, 1_000_000, 3, 24, 72, 5, 10, 5, 80, 7 */ }
}
public record Page<T>(List<T> items, int page, int pages, int total) {}
```

## `BountyService` (stage 1)

`new BountyService(Settings settings, Economy economy, BountyStore store, Clock clock)`. The constructor loads
`store.load()`; all state lives in memory afterwards. **After every operation that changes state, the service calls
`store.save(...)` once** with a snapshot (including `nextId`). Read-only calls never save.

### `create(UUID poster, String posterName, UUID target, String targetName, long amount, Integer hours)`

Returns `CreateResult(CreateStatus status, Bounty bounty)` (`bounty` null unless `OK`). Checks run in this order and the first
failure wins; a failed create changes nothing (no withdrawal, no save, no id used):

1. `SELF_TARGET`: poster equals target.
2. `AMOUNT_TOO_LOW`: amount < minAmount.
3. `AMOUNT_TOO_HIGH`: amount > maxAmount.
4. `INVALID_DURATION`: `hours` not null and (< 1 or > maxHours). `null` means `defaultHours`.
5. `TOO_MANY_ACTIVE`: the poster already has `maxActivePerPoster` ACTIVE bounties.
6. `INSUFFICIENT_FUNDS`: `economy.withdraw(poster, amount)` returned false.

On success the full `amount` is withdrawn (escrow), the bounty gets the next id (1, 2, 3, ...), `createdAt = clock.instant()`,
`expiresAt = createdAt + hours`, status ACTIVE. Several bounties from the same poster on the same target are allowed.

### `cancel(int id, UUID actor, boolean admin)`

Returns `CancelResult(CancelStatus status, long refunded)`. Statuses, checked in this order: `NOT_FOUND`; `NOT_ACTIVE` (status
is not ACTIVE); `NOT_OWNER` (actor is not the poster and `admin` is false); `OK`. On OK the bounty becomes CANCELLED
(`resolvedAt = now`) and the poster is refunded with `economy.deposit`: **admin cancel: 100%**; otherwise 100% if
`now <= createdAt + cancelFullRefundMinutes`, else `floor(amount * cancelRefundPercent / 100)`. `refunded` is the refunded amount.
An expired-but-not-yet-processed bounty is still ACTIVE for `cancel` (expiry is only done by `expireDue`).

### `onKill(UUID killer, String killerName, UUID victim)`

Returns `ClaimResult(List<Bounty> claimed, long payout, boolean onCooldown)`. A kill claims nothing (empty list, payout 0,
`onCooldown` false) if killer equals victim, or if the victim has no claimable bounty. A bounty is **claimable** if it is ACTIVE,
its target is the victim, its poster is not the killer, and `now < expiresAt`. Otherwise:

- **Cooldown:** if this killer already claimed bounties on this victim less than `claimCooldownMinutes` ago (measured from that
  claim's time to now, strictly less), nothing is claimed and `onCooldown` is true. The cooldown is kept in memory only
  (not saved).
- Else all claimable bounties are claimed together: `total` = sum of amounts, `tax = floor(total * taxPercent / 100)`,
  `payout = total - tax`; `economy.deposit(killer, payout)`; each bounty becomes CLAIMED (`claimedBy = killer`, `resolvedAt = now`);
  each poster gets a `Notice(CLAIMED, bountyId, amount, killerName)` (one per bounty, in bounty id order); the claim time is
  recorded for the cooldown; the list returned is in bounty id order.

### `expireDue()`

Returns the number of bounties expired. Every ACTIVE bounty with `expiresAt <= now` becomes EXPIRED (`resolvedAt = now`), its poster
is refunded the **full** amount via `economy.deposit`, and the poster gets `Notice(EXPIRED_REFUNDED, id, amount, targetName)`. Then
every resolved (non-ACTIVE) bounty whose `resolvedAt` is more than `purgeResolvedAfterDays` days before now is removed.
Saves only if something was expired or purged.

### Queries (no saving)

- `Optional<Bounty> get(int id)`.
- `List<Bounty> active()`: all ACTIVE bounties that have not passed `expiresAt`, sorted by amount descending, then `createdAt`
  ascending, then id ascending.
- `List<Bounty> activeOn(UUID target)`, `List<Bounty> activeBy(UUID poster)`: filtered `active()`, same order.
- `long totalOn(UUID target)`: sum of amounts of `activeOn(target)`.
- `Page<Bounty> page(int page, int size)`: 1-based page of `active()`; `pages = max(1, ceil(total / size))`; `page` is clamped into
  `[1, pages]`; `size` must be > 0 (else `IllegalArgumentException`).
- `List<Notice> takeNotices(UUID player)`: returns that player's notices in creation order and removes them; saves only if the
  list was not empty.

## Persistence (`...bountyboard.core`, stage 2)

`FileBountyStore(Path file)` implements `BountyStore`, YAML via `org.bukkit.configuration.file.YamlConfiguration` (it is allowed
here, it does not need a server). `save` writes to a temp file in the same directory and moves it over the target atomically, creating
parent directories. `load`: a missing file gives `new StoreData(List.of(), List.of(), 1)`; an unreadable or invalid file is renamed
to `<name>.corrupt-<epoch millis>` next to it and an empty `StoreData` is returned (never throws). This must hold on a live Bukkit server too, not only in a unit test without a server (do not rely on helpers that swallow parse errors). A round trip must preserve every
field exactly (instants as epoch milliseconds, null `claimedBy` and `resolvedAt`, notices in order, `nextId`).

## Config, messages and formatting (stage 3)

`BountySettings.fromConfig(ConfigurationSection section)` returns `Loaded(Settings settings, List<String> warnings)`. Keys (all
optional, defaults in brackets): `min-amount` (10), `max-amount` (1000000), `max-active-per-player` (3), `default-hours` (24),
`max-hours` (72), `tax-percent` (5), `claim-cooldown-minutes` (10), `cancel-full-refund-minutes` (5), `cancel-refund-percent`
(80), `purge-resolved-after-days` (7). A value of the wrong type or out of range falls back to its default and adds one warning
`"<key>: invalid value, using <default>"`. Ranges: amounts >= 1; counts and hours >= 1; `tax-percent` and `cancel-refund-percent`
0-100; minutes and days >= 0. If after loading `min-amount > max-amount`, use both defaults and add the warning
`"min-amount is above max-amount, using defaults"`. `default-hours` above `max-hours` falls back to `max-hours` with the warning
`"default-hours is above max-hours, using <max-hours>"`.

`Format.amount(long)`: thousands separated by commas, no unit (`1234567` -> `1,234,567`). `Format.duration(Duration)`: whole seconds
(round down); `>= 1d` -> `Xd Yh`, `>= 1h` -> `Xh Ym`, `>= 1m` -> `Xm Ys`, else `Xs`; a trailing zero part is omitted (`1h`, `2d 3h`,
`5m`, `0s`). Negative durations count as zero.

`Messages` (Bukkit side, uses Adventure): `new Messages(ConfigurationSection section)`; `Component render(String key, Map<String,String>
placeholders)` replaces `{name}` in the text, then converts `&` color codes with `LegacyComponentSerializer.legacyAmpersand()`. A key missing from the
config uses the default below; a key not in the defaults renders as the plain text `<missing:KEY>`. A placeholder with no value is left as is.

## Commands and messages (stage 4)

Command `/bounty` (plugin.yml), permission `bounty.use` (default true) for everything but `admin` which needs `bounty.admin` (default op). A sender
that is not a player gets `console-only-player` for player-only subcommands. No subcommand shows `usage`.

| subcommand | behaviour |
| --- | --- |
| `set <player> <amount> [hours]` | resolve the target: an online player, else an offline player with `hasPlayedBefore()`, else `unknown-player`; amount must be a whole number >= 0 else `invalid-number`; call `create`; on OK message the poster `bounty-set` and broadcast `bounty-posted`; failures use the status messages below |
| `list [page]` | `page(page, 10)`; one `list-line` per bounty under a `list-header`; empty gives `list-empty` |
| `mine` | the sender's active bounties (as `activeBy`), one `mine-line` each; none gives `mine-empty` |
| `cancel <id>` | `cancel(id, sender, sender has bounty.admin)`; OK gives `cancel-ok` with the refunded amount; failures `cancel-not-found`, `cancel-not-active`, `cancel-not-owner` |
| `balance` | `balance` message |
| `gui` | opens the board (stage 5) |
| `admin give <player> <amount>` | needs `bounty.admin`; deposits coins via the economy; `admin-given` |
| `admin reload` | re-reads `config.yml` from disk and applies it immediately to the running plugin: every `Settings` value the service uses (limits, durations, tax, cooldown, refund rules, purge days), the messages, and `starting-balance` for players the economy has not seen yet; existing bounties and balances are kept; then `reloaded` |

A subcommand with the wrong number of arguments shows `usage`. A `list` page that is not a whole number >= 1 gives `invalid-number`. `set` takes 2 or 3
arguments, `list` 0 or 1, `mine`, `balance` and `gui` none, `cancel` 1, `admin give` 2, `admin reload` 0. `hours` must be a whole number (else `invalid-number`); so must the `cancel` id and the `admin give` amount (else `invalid-number`).
Whole numbers that do not fit an `int` (more than 2147483647) are never truncated: an `hours` value that big gives `invalid-duration`, a `cancel` id that big gives `cancel-not-found`, and a `list` page that big is clamped to the last page. The target name is matched exactly as typed (`Bukkit.getPlayerExact` for online players).

Default messages (config section `messages`; placeholders in braces; `&` colour codes are used as written):

| key | default text |
| --- | --- |
| `prefix` | `&6[Bounty] &r` |
| `usage` | `Usage: /bounty <set|list|mine|cancel|balance|gui>` |
| `console-only-player` | `This command can only be used by a player.` |
| `unknown-player` | `&cUnknown player: {player}` |
| `invalid-number` | `&cThat is not a valid number.` |
| `bounty-set` | `&aBounty #{id} posted on {target} for {amount} coins.` |
| `bounty-posted` | `&e{poster} posted a {amount} coin bounty on {target}!` |
| `self-target` | `&cYou cannot place a bounty on yourself.` |
| `amount-too-low` | `&cThe minimum bounty is {min} coins.` |
| `amount-too-high` | `&cThe maximum bounty is {max} coins.` |
| `invalid-duration` | `&cDuration must be between 1 and {max} hours.` |
| `too-many-active` | `&cYou already have {max} active bounties.` |
| `insufficient-funds` | `&cYou need {amount} coins, you have {balance}.` |
| `list-header` | `&6Bounties (page {page}/{pages}, {total} total)` |
| `list-line` | `&e#{id} &f{target} &7- &a{amount} coins &7(by {poster}, {remaining} left)` |
| `list-empty` | `&7There are no active bounties.` |
| `mine-line` | `&e#{id} &f{target} &7- &a{amount} coins &7({remaining} left)` |
| `mine-empty` | `&7You have no active bounties.` |
| `cancel-ok` | `&aBounty #{id} cancelled, {refunded} coins refunded.` |
| `cancel-not-found` | `&cThere is no bounty #{id}.` |
| `cancel-not-active` | `&cBounty #{id} is no longer active.` |
| `cancel-not-owner` | `&cBounty #{id} is not yours.` |
| `balance` | `&7Balance: &a{balance} coins` |
| `admin-given` | `&aGave {amount} coins to {player}.` |
| `reloaded` | `&aConfiguration reloaded.` |
| `no-permission` | `&cYou do not have permission.` |
| `claim-broadcast` | `&c{killer} claimed {amount} coins of bounties on {victim}!` |
| `claim-killer` | `&aYou collected {payout} coins ({tax} tax).` |
| `claim-cooldown` | `&7You recently claimed a bounty on {victim}; try again later.` |
| `notice-expired` | `&7Your bounty #{id} on {other} expired; {amount} coins refunded.` |
| `notice-claimed` | `&7Your bounty #{id} was claimed by {other}.` |
| `join-bounty` | `&cThere is a {amount} coin bounty on your head!` |

(Notice messages use `{id}`, `{amount}` and `{other}` from the `Notice` record.)

All player messages are sent as `prefix + message`. Tab completion: every suggestion list is filtered by what has been typed so far (prefix, case-insensitive). The first argument lists the subcommands the sender may use (`admin` only with `bounty.admin`); `set`: online player names (not the sender), then `100 500 1000`, then `1 6 24 72`;
`cancel`: the sender's active bounty ids; `admin`: `give reload`; `admin give`: online player names.

## Built-in economy (stage 4)

`BuiltinEconomy implements Economy` (in `...bukkit`): balances in memory, saved to `balances.yml` in the plugin folder after every change, a
new player's balance is `starting-balance` (config, default 1000, >= 0).

## Listeners and tasks (stage 4)

- `PlayerDeathEvent`: if the victim has a killer that is a `Player` (`victim.getKiller()`), call `onKill`. If it claimed: send `claim-killer` to the killer
  and broadcast `claim-broadcast` (`{amount}` is the sum of the claimed bounties, `{tax}` the tax, in that order of sending: killer message first, then the
  broadcast), then deliver the new notices to every online poster straight away. If `onCooldown`: send `claim-cooldown` to the killer.
- A sender without permission for `admin` gets `no-permission`.
- `PlayerJoinEvent`: deliver the player's notices (`takeNotices`), one message each (`notice-expired` / `notice-claimed`), then if `totalOn(player) > 0` send `join-bounty`.
- A repeating task every 600 ticks calls `expireDue()` and delivers notices to online players straight away.

## The board GUI (stage 5)

`BountyGui implements InventoryHolder`, 54 slots, title `Bounty Board - Page {page}/{pages}` (plain text). Slots 0-44 show the page's bounties (page size 45) as `PLAYER_HEAD`
items owned by the target (`skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(bounty.target()))`, by UUID, never by name): display name `&c{target}` , lore lines `&7Reward: &a{amount} coins`, `&7Posted by: &f{poster}`, `&7Expires in: &f{remaining}`, `&8ID {id}`. Slot 45: `ARROW` "Previous page" when page > 1; slot 49: `PAPER`
named `Page {page}/{pages}`; slot 53: `ARROW` "Next page" when page < pages; everything else empty. Every click in the GUI (and any click or drag
affecting its inventory, including shift-clicks from the player inventory) is cancelled; clicking the previous/next arrows re-renders the viewer's GUI on that page. Opening
the GUI with no bounties shows an empty page 1/1.

## Wiring (stage 6)

`BountyBoardPlugin extends JavaPlugin` (in `...bountyboard.bukkit`) exposes `public BountyService service()` and `public Economy economy()` and a static
test seam `public static void setClockForTests(Clock clock)` (null restores the system clock); `onEnable` builds the service with that clock (so a
test sets the clock before loading the plugin). The resources `plugin.yml` and `config.yml` are provided; do not change them. `plugin.yml` (name `BountyBoard`, `api-version: "1.21"`, command `bounty`, permissions), default `config.yml`
(all settings keys above plus `starting-balance` and an empty `messages:` section), `onEnable` first calls `saveDefaultConfig()` so `config.yml` is written to the plugin folder when it does not exist yet, then loads config and messages, builds the store (`data.yml`), the
service (system clock), the economy, registers the command, listeners and the expiry task, and logs one line per settings warning. `onDisable` saves.
