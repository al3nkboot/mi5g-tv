# RF review: 60 new profiles from upstream v2.4.0 (branch profiles63)

Reviewer: review gate (Opus standing in for Fable), 2026-10-20. Scope: branch `profiles63` in
`cyt-wt/profiles63`, four commits on main `372df59` (90764c8 Xiaomi, 66dcd71 Sony and TCL, 68ec065
Philips Hisense Panasonic Skyworth BenQ Hitachi Haier, 8abce6e the rest; `git diff 372df59..profiles63`,
7 files), against the source lists `third_party/cleanyourtv/examples/<folder>/debloat.txt` at v2.4.0
(69c93b6), PRODUCT-SPEC (D22, D25, D26, D28, D3 of launcher 2.4), SECURITY C4 and 8.5, the planner's
protection logic (`Denylist`, `Protected.isProtected`, `protectedReason`, `takeover`, the D3 Wi-Fi
guard), `BundledProfileSource.score`, `SureSetGenerator`, `SetupMachine.replan`, and the 30 profiles
already shipped. Goal applied: nothing a person could not easily undo, and nothing that breaks Wi-Fi,
input, updates, the Play Store, HDMI, accessibility or boot is ever default-off.

Method. No JDK on the coordinating machine, so the Kotlin converter was mirrored in Python (scan, keep-list reader old
and new, categories, D22 updater rule, known launchers, setupwraith rule, sure sets). The mirror
reproduces the commit messages' sure-set sizes exactly (Amazon 8, Hisense 8, Philips 10, Sony 27,
TCL 25, Xiaomi 24) and the committed protected-in-disable report, so its readings were trusted. All
seven Xiaomi profiles were read in full against their lists; 36 more were read in full (Sony 7, TCL
family 9, Philips 4, Hisense 4, Panasonic 2, Skyworth 2, Haier 2, Hitachi 2, and every single-profile
brand: AiPro2K, Fire TV Stick, BenQ, CHiQ, JMGO, Kalley, NVIDIA, onn, PRISM+, Thomson, Vu, Wanbo).
All 800 default-off rows across the 60 (248 distinct packages) were scanned by name and note, and
every overlapping pair of match keys across all 90 profiles was enumerated.

## Verdict: APPROVE WITH FOLLOW-UPS

The batch is careful work. Every active line of every list read is in its profile and no
commented-out line leaked; credits and PR numbers match upstream git in all 43 profiles read in full;
updaters, accessibility, voice, casting, streaming, screensavers, TV inputs and USB media players are
ask rows as the rules say; the static denylist and the header keep lists are honoured; the
protected-in-disable report is unchanged and correct; the earlier 30 profiles are byte for byte
unchanged and the parser fixes change only three new profiles' keep lists, each for the better. No
default-off row uninstalls anything, so everything is undoable, and nothing default-off touches Wi-Fi,
the Play Store, system UI, settings, installers, permissions, providers of the platform, webview or DRM.

Nine items must be done before merge (P1 to P9). Three are real breakage on named TVs (P3, P4, P5),
two are input risks the goal forbids (P6, P7), two are home screen plan failures that make a profile
useless (P1, P2), one is matching (P8) and one is facts honesty plus the test run (P9). All are small:
meta edits, one parser rule, and a few lines in `SetupMachine.homePossible`.

## Must do before merge

- **P1** Below SDK 26 there is no home screen switch, no launcher install and no TV curtain; the
  clean-up stays (ruling 5).
- **P2** An exact profile with no known stock launcher gets the clean-up only (xiaomi-mitv-astp1, and the
  shipped stellamore-t950s), instead of a refused plan.
- **P3** philips-55oled707: `com.google.android.tvlauncher` must not be never-disable.
- **P4** panasonic-4ktv-jup and panasonic-th-40hs550k: `com.google.android.leanbacklauncher.partnercustomizer`
  never-disable; haier-android-tv-dvb2k's MediaTek variant an ask row.
- **P5** tcl-uniontv-latin-america: `com.tcl.partnercustomizer` an ask row.
- **P6** `fusion.android.tv.demo` an ask row in the new profiles whose header did not check the Input key
  and remote pairing.
