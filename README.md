# Bank Tab Shortcuts

A narrow RuneLite plugin for Chromium-style keyboard navigation across native Old School RuneScape
bank tabs.

## Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+0` | Open All (all items), even without numbered tabs |
| `Ctrl+1` through `Ctrl+9` | Open that numbered bank tab when it exists |
| `Ctrl+Tab` | Open the next existing numbered bank tab, wrapping at the end |
| `Ctrl+Shift+Tab` | Open the previous existing numbered bank tab, wrapping at the start |

All is tab 0. Cycling still visits existing numbered tabs; from All it enters tab 1 going forward
or the highest existing tab going backward. The plugin ignores every shortcut while the bank is
closed, while the bank's item area is hidden, or when the requested numbered tab does not exist.
Number shortcuts work from the top number row and the numeric keypad (with Num Lock on).
Version `1.0.2` also recognizes the original number keys when RuneLite's Key Remapping plugin
rewrites them to F1-F9, so Remap F-keys can remain enabled.

## Optional Tab Numbers

Version `1.1.0` adds **Show tab numbers** in the plugin settings (off by default). Enable it for small
labels at the top-left of native tabs: `0` on All, then `1` through `9` on existing tabs. Adjust
**Number color** (including opacity) and **Number size** (`8`–`16`, default `10`). Changes apply
without restarting. Labels use a muted color and a contained one-pixel shadow; they never handle
mouse input or replace tab icons. Missing tabs and the New tab button have no label.

The renderer follows each native widget's canvas bounds, clips text inside the tab, and stays
hidden when the bank or native tabs are hidden. It does not number Bank Tags sidebar tabs.

## Remembering The Selected Tab

Version `1.1.1` remembers the last successful keyboard selection when you close and reopen the
bank, including All and either cycling direction. A later native tab click takes precedence.
Missing tabs are not restored, and Bank Tags can still restore its own remembered tag.

This is a local view preference for the current login, not a change to the game's saved tab.
Logging out, hopping worlds, losing the connection, restarting RuneLite, or disabling the plugin
clears it; the game's last clicked tab then applies. No simulated click or game action is sent.

## Safety And Scope

The plugin registers a RuneLite key listener and optional overlay, reads the native bank interface and tab-count
varbits, selects a native tab, closes an active bank search input, and asks the bank's own widget
listener to rebuild the item view. RuneLite stores the three display settings through its own
configuration API. The plugin does not send network requests, read or write files directly, inspect
credentials or account profiles, synthesize operating-system input, invoke menu or packet actions,
or automate gameplay. Navigation begins with an explicit user keypress inside the open bank;
reopening the bank restores that chosen view locally.

If RuneLite exposes the bank without its native rebuild listener, the plugin logs
`BTS_BANK_REBUILD_LISTENER_MISSING` and leaves the current tab unchanged. Unexpected navigation
failures are logged as `BTS_NAVIGATION_FAILED` with the requested tab.
Bank-state refresh failures log `BTS_BANK_STATE_FAILED` and disable shortcut interception until
the next successful client tick.
Unexpected restore failures log `BTS_TAB_RESTORE_FAILED` and discard the local preference.
Close-time state capture failures similarly log `BTS_TAB_CAPTURE_FAILED` and discard it.

All bank widget and varbit access runs on RuneLite's client thread. Each client tick publishes an
immutable tab-availability bitmask for the AWT keyboard listener; the listener only consumes valid
shortcuts from that snapshot and queues a request. The queued request checks the bank and tab again
on the client thread. Disabling the plugin invalidates queued requests, including across re-enable.

## Build And Verify

The project pins RuneLite `1.13.1`, Gradle `8.10`, wrapper/distribution checksums, and strict Gradle
dependency verification. Its only runtime dependency is RuneLite.

```powershell
pwsh -NoProfile -File .\scripts\verify.ps1
```

The verifier runs the navigation and shortcut matrix against source classes and again against the
packaged JAR, checks source for forbidden capability patterns and the JAR's class allowlist,
exercises scanner mutation controls, confirms there are no runtime dependencies, checks the Git
diff, and proves two clean builds produce the same JAR.

### Issue 1 Regression (2026-10-14)

[Issue 1](https://github.com/iamthecodeDECODEDORG/runelite-bank-tab-shortcuts/issues/1) reported
`IllegalStateException: must be called on client thread` in `startUp()` and `keyPressed()`.
Version `1.0.1` removes all game-state access from those entry points. The former proxy tests did
not enforce thread ownership; the corrected tests reject every off-thread client/widget access,
reproduce both failures on the original implementation, and dispatch shortcuts through the real
RuneLite `KeyManager` on AWT's event-dispatch thread.

| Boundary | Required behavior | Regression coverage |
|---|---|---|
| Startup with bank open | Register listener without off-thread widget reads | Lifecycle on AWT, bank discovered on next client tick |
| Keyboard dispatch | Consume using cached data; all game reads queued | Every numbered shortcut and both cycle directions |
| Rapid input | Compute each target from current client-thread state | Two queued cycle requests advance twice |
| Bank state changes | Recheck widget visibility and tab existence | Closed, hidden, reopened, and disappearing tabs |
| Disable/re-enable | Discard requests from the previous activation | Queued old request cannot replay |
| Missing rebuild listener | Log `BTS_BANK_REBUILD_LISTENER_MISSING`; preserve tab | No tab mutation or rebuild |
| Shipped artifact | Run the same contract against production JAR classes | Separate `packagedTest` task asserts JAR code source |

There is no existing plugin state to migrate. Plugin Hub users receive the update after the manifest
update is accepted. These tests exercise the threading and navigation contract using a simulated
bank; a logged-in live-bank test remains a separate check.

The [tab-reopening regression report](docs/tab-memory-regression.md) records the native script
path, server-resync reproduction, lifecycle coverage, and remaining live-client validation.

For a private RuneLite development client:

```powershell
.\gradlew.bat run
```

Stock RuneLite does not sideload local JARs. Normal Jagex Launcher use requires installation from
the official RuneLite Plugin Hub.
