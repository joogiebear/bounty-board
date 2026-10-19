Task 3: settings, formatting and messages. Read DESIGN.md, sections "Config, messages and formatting" and "Commands and messages" (the message table), and follow its house rules.

The tests for this task already exist and are read-only: src/test/java/dev/joogiebear/bountyboard/core/BountySettingsTest.java and FormatTest.java, and
src/test/java/dev/joogiebear/bountyboard/bukkit/MessagesTest.java (plus the earlier stages' tests, which must keep passing). They are the contract.
Write the code so they pass. Never edit, add, skip or delete a test.

Implement the three stubs: `core/BountySettings.java` (`fromConfig`), `core/Format.java` (`amount`, `duration`) and `bukkit/Messages.java`.

Notes:
- `Messages`: keep the full table of default messages from DESIGN.md in one place (a map), exactly as written, including `&` colour codes. `render` replaces
  `{name}` placeholders in the text first, then converts `&` codes with `LegacyComponentSerializer.legacyAmpersand()`. A key missing from both the config and the
  defaults renders as the plain text `<missing:KEY>`.
- `BountySettings`: read each key with its own default and range; warnings use the exact texts from DESIGN.md and come in key order, then the cross-checks.
- `Format.amount` must not depend on the default locale (use `Locale.ROOT` or build the string yourself). `Format.duration` uses whole seconds, rounded down.
