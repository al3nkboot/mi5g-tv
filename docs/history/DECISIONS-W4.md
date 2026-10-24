# W4 decisions: probe, profiles, config codec

Stream W4, 2026-10-19. Owns `:core:probe` and `:core:spec`. Built against section 5 as frozen at
R0; the one frozen change is RFC-W4-1 (`docs/rfcs/W4.md`), accepted and applied at integration.

## Launcher config codec (`LauncherConfigCodec`, F8)

- D-W4-1 Semantics follow upstream `Config.java` at main `1bbf0c9` (key set of the pinned
  `7205769` plus `input_action` and `module_cards`). RFC-W4-1 made those two keys fields of
  `LauncherConfigDoc` and `EffectiveConfig` (built-in values `""` and `true`), decoded and
  encoded at their `Config.java` position like every other key. The launcher built from the pin
  ignores them.
- D-W4-2 The launcher parses with Android's org.json, which is lenient (comments, single quotes,
  unquoted names, `=` and `=>`, `;`, hex and octal, last duplicate wins, trailing text ignored, a
  second BOM eaten by the tokener). `AndroidJsonReader` is a line-by-line port of AOSP
  `JSONTokener` (pie-release, identical in main bar annotations) plus `JSONObject.put`'s
  NaN/infinity check, so the phone reads a hand-edited file exactly as the TV does. It refuses
  nesting deeper than 512 (the TV would recurse); that is the only place the codec is stricter.
- D-W4-3 `decode` answers "what will the TV show": `Rejected` exactly when the launcher ignores
  the whole file (over 64 KB, unreadable, top level not an object) or would crash; otherwise `Ok`
  with every key the launcher ignores left null and a warning. Upstream finding: an empty or
  blank `background_color` makes `Color.parseColor` throw `StringIndexOutOfBoundsException`,
  which `Config.color` does not catch, so Home crashes in `onCreate`. The codec reports it as
  `Rejected("... would crash ...")` and `encode` refuses it. Worth sending upstream with the
  overlay disclosure (D15).
- D-W4-4 `dock_apps` and `hidden_apps` entries that are not package names are dropped with a
  warning (the doc type is `List<PackageName>`); they can never match an app on the TV, so the
  visible result is the same. The differential test filters upstream's lists the same way.
- D-W4-5 `effective` re-applies `Config.java`'s ranges and colour parsing, so a doc built in code
  previews as the TV will show it. `EffectiveConfig.wallpaper` means "shows wallpaper.jpg when
  that file is there" (`builtInDefaults.wallpaper = true`).
- D-W4-6 `encode`: `Config.java` key order, two-space indent, lists on one line, UTF-8 without BOM,
  final newline; control characters and lone surrogates escaped; refuses out-of-range numbers,
  untrimmed strings, colours the launcher cannot read, a modelled key inside `unknown`, a carried
  value that is not plain JSON (for example `JsonUnquotedLiteral`) and anything over 64 KB.
- D-W4-7 Differential test: instead of Robolectric (the spec's wording), the real upstream
  `Config.java` is compiled unchanged on the JVM in `:core:spec`'s tests against the vendored AOSP
  org.json sources (Apache-2.0, the same classes Robolectric ships), AOSP `Color.parseColor`
  copied verbatim, and three-method stubs for `Context`, `Resources` and `Log`. Both upstream
  versions run: the latest main (vendored at `core/spec/src/test/java/com/gathin/tvhome/Config.java`,
  MIT) and the pinned one (copied from the submodule at build time into
  `com.gathin.tvhome.pinned`). Resource defaults come from each version's `config.xml`.
  Coverage: the three theme fixtures plus 19 hand cases, 10,000 generated files against the
  latest main, 3,000 against the pin, 2,000 encoded docs (round trip plus upstream read), and
  10,000 files through both org.json and our port value for value.

## Profiles (`:core:spec`, D26, D28)

- D-W4-8 `debloat.txt` grammar: a group is a run of package lines; its title is the first line of
  the comment block directly above (decoration stripped); an indented comment under a package
  continues its note; commented-out packages are the owner's keeps and are not in the profile.
- D-W4-9 Header facts and group categories are curated data, `profile-meta.json` (written by a
  Sonnet pass over all 26 headers, reviewed here): match strings, platform, stock launcher,
  Live TV app, setupwraith, quirks, never-disable list, tested info, and a category plus "owner's
  choice" flag for every group title, with per-package overrides for mixed groups. The parser
  falls back to header keywords for a folder without an entry (the four examples main added
  after the pin parse that way in a test). The converter fails the build when a title has no
  entry, so upstream drift is caught on the next pin bump.
- D-W4-10 Plan groups use one fixed set of categories (ads, tracking, preloads, brand extras,
  shop and demo, other; then streaming, voice, casting, screensaver, accessibility, software
  updates as `ask`; `home` as `keep`). A firmware updater is always "Software updates" (`ask`, D22)
  wherever upstream filed it. Owner's-choice groups of a disable category become `<id>-optional`
  `ask` groups. Stock launchers and setupwraith never sit in a group: they go to
  `stockLaunchers` and `setupWraith` (plus the quirk) for the takeover section.
- D-W4-11 Never-disable is the union of the curated list and the header's own "Keep" lines; a
  package that is also in a disable group is kept protected and listed in the feedback report.
- D-W4-12 Stock launcher components: `tvlauncher/.MainActivity` is verified on the reference
  TV; `launcherx/.home.HomeActivity`, `leanbacklauncher/.MainActivity` and the Fire TV
  `.ui.HomeActivity_vNext` follow the platform builds. W3 should accept a claimant whose package
  is on the allowlist even if the class differs.
- D-W4-13 Build-time conversion: `:core:spec:convertProfiles` (a `JavaExec` of this module's own
  `ProfileConverter`) writes `profiles.json` (schema 1, profiles plus sure sets) into generated
  resources; `bundleThemes` copies the three upstream themes. Runtime reads only those resources.
- D-W4-14 Exact match: model or device (case-insensitive), then the profile's brand (or the
  manufacturer), SDK range and fingerprint prefix when set. SDK bounds are the header's Android
  version on both ends, so a TV updated past its profile's firmware gets the brand sure set, not
  an untested exact profile.
