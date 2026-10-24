# Working in this repo (agents and people)

Nine workstreams build the app in parallel against the frozen interfaces (product spec section 5,
as fixed by gate R0) and the fakes in `:testing:fixtures`. Two rules hold everything together.

## Rule 1: you edit only the modules your stream owns

| Stream | Owns |
| :- | :- |
| W1 Foundation and fakes | `build-logic/`, `settings.gradle.kts`, `gradle/` (catalog and wrapper), `:core:model` (including the `core.session`, `core.discovery` and `core.store` seams and `SnapshotCodec`), `:testing:fixtures`, `fixtures/`, `third_party/` pin, `.github/workflows/pr.yml`, `checkModuleGraph`, the APK size gate, `scripts/build-on-forge.sh`, `scripts/import-mitv-capture.py` |
| W2 ADB transport and signer | `:adb:wire`, `:adb:session`, `:testing:fake-adbd`; in `:android:platform`: `KeystoreAdbSigner`, `SecretBox`, `PlainSocketFactory` |
| W3 Ops, planner, executor, journal, watchdog | `:core:ops` (renderer, `Protected`, `ConfigTransport`), `:core:planner`, `:core:engine` (executor, journal, `FileOverlayTransport`) |
| W4 Probe, profiles, config codec | `:core:probe`, `:core:spec` (`DebloatTxtParser`, the build-time profile converter, `FactsTable` data, `DockRanking`, `LauncherConfigCodec`, `ThemeSource`), the sure-set generator |
| W5 Trust, registry, downloads, launcher pin | `:core:trust`, `:core:registry`; in `:android:platform`: `UrlConnectionFetcher`, `FileBlobStore`, `PackageManagerApkInspector`; the bundled launcher APK and its pin, the seed index and keys |
| W6 Backup, restore files, restore note | `:core:backup` (and the `SnapshotStore` implementation) |
| W7 Design system and components | `:ui:design` |
| W8 Flows, screens, Android glue | `:core:flows`, every `:feature:*`, `:android:work`, `:app`; in `:android:platform`: `NsdDiscovery`, `AndroidCredentialGate`, `FileStore`, network state |
| W9 Release, CI, e2e, perf, runbook | `release.yml`, `nightly.yml`, `:e2e`, `:benchmark`, the reproducible-build container, the internal APK build, the real-device runbook, the redaction check script |

`:android:platform` is shared by W2, W5 and W8: each edits only its own files there.

W1 wrote the section 5 declarations into every module on day 1 (files headed `FROZEN
INTERFACE`), with `TODO("Wn")` bodies where a body was needed to compile. Those files belong to the
module's owning stream from then on: the owner replaces the bodies, never the signatures.

Need something from another stream's module? Ask through an RFC (rule 2) or a note to that
stream's owner; do not patch it yourself, not even "just a small fix".

## Rule 2: interface changes go through an RFC

Names and signatures in section 5 are binding. To change one (add, remove, rename, move, change a
type or a default):

1. Write a one-paragraph RFC: what changes, why, who is affected, the smallest alternative. Put it
   in your stream's `DECISIONS-Wn.md` under "RFCs" (see `DECISIONS-W1.md` for the shape).
2. A Fable reviewer signs it off at gate R0 before it merges.
3. The interface owner applies it to the frozen file, and W1 updates the fakes in
   `:testing:fixtures` in the same change.

Not an RFC: adding fields to the types section 5 marks as growable (`WizardDraft`, `StopCard`,
`DoneSummary`, `RestartState`: "W8 may add fields, never remove"), private helpers, bodies.

## Other house rules

- Module edges follow `build-logic/.../ModuleRules.kt`; `checkModuleGraph` fails the build on an
  upward edge, a feature reaching past `:core:flows`, `:core:model` and `:ui:design`, a test module
  in a main configuration, an Android plugin in `:core:*` or `:adb:*`, or a third-party dependency
  not on the module's allowlist. Changing the rules is an RFC.
- Versions live only in `gradle/libs.versions.toml`; stable releases only.
- Pure Kotlin modules have no Android imports. Kotlin comments use `//`.
- `ShellLine` is built only by `:core:ops` and `:core:probe` (`@RawShell`); `GatePass` only by gate
  implementations (`@GateIssuer`) and `FakeCredentialGate`.
- No em dashes and no double hyphen sequences in prose, comments or commit messages.
- Commits: small, plain messages, no co-author trailers, no tool banners or session links.
- Android builds run on forge with `scripts/build-on-forge.sh`, never on the coordinating machine; never leave a
  container running there.
