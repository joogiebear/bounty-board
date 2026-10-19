Task 2: file persistence. Read DESIGN.md, section "Persistence", and follow its house rules.

The test for this task already exists and is read-only: src/test/java/dev/joogiebear/bountyboard/core/FileBountyStoreTest.java (plus the earlier
stage's tests, which must keep passing). It is the contract. Write the code so it passes. Never edit, add, skip or delete a test.

Implement `FileBountyStore` in src/main/java/dev/joogiebear/bountyboard/core/FileBountyStore.java (it exists as a stub with the right constructor and
methods). Use `org.bukkit.configuration.file.YamlConfiguration` for the format.

Notes:
- Save atomically: write a temp file in the same directory, then move it over the target with `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.
  Create parent directories. No temp file may be left behind.
- Store instants as epoch milliseconds (long) and UUIDs as strings; keep nulls as absent keys. Names and notice texts can contain YAML-sensitive
  text (`yes`, `123`, `: `, quotes, leading spaces): they must come back exactly, so store them as strings and read them back as strings.
- `load` never throws: a missing file gives an empty `StoreData` with `nextId` 1; a file that cannot be parsed is renamed to
  `<file name>.corrupt-<epoch millis>` in the same directory and an empty `StoreData` is returned.
- Keep the order of the bounty list and the notice list.
