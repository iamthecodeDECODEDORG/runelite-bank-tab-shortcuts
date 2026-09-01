# Bank Tab Shortcuts

A narrow RuneLite plugin for Chromium-style keyboard navigation across native Old School RuneScape
bank tabs.

## Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+1` through `Ctrl+9` | Open that numbered bank tab when it exists |
| `Ctrl+Tab` | Open the next existing numbered bank tab, wrapping at the end |
| `Ctrl+Shift+Tab` | Open the previous existing numbered bank tab, wrapping at the start |

The all-items view is intentionally not numbered. Cycling from all items enters tab 1 going forward
or the highest existing tab going backward. The plugin ignores every shortcut while the bank is
closed, while the bank's item area is hidden, or when the requested numbered tab does not exist.

## Safety And Scope

The plugin only registers a RuneLite key listener, reads the native bank interface and tab-count
varbits, selects a native tab, closes an active bank search input, and asks the bank's own widget
listener to rebuild the item view. It does not send network requests, read or write files, inspect
credentials or account profiles, synthesize operating-system input, invoke menu or packet actions,
or automate gameplay. Every action begins with an explicit user keypress and only works inside the
open bank interface.

If RuneLite exposes the bank without its native rebuild listener, the plugin logs
`BTS_BANK_REBUILD_LISTENER_MISSING` and leaves the current tab unchanged. Unexpected navigation
failures are logged as `BTS_NAVIGATION_FAILED` with the requested tab.

## Build And Verify

The project pins RuneLite `1.12.37`, Gradle `8.10`, wrapper/distribution checksums, and strict Gradle
dependency verification. Its only runtime dependency is RuneLite.

```powershell
pwsh -NoProfile -File .\scripts\verify.ps1
```

The verifier runs the navigation and shortcut matrix, builds the plugin, scans source and packaged
classes for forbidden capabilities, exercises scanner mutation controls, confirms there are no
runtime dependencies, checks the Git diff, and proves two clean builds produce the same JAR.

For a private RuneLite development client:

```powershell
.\gradlew.bat run
```

Stock RuneLite does not sideload local JARs. Normal Jagex Launcher use requires installation from
the official RuneLite Plugin Hub.
