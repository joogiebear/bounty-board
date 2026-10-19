# BountyBoard pilot: scorecard (2026-10-19)

A fresh Paper 1.21.11 plugin (players post escrowed bounties on each other; a kill pays the killer; unclaimed bounties expire and refund;
paged board GUI), built with the tests-first loop: Claude wrote `DESIGN.md`, the skeleton and the tests; Qwen3.8 27B (128k context, Aider)
wrote the code one task at a time; `verify_gradle.py` (compile, JUnit, house rules, read-only check) fed failures back, up to four rounds.
Qwen's final code is in `qwen-code/`; `reference/` is an independent implementation built from the spec alone.

## Setup and calibration

- Environment: OpenJDK 21 and Gradle 8.14.3 in the llm-test container; Paper API 1.21.11 and MockBukkit 4.116.3 resolve and run a command test in about 22 s.
- **149 visible tests** in five stages (service rules, file store, settings/format/messages, the whole plugin on a mock server, the GUI).
- An independent agent implemented the spec **without seeing the tests**; the tests then passed on its code except two, both my fault (a forgotten
  message drain; a MockBukkit quirk with skull owners), which I fixed before Qwen started.

## Result on the visible tests

| Stage | Task | First attempt | Fix rounds (model time) |
| --- | --- | --- | --- |
| 1 | `BountyService` rules | 183 s | 0 |
| 2 | `FileBountyStore` | 529 s | 0 |
| 3 | Settings, `Format`, `Messages` | 232 s | 0 |
| 4 | Commands, listeners, economy, expiry task | 601 s | 170 s (compile errors, one invented package `org.bukkit.logging`), 67 s (one message detail) |
| 5 | Board GUI | 251 s | 0 |

Stages 1, 2, 3 and 5 passed on the first attempt. About 31 minutes of model time to green.

## What the visible tests did not catch

A review of the code and a **blind audit** (a second agent wrote 239 more test executions from the spec alone and validated them on the
reference) both found real defects. On Qwen's code **7 of the 239 held-out executions failed**, and they matched the review findings:

- **Corrupt data file on a real server:** `FileBountyStore.load` used `YamlConfiguration.loadConfiguration`, which swallows parse errors when a
  server exists, so a corrupt `data.yml` would be overwritten with no backup. The unit tests passed only because `Bukkit.getLogger()` throws without a server.
- **`config.yml` never written** (no `saveDefaultConfig()`); **`admin reload` did not reach the running service** (partly a gap in my spec).
- **`long` to `int` narrowing:** `hours` 4294967297 became 1 hour and `cancel 4294967297` cancelled bounty #1.

Not caught by any test and left open: `starting-balance` is ignored on reload (the spec says it should apply), `/bounty set` scans every offline
player ever seen on each call (slow on a big server), board names and lore render in italics by default, `list` is refused from the console (the spec was
vague), and a failed save throws after the money has already moved.

## Stage 6: the findings back through the loop

The held-out tests became read-only stage 6, the spec gained the missing rules (config file, reload, live-server persistence, int range), and the
loop ran: the first attempt (516 s) fixed 5 of the 7 failures, one fix round (401 s, given the failing test output) fixed the corrupt-file handling.
**All 388 test executions pass.** Total model time: about **49 minutes** (the corrupt-file fix was the only thing the first attempt missed).

## Live check on real Paper 1.21.11 (`live-test/`)

Confirmed on a real server: the plugin enables and writes `config.yml`; post, broadcast, balance, list, mine and the error messages; the board opens
(`generic_9x6`, titled `Bounty Board - Page 1/1`) with a player head, coloured name and lore, and every click is cancelled and resynced; a real PvP
kill via `/damage ... by carol` pays 95 coins after 5% tax with broadcast and notice; reload applies a lowered `max-amount`; after a restart an
expired bounty is refunded and the poster is told on join; a corrupt `data.yml` is quarantined and the plugin carries on. No exceptions in the log.
Not checked live: the 45-bounty paging (covered by the mock-server tests) and the italics.

## Findings about the tooling

- **SpawnLoft bot host bug:** `waitWindow` can never match on 1.21.11 because the window title is an NBT-style object that `windowTitle()` does not convert.
  The harness also misses messages sent before its first step, and new players are creative (invulnerable) by default on the test instance.
- MockBukkit does not keep a skull owner's UUID, and its `getPlayerExact` ignores case, so those two behaviours can only be checked live.

## What this says about delegating plugin work

- **Worked:** every pure-logic and plumbing task (rules, persistence, config, commands, GUI) came out right from a precise spec and tests,
  mostly first time, in about half an hour of model time.
- **Where Qwen needed help:** an invented API package, a Bukkit helper that behaves differently with and without a server, and silent integer
  narrowing. All three compile; none is visible to unit tests. These are exactly what the review, the blind audit and the live run found.
- **What made it work:** an exact spec, read-only tests, a calibrated reference, a blind audit, and Claude's review of anything environment-dependent.
  Without the audit and the review, the plugin would have shipped with a data-loss bug on corrupt files and no config file on first start.
- **Spec side:** two of the findings (config file, reload) were gaps in my brief, not Qwen's mistakes. A checklist of "things a real server needs"
  (default config, reload, persistence on a live server, input ranges) belongs in every plugin spec.