- **P7** No keyboard and no updater default-off: sony-kd-55x8509c's Japanese keyboard and the Fire TV
  Stick's `com.amazon.tv.easyupgrade` become ask rows.
- **P8** Matching: tcl-43c645 must not match on model alone; tcl-40s5400a drops the "Smart TV" model key;
  every tie the resolver settles by id is pinned in a test (rulings 1 and 3).
- **P9** Facts pass (descriptions that understate or guess) and the test run on forge, attached.

## Findings

| # | Sev | Where | Finding | Status |
| :- | :- | :- | :- | :- |
| P1 | Must fix (Medium) | `SetupMachine.homePossible`, `DefaultPlanner.launcherInstall`, `weaveCurtain` | sony-kd-55x8509c is Android 7.0 (SDK 24) and the Fire TV Stick 4K is SDK 25; the launcher and the curtain are minSdk 26 (`TV_MIN_SDK`, upstream `minSdk 26`). Nothing gates them: Setup offers the dock and look, plans the install (fails, CONTINUE), pushes config.json into the overlay of an app that is not there, installs the curtain (fails), and the run ends with the "home screen not switched" Done after a failed start. Safe, because the F3 precondition never opens the section and the stock home is never touched, but the person is offered and then shown a failure that could never succeed. Fix: `homePossible()` false when `facts.sdk < 26` (best from a `minSdk` on `LauncherArtifact`, so it follows the pin), the planner refuses `launcherInstall` and skips `weaveCurtain` below the curtain's minSdk as defence in depth, and the clean-up is planned as now. Test: a Setup on SDK 24 plans disables only, no install, no push, no curtain. | Open |
| P2 | Must fix (Medium) | `SetupMachine.homePossible`, `DefaultPlanner.takeover` line 427, xiaomi-mitv-astp1 | ASTP1's stock home is `com.mitv.tvhome` (China PatchWall), which is not a known launcher, so the profile's `stockLaunchers` is empty. Setup still sets `takeover = true`, the planner refuses ("no known home screen for this TV model"), the whole plan is `Refused`, and Setup stops on the PROBE_FAILED card. The profile can never be applied. The shipped stellamore-t950s has the same shape today. Fail closed, but useless and misleading. Fix: `homePossible()` false when the exact profile has no known stock launcher (as for FIRE_OS and TCL_CN), so these TVs get the clean-up. `com.mitv.tvhome` stays never-disable (it is), and the runtime HOME claimant rule also holds it. | Open |
| P3 | Must fix (Medium) | philips-55oled707, `DebloatTxt.keepListIn` | The keep-list reader starts at header line 25 ("... DO NOT DISABLE block says ...") and runs to line 43, collecting `com.google.android.tvlauncher` and `com.phorus.playfi.tv`. With the stock launcher never-disable, `takeover` sets `keepStockEnabled`, and the header's own note 3 says tvlauncher then keeps Home and set-home-activity does not stick: the home screen switch always fails and rolls back on this set, and on any 50PUS8517 that resolves here (see P12). Fix: end a keep paragraph at a numbered note or a line that names another list, or let the meta drop header keep names (a `headerKeep: false` or an explicit exclusion); add a ProfilesTest that no profile's never-disable contains one of its own stock launchers unless it carries STOCK_LAUNCHER_KEEP_ENABLED. | Open |
| P4 | Must fix (High) | panasonic-4ktv-jup, panasonic-th-40hs550k, haier-android-tv-dvb2k | `com.google.android.leanbacklauncher.partnercustomizer` is default-off (ads) on both Panasonic sets, MediaTek Android TV 11 with tvlauncher, neither reboot-tested. chiq-ai-pont (shipped, same platform) and hitachi-65qledsm20 keep it: without its `tvlauncher.widget` provider `com.mediatek.tv.service` crash-loops after the next reboot and the Input key and hot keys stop; nothing shows until the reboot. The TH header adds that it removes the Inputs and Wi-Fi icons from the stock home. Our own facts row says "Provides data the TV service needs at boot". This is the one boot and input break in the batch. Fix: never-disable on both Panasonic profiles with a curation note; haier-dvb2k's `com.mediatek.android.leanbacklauncher.partnercustomizer` (same platform, no reboot test; only chiq-gtvu65 on Google TV 14 shows it safe) an ask row until reboot-tested. | Open |
| P5 | Must fix (Medium) | tcl-uniontv-latin-america | `com.tcl.partnercustomizer` is default-off, while the list's own header says the remote's T key (its TKey activity) was not tested and to re-enable it if the key stops working; Thomson (same build) and the Hitachi TCL build keep it as key handling. Remote input. Fix: ask row (brand) with a curation note. The other new TCL profiles disable it with their contributors' testing; see P20. | Open |
| P6 | Must fix (Medium) | aipro2k, haier-h32s80efx, haier-android-tv-dvb2k, hisense-hg9, hisense-hismarttv-a4, jmgo-n1s-holan, panasonic-th-40hs550k, skyworth-swtv-21ae, sony-bravia-tl2, xiaomi-mitv-mosr4 | `fusion.android.tv.demo` is the shared package name of each ODM's retail display app. On the MediaTek MTC build (hitachi-65qledsm20) it handles the remote's Input button (TV_INPUT_BUTTON) and Bluetooth remote pairing (AddAccessoryActivity), and the builder rightly made it never-disable there and removed it from the sure sets. Ten new profiles still disable it, and the facts row ("In-store demo loop") is shared by all. Keeping a demo app on costs nothing; losing the Input key or re-pairing does. Fix: ask row in every new profile except those whose header shows the Input key and pairing checked with it off (panasonic-th checked the input panel with remote keys, haier-h32 checked its components with dumpsys, haier-dvb2k names the component HRetailDisplay_2K: these three may stay default-off with a curation note saying so). Soften the facts text ("Retail demo app; on some MediaTek TVs it also handles the Input key"). The shipped xiaomi-mi-tv-4a, mooq0, moor2 and xgimi rows are a follow-up (P20). | Open |
| P7 | Must fix (Medium) | sony-kd-55x8509c, amazon-fire-tv-stick-4k-2018 | (a) `com.google.android.inputmethod.japanese` is default-off (preloads) on the 55X8509C, while sony-kd-65x9300d and sony-xbr-65x900f make the same package an ask row; the curation note leans on the runtime active-keyboard check. Keyboards are never default-off: ask row (other). (b) `com.amazon.tv.easyupgrade` is default-off (ads) here, while insignia-ns55f301na25 makes it a Software updates ask row and facts.tsv says "Helps install system upgrades" (updates). Either an updater is off by default or the facts are wrong; S3 shows the person "Helps install system upgrades" switched off. Make it the updates ask row as on Insignia until the upstream reading ("upgrade your Fire TV" promos) is confirmed, then fix the facts row and the Insignia override together. | Open |
| P8 | Must fix (Medium) | `BundledProfileSource.score`, tcl-43c645, tcl-40s5400a, ProfilesTest | (a) tcl-43c645 matches model "Smart TV Pro" alone (TCL, SDK 30); its header has no device or fingerprint. It now wins the alphabetical tie for every TCL SDK 30 "Smart TV Pro" with an unknown device, where tcl-65c725 (tvlauncher) won before. On a tvlauncher set that lands here, the takeover finds no launcherx claimant and refuses, which refuses the whole Setup (PROBE_FAILED), and the TCL sure set (the D26 answer for unknown models) is bypassed. Park it in unconverted.txt ("no device codename; ask the contributor for getprop") until a device or fingerprint is known. (b) tcl-40s5400a's model key "Smart TV" catches every TCL SDK 30 "Smart TV" with an unknown device, Google TV units included, and beats tcl-g07-google-tv by id. Drop the model key; device C06 is enough (the Hitachi AR14 fingerprint still wins its own build). (c) Pin every tie the resolver now settles by id in a test with the winner the curation notes intend: G10 (55s470g), BRAVIA_ATV2 EU (4k-gb), BRAVIA_VH2 EU (4k-vh2), G05 GB (65c735), G08 GB (c755), UnionTV (tcl-uniontv on TCL, thomson otherwise), R10G (kalley on Kalley), PH1M_EA_9970A (by model string), eleonas (by brand). A renamed or new folder can then never flip a reviewed resolution silently. See ruling 1 for the scoring change. | Open |
| P9 | Must do before merge | facts.tsv, unverified-facts.tsv, forge | Facts that understate or guess, all read in S3's rows: `com.google.android.tvrecommendations` "Promoted content rows" (it also runs the system notification panel on Philips and serves the Google sign-in notice on Xiaomi; say so); `com.google.android.tv.remote.service` (the c755 header found it carries the remote's mic button); `com.tcl.ttvs` (on several TCL sets it hosts the firmware updater); `com.xiaomi.tvqs.overseas` (the Mi Box S 2nd gen header calls it the quick settings panel, MOSR4 and MOOR2 call it Firebase analytics: contested, list it as unverified); `com.goodtoolapps.zeus` ("the owner does not use" reads as the person); `com.mitv.toolhouse`, `com.sony.dtv.sonyshelf`, `com.sony.dtv.networkrecommendation`, `com.sony.dtv.recommendationservice`, `com.apps.esticker3`, `com.haier.yudee.tv`, `com.tcl.interactive`, `com.google.android.apps.nbu.smartconnect.tv` (a data-saver VPN per its header), `org.droidtv.nettvapps` (from the manifest only) into unverified-facts.tsv; Amazon `kindleconnect` (upstream: "show on Fire TV" from Echo or a tablet), `ffsprovisioneeclient` (the provisionee is this stick), `privacypassservice` and `livedeviceservice` reworded or listed. Then run `:core:spec:test` (ProfilesTest, ProtectedReportTest), `:core:planner:test` and `:core:flows:test` on forge in verify mode and attach the summary; this review's readings come from a Python mirror, not the Kotlin build. | Open |
| P10 | Medium | `DefaultPlanner.takeover`, R5-W4 O3 | The converter turns any listed setupwraith into the home screen switch's wizard, overruling the meta. New cases: panasonic-4ktv-jup, philips-55oled707, tcl-40s5400a, where the headers say the wizard does not claim Home (the curation notes say so honestly). D3 now guards the Wi-Fi, so the remaining cost is a needless switch-off (re-pairing the remote needs it on afmu0; factory reset needs it back). See ruling 4. | Follow-up |
| P11 | Medium | `DefaultPlanner.takeover` order | The takeover switches off the stock launcher, then the wizard. The afmu0, vu-4k-lushan and wanbo-x5-pro headers (and the shipped xiaomi-tv-a-pro-2026) say the wizard's RecoveryActivity takes Home once launcherx is off and can crash-loop system_server, so the wizard must go first; when D3 keeps the wizard on, the switch-off of launcherx still happens and the switch is left to VerifyHome and the rollback. Fix: switch the wizard off before the stock launcher; when a HOME_TAKEOVER_SETUPWRAITH profile must keep the wizard on (OWNER or UNKNOWN), skip the switch up front with the HOME_SETUPWRAITH card instead of attempting it (a skip, not a planner refusal, which would refuse the whole plan). Launcher 2.4 behaviour, reviewed in RF-LAUNCHER-240; these profiles add more TVs to it. | Follow-up, before release |
| P12 | Medium | philips-50pus8517, philips-55oled707 | Both model strings come from the headers, but "2021_22_Philips_UHD_Android_TV" is what `adb devices -l` prints for "2021/22 Philips UHD Android TV". If both sets report the slash form, a 50PUS8517 resolves to 55oled707 (score 3), and the 8517 list only wins on a device-only match. The swap is low risk after P3. Confirm with the contributor; meanwhile P8 (c) pins the intended winners. | Follow-up |
| P13 | Medium | kalley-r10g, prismplus-q65-ultra-r10g | Both match device "R10G" with no model key. The sibling MOKA build philips-r8g-google-tv reports model R8G and device whitefield, so R10G is likely the model. If so both profiles never match (fail closed). Put "R10G" in models as well, and get getprop from a contributor. kalley-r10g: `rebootTested` should be true (header line 46). | Follow-up |
| P14 | Medium | `com.google.android.tvrecommendations` (6 new profiles, old ones, five sure sets) | Default-off widely, including the reference TV. Disabling it kills the system notification panel device-wide (Philips), so a Google sign-in notice has nowhere to show and the Play Store then only says "Authentication is required" (axso2 header). The takeover disables tvlauncher too, so the panel is gone after a switch anyway; on a TV that keeps its own home it is this row alone. On Philips the debloat runs before the takeover, which force-stops the live stock launcher (the header says disable it after). P9 fixes the words. Owner call: ask row, or keep default-off and give Manage a "Google sign-in needed?" help line that puts both back for a minute. | Follow-up, owner |
| P15 | Medium | kalley-r10g, hisense-hismarttv-a4-android11, tcl-65c735 | After the switch the remote's Input key is dead on the Kalley (KEYCODE_TV_INPUT needs the launcher's InputKeyService, which the app never switches on) and on the A4 Android 11 (only tvlauncher handles VIEW_INPUTS); the 65C735's Netflix, YouTube and Prime keys need launcherx. The launcher's Inputs pill still reaches the inputs. Fix: a per-profile note on S3's home switch row ("the remote's Input key stops working; use Inputs on the new home screen"), from a new profile field or quirk. tcl-65c735 also reports that its USB Ethernet adapter never appears after a reboot with USB debugging on; worth a note before the app offers a reboot there. | Follow-up |
| P16 | Low | Sony b2b, Sony and TCL partner customisers | `com.sony.dtv.b2b.vendorprotocol` is default-off in four Sony profiles whose lists say "re-enable if you use" Control4-style home automation (sony-kd-65x80j even moves the rest of b2b to ask); `launcherxcustomizer` and `tvlaunchercustomizer` are default-off though they configure a stock home a Simple user might keep. Ask rows would be kinder; not in the goal's list. | Follow-up |
| P17 | Low | Google TV app (`com.google.android.videos`) | Ask row on most new Google TV profiles (it holds purchased films), default-off on benq-e55-750, tcl-43c645 (Google TV), hisense-hismarttv-a4, hitachi-65qledsm20 and in the Sony, TCL and Xiaomi sure sets (so unknown Sony Google TV sets get it off). Make it an ask row on GOOGLE_TV profiles and keep it out of sure sets, or accept with the facts text as it is (it does name purchases). | Follow-up |
| P18 | Low | tcl-55s470g, tcl-c755-google-tv, hitachi-cdh-le32smart21 | 55s470g: `rebootTested` should be true (upstream 2214d54 says reboot-checked). c755: GB G08 sets leave tcl-qm6k-google-tv and lose WIRELESS_DEBUG_PAIRING_REQUIRED, which the qm6k header ties to non-US G08 sets; no code reads the quirk yet, so no effect today, but add it. Hitachi CDH: `com.tcl.guard` is persistent per its header, so the row will always show as protected; make it an ask row or note it. The Hitachi fingerprint pins AR14, so after an update the set falls to tcl-40s5400a; acceptable. | Follow-up |
| P19 | Low | header "Kept" lists | "Kept on purpose" and "Deliberately NOT disabled" paragraphs are not keep lists to the reader (by design after the "keeps" fix), so their names reach neither the profile's never-disable nor the sure-set exclusion: skyworth-21ae (`com.mediatek.autopair`, `com.smartdevice.global.service`), panasonic-th (`com.mediatek.tvinput`, `com.mediatek.tv.service`, `com.mediatek.network`, `tv.remote.service`), panasonic-jup (`com.mediatek.*` wildcard: add tvinput and tv.service), skyworth-24na (Gboard), tcl-50ep680 and tcl-uniontv (`autopair`, `tcl_bt_rcu_service`, `keyhelp`, `keycustomfunctionservice`, `framework.custom`), the Mi Box S 2nd gen USB media apps. None is listed as a package, so no plan changes today; add them to the meta so Expert switch-offs and future sure sets respect them. Header parse leftovers that are not packages (`android.uid.system`, `org.tcl.ttvs`, `tv.agent`) are harmless; `com.tcl.systemserver` does not protect the real `com.tcl.system.server`. | Follow-up |
| P20 | Low | shipped profiles | Same rules applied to the earlier 30: `fusion.android.tv.demo` is default-off on xiaomi-mi-tv-4a, mooq0, moor2 and xgimi-horizon-s-pro (P6); `com.tcl.partnercustomizer` (T key) on the TCL lists whose headers did not test the T key; tcl-qm6k-google-tv parsed "ttvs and logkit ship ENABLED" as a keep list (already in the protected report as feedback). | Follow-up |
| P21 | Low | facts copy | Hisense wording on a Vu ("Hisense ACR", "Hisense store mode", "HiAlexa, Alexa for Hisense TVs"); hisense-hg9's SmartSense rows say "needed to boot" where its header reports a clean boot with them off; `com.mediatek.testpq` "Picture test tool" (it applies picture settings at boot on Hitachi). Ask rows only; wording. | Follow-up |
| P22 | Low | curation-notes.tsv | Departures without a note: panasonic-jup `providers.calendar` ask, haier-h32 `usmartdevice` ask, haier-dvb2k `yudee.tv` and `sharetv.webview` to casting, vu `odinbrowser` ask, wanbo `hotspot2.osulogin` ask, prismplus karaoke and preloads ask, jmgo's guessed brand. The 55PUS7503 contributor (PR #209) gets no credit though their list is subsumed by 65pus8102: name them in 8102's `tested.by` or the refusal line. | Follow-up |
| P23 | Info | quirks with no consumer | REBOOT_COMMAND, SETTLE_BEFORE_REBOOT, REENABLED_AT_BOOT and WIRELESS_DEBUG_PAIRING_REQUIRED are carried in profiles but read by no code outside the parser (sony-kd-65x80j and sony-bravia-bf1 would even need opposite meanings of REBOOT_COMMAND). Define each quirk's meaning when the first consumer lands. | Info |
| P24 | Info | xiaomi-mitv-afmu0 | The header says "re-enable setupwraith to re-pair the remote". The S3 row for the wizard (inside the switch) could say so. | Info |
| P25 | Info | amazon-fire-tv-stick-4k-2018 | Commented-out package lines with a trailing note become group titles in the scanner; the meta carries them as keys, so the result is right but fragile. `com.amazon.providers.contentsupport` and `bueller.photos` are default-off as on Insignia (lived with); the home is Fire OS's own and cannot be replaced here, so watch for reports. | Info |

