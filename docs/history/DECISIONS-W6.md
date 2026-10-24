# W6 decisions: backup, restore files, restore note

Stream W6, 2026-10-19. Module `:core:backup`, built against product spec section 5.10 and 5.13
(after gate R0) and the fakes in `:testing:fixtures`. No frozen signature changed; no RFC.

## What is in the module

| Class | Implements | Notes |
| :- | :- | :- |
| `DocumentSnapshotStore(docs, keep = 20)` | `SnapshotStore` | gzip JSON through `SnapshotCodec` at `tvs/<tvId>/snapshots/<takenAtMs 13 digits>-<REASON>-<contentHash>.json.gz` |
| `ZipRestoreBundleWriter(renderer, clock, appVersion, profiles?, zone)` | `RestoreBundleWriter` | the ARCHITECTURE 5.4 zip |
| `RestoreBundleReader` | | checks a zip and decodes it (for the round trip now, for import in 0.2) |
| `DefaultTvRestoreFiles(clock, zone)` | `TvRestoreFiles` | `restore-point.json` schema 1 and `restore.txt` (D44) |
| `PlainRestoreNote` | `RestoreNote` | D47 |
| `RestoreCommands(renderer)`, `RestoreScripts`, `ChangeSummary`, `ChangeJson` | | undo steps, the laptop files, the net effect of the changes, the ChangeRecord codec |

## Decisions

- D-W6-1 The undo lines come from the app's `OpRenderer` (`inverse`, then `render`), injected
  into `ZipRestoreBundleWriter`. `:core:backup` builds no shell command itself, so `commands.txt`,
  `restore.sh` and `restore.ps1` carry the phone's own lines (ARCHITECTURE 5.4 "same lines",
  SECURITY 3.4 rule 5). An undo that is refused, has no prior, needs a transfer, or has no inverse
  because something cannot be rebuilt (an app that was on the TV before us and is gone, a file
  that existed before) becomes a "not possible from a computer" entry with the reason.