- D-W4-15 Sure set (D26): packages in a default-disable group in at least three profiles of the
  brand and in no profile's never-disable list; each placed in the group most of those profiles
  use. Brands that qualify at the pin: TCL and Xiaomi.
- D-W4-16 Dock ranking (D27): fixed regional tables (IN, US, GB, CA, AU, then a global list).
  When the TV has inputs and its Live TV app is installed, Live TV keeps the fourth slot; that
  gives section 4's "Netflix, YouTube, Prime Video, Live TV" on the reference TV, where the
  literal UX 2 order would have put Hotstar fourth.
- D-W4-17 Facts table: `facts.tsv` (package, name, description, category) covers every package in
  every pinned example (360), every keep, launcher and Live TV package, and the dock table apps.
- D-W4-18 Themes: ids `default`, `aurora`, `six-columns`; a wallpaper is a `BlobRef` with path
  `bundled:tv/cleanyour/core/spec/themes/aurora/wallpaper.jpg`, readable with
  `BundledThemeSource.bytes(blob)` (hash checked). The Default theme carries upstream's default
  dock and hidden lists; the planner should overlay the user's dock on whichever theme applies.

## Probe (`:core:probe`, D40, F3)

- D-W4-19 A full snapshot is seven shell calls and one sync pull: `FACTS` (getprop, meminfo, the
  GMS path), `pm list packages -f`, `pm list packages -d`, the grep of `dumpsys package packages`
  (flags, version, user 0 state), `HOME` (query-activities then resolve-activity), `INPUTS`
  (`dumpsys tv_input` then `dumpsys hdmi_control`), `SETTINGS` (secure then global), then a pull of
  the overlay `config.json`. `pm` lists stay one per call (upstream saw chained `pm` stop early);
  if the HOME script loses its second half the prober asks for the resolution alone. Scripts are
  constants with marker lines, pinned by `golden-scripts.txt`.
- D-W4-20 Lines with an argument (`sha256sum`, the installed APK hash, `pgrep`) take only a typed
  value whose grammar admits no quote and single-quote it. `installedSha256` resolves the APK path
  on the TV, so TV output is never quoted back into a command. `watchdogAlive` uses
  `pgrep -f '[c]leanyourtv-rollback-T'` so the shell running it does not match itself, and falls
  back to `ps -A -o ARGS` where toybox has no pgrep.
- D-W4-21 `PackageState`: enabled from `pm list packages -d` (the ground truth) refined by user
  0's `enabled=` code (3 is `DISABLED_USER`, 2 and 4 `DISABLED`, 1 `ENABLED`, 0 `DEFAULT`);
  `system` and `persistent` from `pkgFlags`, path as a fallback; `tvInput` from the TIF inputs;
  `signer` stays null (Android 9's dumpsys prints only a 32-bit signature hash; W5's inspector
  owns digests); `label` null (friendly names come from the facts table); `installedByUs` false
  (the phone's records know that, not the TV).
- D-W4-22 The framework package `android` is reported like any other package since RFC-W1-2's
  ruling admitted the literal in `PackageName` (107 packages on the reference TV). The prober still
  builds names with a guarded constructor, so a name the grammar refuses is skipped.
- D-W4-23 `installRefusal` is the one probe call that writes: mkdir, push the no-op APK, `pm
  install -r`, `pm uninstall` on success, `rm` always. The bundled no-op APK (W5) must have the
  package name `tv.cleanyour.noop`. `INSUFFICIENT_STORAGE` raises `Device.NotEnoughSpace`; any
  other failure counts as refusal.
- D-W4-24 Fixtures: W1's capture for what it recorded; the dumps it did not record are
  synthesized in the Android 9 shapes, consistent with that capture, plus Android 12 to 14 shapes
  (`core/probe/src/test/resources/probe/README.md`). They should be replaced by real captures at
  the first device session.

## Data feedback

`core/spec/upstream-feedback/protected-in-disable-groups.txt` lists packages upstream examples put
in a default-disable group that the app protects by name (PLAN 6.5, SECURITY 8.5) or that the same
profile's header says to keep. `ProtectedReportTest` regenerates it and fails when it drifts.