## Rulings on the builder's flags

- **(1) tcl-55s470g wins the G10 tie alphabetically: outcome upheld, method overruled.** The two lists
  are the same device, product and build (G10, G10_4K_US_NF, STT2.230203.001). 55s470g is both safer
  and more complete: G10 sets lose default-off only for TCL TV+ and `channelplus`, and `channelplus`
  registers a TV input, so ask is the rule anyway; they gain six never-disable names (ttvs as the
  updater host, providers.config, initsetup and others), REENABLED_AT_BOOT, and ask rows for voice,
  casting and the Google TV app. So G10 sets should get 55s470g. But id order is not a policy: a
  renamed folder or a new upstream example silently changes which reviewed list a TV gets, and the same
  rule already decides five other pairs this batch (G05 GB, G08 GB, ATV2 EU, VH2 EU, UnionTV). My
  recommendation for the resolver, in order: the score (model 2, device 1); then the more specific
  match (a fingerprint prefix beats none, a longer one beats a shorter one; a brand beats none); then
  an explicit `supersedes` in the meta for true duplicates (55s470g supersedes tcl-g10-google-tv);
  then a test failure, not the id, for any pair still tied. Keep the older profile in the bundle
  (Manage resolves a TV by its stored `profileId`, so dropping a shipped id would orphan existing TVs).
  For this merge, P8 (c)'s pinned tests are enough; the scoring change is a follow-up. Note for the
  record: this is the same situation as philips-55pus7503, which was refused for being unreachable;
  tcl-g10-google-tv is now unreachable too, and should be marked superseded rather than removed.
