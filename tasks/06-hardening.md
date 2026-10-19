Task 6: close the gaps between the finished plugin and DESIGN.md. Read DESIGN.md again: it was updated, especially the rows for `admin reload`, the section "Persistence", the rule about whole numbers in the command section, and "Wiring".

The new tests for this task already exist and are read-only: the `Held*Test.java` files and the helpers `HeldBase.java` and `HeldSupport.java` under
src/test/java (plus all the earlier stages' tests, which must keep passing). They are the contract. Never edit, add, skip or delete a test.

A review of the finished plugin against DESIGN.md found these differences. Fix the code in the existing classes (`BountyBoardPlugin`, `BountyService`,
`FileBountyStore`, `BountyCommand`), without rewriting parts that already work:

1. **The config file is never written.** On a first start the plugin folder must contain `config.yml` (see "Wiring").
2. **`/bounty admin reload` must apply the new config to the running plugin**, not only the messages: the settings the service uses (limits, durations, tax,
   cooldown, refund rules, purge days) and `starting-balance` for players the economy has not seen yet. Existing bounties and balances are kept. The
   `BountyService` constructor signature is fixed by the earlier tests, so add whatever method you need to it.
3. **A corrupt data file must be moved aside on a real running server**, exactly as the section "Persistence" says (renamed to `<name>.corrupt-<epoch millis>`,
   empty data returned, store usable afterwards). A helper that swallows parse errors will make a corrupt file look like an empty one and the next save
   would overwrite it, so the parse failure must be detected.
4. **Whole numbers that do not fit an `int` must not be truncated.** `hours` 4294967297 must not become 1 hour, and `cancel 4294967297` must not cancel bounty #1:
   follow the rule in the command section of DESIGN.md.

No React, no new dependencies. Keep the code style of the surrounding classes.