- D-W6-2 (rewritten in T5 after R5-W6 M1 and M2) The laptop files undo in the planner's order.
  `RestoreCommands` takes `UndoOrder.undoOrder(changes)` from `:core:ops`, the helper W3's
  `DefaultPlanner.undoEverything` uses: one record per item (a package's state, a setting, the
  home screen, an app we installed, a file we wrote), the EARLIEST, so each item goes back to its
  state before this app first touched it; items in phases: package states, settings, the home
  screen, our apps, our files (so the stock launcher is switched on before HOME moves, and our
  launcher is removed only after HOME is back); inside a phase the item first touched most
  recently goes first. Each record goes through `renderer.inverse`. A `null` inverse of a package
  state or setting change is a change that left the item as it was (W3: already in that state;
  `adb_enabled` and `adb_wifi_enabled` are never switched back on): no step. What the records
  already took back has no step either: an app we installed and later removed again (no
  uninstall, no "cannot be reinstalled" entry), any step aimed at a package the records leave
  uninstalled (its state, a home screen in it, files in its folder), a file we wrote and later
  deleted. Unlike the phone, a script cannot read the TV, so it does not skip an item the TV
  already shows in its first state; that line repeats the state and changes nothing. On a TV that
  still shows every change the default script runs exactly the lines of the phone's Undo
  everything that keeps the launcher, and with `--remove-apps` those of the one that removes it
  (less the file in the launcher's folder, which goes with the app); tests
  `theStepsAreThePhonesUndoEverything` and `whereThePhoneSkipsAnItemAlreadyBackTheScriptRepeatsIt`
  hold this with the real planner and `ShellRenderer`. Left out as before: reads, settles, the
  HOME watchdog and its confirm, restarts, `debug_off`, reboot, `mkdir`, and our own files under
  `/sdcard/cleanyourtv` (rewritten by every plan).
- D-W6-3 TV identity in the scripts without a serial in the file: `restore.sh` and `restore.ps1`
  compute `TvId` on the laptop (first 16 hex of sha256(`ro.serialno` + `ro.product.device`), the
  D37 formula) and stop with exit 3 before any write when it differs, or when no sha256 tool exists
  (`sha256sum`, `shasum`, `openssl`), unless `--allow-other-tv` (`-AllowOtherTv`) is passed. The
  adb device serial is passed with `-s`, or `--host IP[:PORT]` connects first (port 5555 default).
- D-W6-4 Uninstall steps (our launcher, modules) run only with `--remove-apps` (`-RemoveApps`); in
  `commands.txt` they are commented with a `# remove-app: ` prefix. This matches the app's default
  "keep it installed as an ordinary app" (UX 4.13). Other options: `--dry-run` prints the lines
  without calling adb. A failed step is reported and the rest run; exit 1 at the end.
- D-W6-5 Free text (nickname, room, the model the TV reports, profile notes) never reaches a script
  as code: printable ASCII on one line, inside a comment or a quoted literal only. TV lines are
  double quoted when nothing in them is special inside double quotes, else single quoted.
- D-W6-6 `snapshot.json` in the zip is the BASELINE with `facts.serialLast4` blanked and the hash
  recomputed (manifest `"redacted": ["snapshot.facts.serialLast4"]`). No file holds the serial,
  the address, the subnet or Wi-Fi data. `restore-point.json` (on the TV's shared storage) also
  leaves out the nickname; `restore.txt` names the TV by nickname and model.
- D-W6-7 Snapshot store: the first BASELINE of a TV is never pruned; later BASELINEs count with
  the rest. `save` refuses a snapshot whose `contentHash` is not `SnapshotCodec.hash`, is a no-op
  for a known hash, and prunes to `keep` (20) afterwards, so the store stays bounded even if no
  caller prunes.
- D-W6-8 The zip is deterministic for the same inputs: fixed entry order (manifest first), entry
  time = `createdAtMs`. The reader accepts only the known entry names, each once, at most 8 MiB
  each and 32 MiB in all, and checks every size and sha256 against the manifest both ways.
- D-W6-9 The restore note lists friendly names (`FactsTable`, else the package name), sorted, at
  most 40 then "and N more, listed on the TV under Files > cleanyourtv". The home screen line
  appears when HOME was changed; "Keep hidden is always a safe answer" when a module was installed.
  `dateText` is formatted by the caller in the phone's locale.

## Tests

`./gradlew :core:backup:check`. The shell-interpreter test (`RestoreShellTest`) takes `restore.sh`
from an exported and re-read zip and runs it under every POSIX shell found (dash as `/bin/sh` in
the forge container, bash in POSIX mode, and others when installed) against a stub `adb`
(`src/test/resources/stub/adb`) that keeps the fake TV as files, refuses any line that is not
constant words and single-quoted tokens, tokenises lines like the TV's sh and logs every call. It
checks: the baseline comes back exactly with `--remove-apps`, and plus our launcher without it;
another TV, or no sha256 tool, stops with exit 3 and no write; `-s` reaches every adb call;
`--host` connects first; `--dry-run` calls nothing; a failing step is reported while the rest
run; a hostile nickname and model stay inert under `sh -n` and a real run; after the R5-W6 M1
flow (setup, the TV's own home screen, the new one removed) the script ends at the baseline with
exit 0 and sends no line naming the removed launcher. All of it with W3's `ShellRenderer`. `TvRestoreFilesTest`
runs the pinned upstream `tools/restore.sh` itself (bash) on our `restore.txt`.

## Open for other streams

- W3 (done in T6, R5-W6 M2): every test runs with W3's real `ShellRenderer`; the stand-in
  `ReferenceRenderer` is deleted. The stub adb takes the real quoting (`'pm' 'enable' ...`).
- W2: `restore.sh` relies on the D37 formula exactly (no separator, values as `getprop` prints
  them without the line break). If the session trims or normalises differently, tell W6.
- W8: wire `DocumentSnapshotStore` over `FileStore`, `ZipRestoreBundleWriter` with the app's
  renderer, clock, version and profile source, and `RestoreBundle.fileName` for the SAF name. The
  WRITE_RESTORE_POINT blobs are `DefaultTvRestoreFiles` output stored in the BlobStore.