- **(2) Seven profiles guess a brand string, three match on the maker: upheld, with notes.** The brand
  is a hard filter on brand or manufacturer, so a wrong guess fails closed (the profile never matches;
  none of these brands has a sure set, so the TV gets the generic plan). That is acceptable. Inferred:
  panasonic-jup, panasonic-th (its vendor fingerprint says skyworth), skyworth-24na, haier-dvb2k,
  haier-h32, benq-e55-750, chiq-gtvu65, the four Hisense, jmgo, onn, kalley, aipro2k, thomson. Maker
  matches (Vu HVCL, Wanbo SkyworthDigital, PRISM+ MOKA) come from the headers and are sound; the
  Philips R8G header confirms MOKA builds report that manufacturer. One fails open rather than closed:
  thomson-32he5800 loses to tcl-uniontv-latin-america whenever the TV reports TCL as brand or
  manufacturer (same build, so harmless after P5); its note should say manufacturer too. Every guessed
  brand gets a "(match) brand inferred, confirm with getprop" curation line (jmgo and the Hisense four
  have none), so a device report can settle each.
- **(3) tcl-43c645 matches on model alone: overruled, see P8.** Model-only matching is acceptable only
  for a model string that names one product. "Smart TV Pro", "Smart TV", "BeyondTV", "UnionTV" and
  "AI PONT" name a family, and with them a model-only profile becomes the fallback for every unknown
  sibling, bypassing the D26 sure set and, when the stock launcher differs, refusing the whole Setup.
  Park 43c645 until a device is known. benq-e55-750 (model "AI PONT", brand BenQ, SDK 30, no device in
  the header) is narrower and its list is generic, so it may stay, with a request for getprop; the
  shipped benq-e55-735 and tcl-beyondtv already work this way. Longer term, a profile that lists
  devices should need its device to match, and a family-name model should never score on its own.
