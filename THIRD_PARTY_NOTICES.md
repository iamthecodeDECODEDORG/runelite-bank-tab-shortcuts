# Third-Party Notices

This plugin uses RuneLite's public plugin API and follows the bank rebuild and key-listener patterns
from the RuneLite client. No RuneLite source code is copied into this project.

- Project: RuneLite
- Source: https://github.com/runelite/runelite
- Reference commit checked 2026-09-01: `e1afd4502d3819700374bd045ab4dfcf6f9d82ec`
- License: BSD 2-Clause

The Gradle wrapper is distributed by the Gradle project under the Apache License 2.0.

## Native Tab Layout Reference (2026-10-14)

The native tab child mapping (All at child 10; numbered tabs at children 11–19) was checked against
[Bank Tab Names](https://github.com/Psyda/bank-tab-names/blob/a8cd8e90a4cdd250cee3b1d4d45e48d0ff8cc9d4/src/main/java/psyda/banktabnames/BankTabNamesPlugin.java),
BSD 3-Clause, as an API/layout reference. No source code was copied. This plugin uses RuneLite's
existing overlay, font, and configuration APIs rather than adding a runtime dependency.
