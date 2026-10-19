# BountyBoard: build plan (Claude designs, Qwen3.8 codes)

Pilot of the tests-first loop on a Paper plugin. Claude writes `DESIGN.md`, the interfaces and the tests; Qwen3.8 27B writes the
code through Aider one task at a time; a verify step (Gradle compile + all tests + house-rule checks) feeds failures back; Claude reviews.

## Tasks (each is one Aider run plus verify and up to four fix rounds)

| # | Task | Qwen writes | Claude writes first | Verified by |
| --- | --- | --- | --- | --- |
| 0 | Skeleton | nothing (Claude: Gradle files, plugin.yml, core interfaces and records as stubs) | `build.gradle.kts`, records, interfaces | compile |
| 1 | `BountyService` rules | `core/BountyService.java` | `BountyServiceTest` (fake Economy, Store, Clock; ~45 tests) | JUnit |
| 2 | File persistence | `core/FileBountyStore.java` | `FileBountyStoreTest` (temp dirs, corrupt file, round trips) | JUnit |
| 3 | Config, messages, formatting | `BountySettings`, `Format`, `bukkit/Messages` | `BountySettingsTest`, `FormatTest`, `MessagesTest` | JUnit |
| 4 | Commands, listeners, economy, task | `bukkit/*` (command, tab complete, listeners, `BuiltinEconomy`, expiry task) | MockBukkit tests per behaviour in the DESIGN tables | JUnit + MockBukkit |
| 5 | Board GUI | `bukkit/BountyGui`, click listener | MockBukkit inventory tests | JUnit + MockBukkit |
| 6 | Wiring | `BountyBoardPlugin`, `plugin.yml`, `config.yml` | plugin-load and end-to-end MockBukkit test | JUnit + MockBukkit |
| 7 | Live check | none | bot scenario on the Paper VM (`spawnloft-test`): post, kill, claim, expiry, GUI | Claude review |

## After the tests pass

1. **Held-out audit.** An independent agent writes extra tests from `DESIGN.md` alone (never shown to Qwen); failures go back in as read-only tests,
   as in the Tally run.
2. **Live server check** on `spawnloft-test` (Paper 1.21.11) with the SpawnLoft bot harness.
3. **Claude's review** of the code for threading, API misuse (deprecated or removed methods), event handling and anything the tests cannot see.

## What we measure

Rounds per task, model time, which failure classes recur, what the held-out audit finds, and what only the live server reveals.

## Environment (done)

The `llm-test` container has OpenJDK 21 and Gradle 8.14.3; a smoke project (Paper API 1.21.11, MockBukkit 4.116.3, JUnit 5) builds and runs a
command test in about 22 seconds. Workspaces live under `/srv/bounty/` in the container; the repo is `S:\VMs\bounty-board` (own git repo, to be
created private on GitHub at the first commit).