- **(4) The converter forces the setupwraith flag whenever the wizard is a package line (R5-W4 O3):
  overruled in part, follow-up P10.** Since D3 the forced flag no longer risks the Wi-Fi, so it is not
  a merge blocker, and on Google TV sets it is what makes the switch hold. But the curated meta should
  win, as R5-W4 O3 asked: where the meta names no wizard (the header says it does not take Home), the
  listed wizard goes to the "Needed for the new home screen" group as a keep row and no flag is set.
  Better still, and covering uncurated data too: the takeover switches the wizard off only when the
  snapshot shows it among the HOME claimants. That closes O3.
- **(5) sony-kd-55x8509c is SDK 24 and the launcher needs 26: yes, a must-fix in the planner and
  Setup, see P1.** Skip the launcher install, the look push, the curtain and the home screen switch
  below SDK 26, keep the clean-up, and do not offer the dock or look. No earlier profile hits it: all
  30 shipped profiles are SDK 26 or above (the lowest is 28). It is not new, though: any unknown TV or
  sure-set match on Android 7 already took this path, and this batch adds the first exact profile
  below 26 (and the Fire TV Stick at 25, where the switch is already off but the curtain is still
  attempted). sony-kd-65x9300d at SDK 26 is fine.

## What was checked and held

- **Parser changes.** `KEEP_WORD` (`^keep\b`) and the "Already" stop change the keep lists of exactly
  three upstream folders, all new: nvidia-shield-tv-2019 loses eight names a wrapped "keeps" line had
  made never-disable by accident (none of the header's "Kept" items is an active line, so nothing it
  meant to keep is now off), sony-xbr-65x900f loses Samba TV and Sony promos (its list disables them),
  sony-bravia-tl2 loses three already-off apps that are not listed. No keep list of the 30 shipped
  profiles changed, and the 30 meta entries are unchanged. Both new ProfilesTest cases test the
  intended reading. P3 is the remaining over-read.
