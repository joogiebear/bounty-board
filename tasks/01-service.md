Task 1: the bounty rules. Read DESIGN.md, especially the house rules and the section "BountyService", and follow it exactly.

The tests for this task already exist and are read-only: src/test/java/dev/joogiebear/bountyboard/core/BountyServiceTest.java and TestSupport.java.
They are the contract. Write the code so they pass. Never edit, add, skip or delete a test.

Implement `BountyService` in src/main/java/dev/joogiebear/bountyboard/core/BountyService.java. The class already exists as a stub with the exact
public signatures: keep them, replace the `throw new UnsupportedOperationException` bodies. The records, enums and interfaces in the same package
(Bounty, Notice, StoreData, Settings, Page, CreateResult, ...) are read-only and are shown to you; use them as they are.

Notes:
- This is plain Java 21 with no Bukkit imports; time comes only from the injected `Clock`.
- Keep the state in memory (a list or map of bounties, a list of notices, the next id) and call `store.save(...)` exactly once after every
  operation that changes something, never after a read. Save a snapshot (copies), not your live collections.
- Money is `long`; percentages round down with integer arithmetic.
- The kill cooldown is kept in memory only.
- Write small private helper methods; do not put all the rules into one long method per operation.