- **Default-off scan.** All 800 default-off rows (248 packages) were checked by name and note against
  the goal's list. No default-off row is a static denylist name, a Wi-Fi or network stack package,
  Play Store or Play services, an installer, permission controller, settings or settings provider,
  system UI, webview, DRM, a TV input framework, HDMI-CEC, Bluetooth or remote pairing service, OTA
  (every updater by name is a Software updates ask row via D22, and the header-named ones too), an
  accessibility service, or a known stock launcher. The exceptions are P4, P5, P6 and P7. Platform
  providers that appear (calendar, contacts) are ask rows; the vendor "providers" that are default-off
  feed ad tabs. Persistent packages listed default-off (Hisense logreport, TCL guard) are refused at
  run time and shown protected.
- **Xiaomi, all seven in full (the owner's brand).** Mi Box S 2nd gen, AFMU0, ASTP1, AXFR1, AXSO2,
  MOSR4, MSSP3 Android 9: every active line present, comments excluded, header keep and "DO NOT
  DISABLE" lists in never-disable (mitv.service, overseaservice, livetv, mediatek network, tvinput and
  tv.service, mi.bluetooth, tv.remote.service, the quick settings panel on the Mi Box, the only settings
  app on ASTP1), updaters ask (updateservice, upgrade, upgrader, ABUpdateStatus), TalkBack, USB media
  player, screensavers, casting and voice ask, setupwraith in the switch on the three Google TV sets
  with D3's guard, REENABLED_AT_BOOT on MSSP3 for katniss. Match keys from the headers; credits
  (#169 and #253, #185, #128, #229, #260, #177, #223) match upstream git; `rebootTested` matches each
  header. PatchWall (`com.mitv.tvhome.atv`) is default-off as on the reference TV, and is protected at
  run time on any Xiaomi where it claims Home. ASTP1 is P2. The reference TV (MiTV-AXSO0, dangal) is
  matched by no new profile, `resolveExactThenBrandThenNone` still holds, and its SDK 30 fallback, the
  Xiaomi sure set, changes from 18 to 24 packages: plus the two partner auto-installs, print spooler,
  partner setup, the Google TV app, the handbook and the Mi Home tab, minus the retail demo (P6). All
  benign on that TV.
- **Sure sets.** New sets for Amazon (8), Hisense (8), Philips (10), Sony (27); TCL 11 to 25 (gains
  TCL promos, hotel menu, interactive, smartlink, waterfall and Google extras; loses messagebox and
  partnercustomizer, now kept by Thomson and Hitachi). Every member is default-off in three or more
  profiles of the brand and in no profile's never-disable, as D26 says; the generator excludes
  never-disable names across all brands, which only makes sets smaller. The Google TV app (P17) and
  tvrecommendations (P14) are the members worth a second look.
- **Protected report.** Recomputed with ProtectedReportTest's rules over all 90 profiles: the same five
  rows as committed, so the unchanged report is correct and the test will pass.
- **Matching.** Every pair of the 90 profiles that can match the same TV was enumerated. Brand,
  SDK and fingerprint separate most; the ties settled by id are listed in P8 (c) and each currently
  gives the result its curation note intends. Fingerprint prefixes in sony-bravia-4k-gb, sony-bravia-4k-vh2,
  tcl-65c735, tcl-c755, tcl-43ep660, tcl-beyondtv-rt2851 and tcl-beyondtv2 match their headers' builds.
  Cross-brand device sharing (eleonas, R10G, PH1M_EA_9970A, AI PONT) is held apart by brand, model or
  SDK.
- **Refusals in unconverted.txt.** redmi-mitv-antl0 (SDK 22, refuses `pm disable`), portronics-beem-470
  (generic ADT-3 identity) and philips-55pus7503 (same device, firmware and fingerprint as 65pus8102,
  whose list contains all eight of its packages; 8102's extra default-off rows are app and telemetry
  level) are each justified; the file's pin test and sort order hold.
- **Facts and credits.** 301 new facts rows read: names and descriptions are mostly taken from the
  contributors' notes and are plain; the 34 unverified rows are the right kind (purpose from the
  package name). P9 lists the ones that understate or guess without a flag. Every `tested.by` in the
  43 profiles read in full names the contributor and PR that upstream git shows.
- **Writing rules.** No em dash and no double hyphen in facts.tsv, curation-notes.tsv,
  unverified-facts.tsv, unconverted.txt or the commit messages; no attribution lines in the commits.
