# R4 review of W7 (`:ui:design`): UX and UI against the mockups, plus the R5 code review

Reviewer: R4 gate (did not write the branch). Inputs: the approved mockups
(`app-design/mockups/index.html`), DESIGN-SYSTEM.md, UX.md 7.1 to 7.3, PRODUCT-SPEC D2, D5, D31,
D32, D33, D35, the 158 renderings in `cyt-wt/w7-shots/` (every component and the eight mockup
screens in dark, light, 200% font and RTL), and `git diff main...w7`.

## Verdict: MERGE AFTER FIXES (fixes applied on w7, goldens re-recorded)

The branch is a faithful, minimal rebuild of the mockups: black, glass, Outfit, one glow per
screen, the white pill, the Off / Kept pill, the quiet overline step counter. Side by side with
the mockups the eight screens read as the same design; the differences are the ones the frozen
decisions asked for (D7's chip wording on Done, D24's "Optional") or fixture copy in the test
screens. What held it back was 200% font: the status chip clipped its state word on five of the
eight screens, the hero badge ran over the tagline, the sheet title broke mid-word, and a
package name was hard-clipped. Those, one TalkBack gap (the switch row had no on or off state),
one spec deviation (the thumbnail crossfade used an offscreen layer) and one real-sheet visual bug
(the top rim drew under the drag handle) are fixed in the R4 commits below. Build and tests pass
after the fixes; see the end.

## Findings

Severity: High blocks the merge, Medium should be fixed before W8 builds on it, Low is noted.

| # | Sev | Finding | Where seen | Status |
| :- | :- | :- | :- | :- |
| H1 | High | The TV status chip clipped or wrapped its state word at 200% font ("Conne / cted", "Waitin"). The state word is the information (DESIGN-SYSTEM 8.3, "the dot is never the only cue"), and the 56 dp top bar cut the second line | s03, s04, s05, s06, s08 font200; c28 font200 | Fixed: `GlassChip(singleLine = true)` for the status chip shortens the TV name with an ellipsis and never cuts the state word; `CytTopBar` grows with its centre (`heightIn(min = 56)`) |
| M1 | Medium | `ModuleHero`: the "Its card on your TV" badge overlapped "NES GAMES" at 200% (the name column had a fixed 120 dp end padding, the badge grew past it) | s07, c20 font200 | Fixed: name, tagline and badge share one bottom row; the name wraps beside the badge |
| M2 | Medium | `SheetHeader` broke "Customise" mid-word at 200% to make room for "22 of 23 off" | c12 font200 | Fixed: a `FlowRow` puts the total under the title when the two do not fit |
| M3 | Medium | `SwitchRow` announced "Keep debugging on ... switch" with no on or off state: `clickable(role = Switch)` carries no value and the inner `Switch(onCheckedChange = null)` has no toggle semantics. DESIGN-SYSTEM 10: every state in words | code (ATF does not catch it) | Fixed: the row is `toggleable(value, role = Switch)`; the switch inside is a picture (`clearAndSetSemantics`) |
| M4 | Medium | `DiffRow` hard-clipped the package name at 200% ("com.mitv.tvhom") | s08, c07, c12 font200 | Fixed: `LtrText(overflow = MiddleEllipsis)` keeps both ends ("com.mitv…home.atv"); `LtrText` gained an `overflow` parameter |
| M5 | Medium | `TvThumbnail` used `Crossfade`, which renders both frames through `saveLayer`: an offscreen buffer the size of the thumbnail every 5 s, against DESIGN-SYSTEM 5 and D-W7-7 ("fades use ModulateAlpha, never an offscreen layer") | code (D32 check) | Fixed: the last frame stays underneath while the new one fades in with `ModulateAlpha`; one plain draw per frame |
| M6 | Medium | `CytBottomSheet` drew the 1 dp top rim on the content column, under the drag handle, not on the sheet's edge (only `SheetSurface`, the test stand-in, had it right). The shipping sheet would show a stray line 20 dp into the sheet | code | Fixed: the rim draws over the `ModalBottomSheet` surface with `drawWithContent` |
| L1 | Low | The TV stand (white 22%) vanished in light theme | s01, s03 light; c24 light | Fixed: the stand takes the background's contrast (black 22% in light); the screen keeps the TV's colours (D-W7-5) |
| L2 | Low | The countdown digits touched the ring stroke at 200% (fixed 64 dp ring, 32 sp digits) | c10 font200 | Fixed: the ring grows with the font, up to 1.6x |
| L3 | Low | `font/outfit.ttf` and `raw/outfit_ofl.txt` were the only unprefixed resources; resource names merge across modules | code | Fixed: `cyt_outfit`, `cyt_outfit_ofl`; API.md updated |
| L4 | Low | The Retro screen sample put three `InfoChip`s in a `Row`: "4 MB" fell off the screen at 200% | s07 font200 | Fixed in the sample; API.md tells W8 to use a `FlowRow` |
| L5 | Low | `ui/design/build.gradle.kts` registers `build/compose-reports` as an output of `compileReleaseKotlin`, but `compileDebugKotlin` writes its reports there too. Gradle treats this as overlapping outputs and disables the build cache for that task (a warning, not a failure) | code | Not fixed. Option: one report dir per variant, or only enable the reports on release |
| L6 | Low | `CodeMatchCard` with the 12 hex key at 200%: the label squeezes to a word per line beside a three-line code. Readable, not pretty | c16 font200 | Not fixed; W8 could stack label over code when `fontScale >= 1.5` |
| L7 | Low | `ThemeSwatch` builds its radial gradient with pixel constants (centre 18, 14; radius 140), so the glow sits differently per density | code | Not fixed; decorative |
| L8 | Low | The `Fx.homeShot` fixture stands in for the TV screenshot with a plain grey block for the clock; it shows as an odd rectangle in s05, c22, c23 | goldens only | Not fixed; cosmetic in test images |
| L9 | Low | `DiffRow` shortens the plain-words line to two lines with an ellipsis at 200% ("...with adv..."). The mockup's `.cut` asked for one line, so this is more than the design, but the reason can still be lost on a long description | s08 font200 | Left as is; mention in case W8 wants `maxLines = 3` at Custom level |
| L10 | Low | Mockup 07's pixel sprite in the hero is absent: `ModuleHero(art = ...)` is the slot, W8 draws it from the module's card art | s07 | Not a defect |
| L11 | Low | `WelcomeIllustration` plays once per composition: it replays if the user comes back to Welcome. D5 says once | code | Not fixed; W8 passes `play = false` after the first run if it keeps a flag |
| N1 | Note | RTL goldens show English strings reordered by bidi ("OF 5 1", "of 7 7", ".Your TV, tidied up"). That is what forced RTL does to LTR strings; in an RTL locale the translated strings read right. The layout, chevrons, the step axis, the ring direction and the LTR isolates on package names and digests are correct | RTL set | Expected |

## What was checked and passed

- Visual fidelity: the eight screens against mockups 01 to 08. Tokens match (gutter 20, title gap
  12, display 40/46 Light, headline 30/36, numeral 64 ExtraLight tabular, overline 11 with 2 sp
  tracking, 56 dp pills, 32 dp chips, 20 dp card radius, 28 dp sheet). Differences from the
  mockups and why: Done's chip says "Connected" and the checklist says debugging switches off on
  Finish (D7, spec S5); the restart card says "Optional" (D24); the Which TV sample has no "Home
  Wi-Fi" chip and a shorter banner (fixture copy, W8 owns the screen); the hero sprite is W8's
  `art`.
- Hierarchy, rhythm, typography: one title, one sentence, one primary per screen; 4 dp steps;
  Light only at 22 sp and up; titles use `LineBreak.Heading` ("things to / switch off").
- Glass recipe: fill 9% to 4%, specular 8% over the top 40% as a clamped gradient, 1 dp 20% rim,
  the highlight's 1.5 dp 80% rim and the 2, 4, 6 dp rings. `GlassTraceTest` records exactly three
  draws (six on the highlight), no `saveLayer`, no clip. Brushes and outlines are built once per
  size in `drawWithCache`.
- Ambient glow: one radial per screen on the window background, centre (32%, 26%), core 24% (14%
  light), the dip on change (35% in 200 ms, back in 500 ms), no red glow. Done is the brand glow
  with a success core (D31, D-W7-3).
- Contrast: the dark and light pairs are the DESIGN-SYSTEM 10 tokens. New pairs introduced by the
  components (WCAG relative luminance): `onSuccessContainer` on `surface1` ("Home opens the new
  screen") 11.4 dark, 8.4 light; `warning` on `surface1` (the risk line) 10.8 dark, 6.2 light;
  the Kept pill's `accent` on accent 14% over `surface1` 6.2 dark, 5.2 light; `textSecondary` on
  the glass chip over the brand glow's brightest point 5.04 dark (the tightest pair in the app,
  which is why the glow core stays capped at 24%). All pass.
- 200% font: after the fixes nothing is clipped or overlapping in the 39 font200 renderings; the
  buttons wrap to two lines at most; the screens scroll.
- RTL: layout, chevrons (`autoMirror` on Back, ChevronForward, Undo), the step and title
  transitions, the ring direction and the LTR isolates all mirror or hold correctly.
- D2: the Off / Kept pill (surface.3 + rim + minus; accent 14% + accent 50% rim + check), the
  word on the pill, the whole row as the target with "Keep it" / "Switch it off" as the click
  label and "Off" / "Kept" as the state, protected rows without a pill plus "Why?", the undo
  snackbar at 5 s (10 s with TalkBack).
- D5: the welcome plays once, 3 s, rests on the clean state; reduced motion shows the rest state
  (m01 and m02 captures).
- D31: the glass check mark with the highlight rim and rings, `Glow.Done`.
- D32: a 150 ms crossfade, no shimmer; now without an offscreen layer (M5).
- D33: `StepCounter` as a quiet overline, "2 of 5" and "4 of 5 · Look at the TV".
- D35: `AdbAllowMirror` is a picture; `FLAG_SECURE` and `accessibilityDataSensitive` on the Allow
  screen are W8's, as API.md says. W8 must not forget them.
- DESIGN-SYSTEM 10: 48 dp targets everywhere a thing is tappable (the row for pills, the text
  button for Why?, the chip when clickable); headings on titles, sheet titles, group headers, the
  numeral; polite live regions on the discovery chip, narration, snackbar, the busy line and the
  ring; the ring speaks only at 60, 30 and 10 s; Yes and No are the first stops after the heading
  (`traversalIndex`); ATF runs on all 156 renderings with no suppressions.
- Tone (UX 7.3) of the built-in strings: Connected, Waiting, Working, Debugging off, Offline, Off,
  Kept, Keep it, Switch it off, Why?, Show N more, Undo, Done, In progress, Next, Skipped, Live
  from the TV, Copy as commands, "One minute left. Say yes or go back.", "30 seconds left.",
  "10 seconds left.", "N earlier steps". All from the word list; "debugging" appears only in the
  state word the TV's own menu uses; the mirrored dialog strings are the TV's and the launcher's
  own words in their casing. No "Oops", no exclamation marks, no "disable", no "backup".

## Code review (R5 for this PR)

- API ergonomics for W8 (API.md): good. Every screen in UX 2 and 4 can be composed from the
  listed pieces without reaching into Material; copy is passed in; the few built-in words have
  overrides where they matter (`stateWord`, `actionLabel`, `label`). `HighlightColumn` lets W8
  build other single-choice lists. Suggestions, not blockers: `CountdownConfirm` takes 17
  parameters and W8 will call it from one place, fine; `ScreenScaffold` has an `overlay` slot for
  the snackbar host, which API.md could say in so many words.
- Stability: `checkComposeStability` fails the build on any unstable class in the module; the
  state classes (`DiffItem`, `DiffGroup`, `SummaryGroup`, `NarrationLine`, `ChoiceOption`,
  `ThemeOption`, `UndoMessage`) are `@Immutable`, `UndoSnackbarState` is `@Stable` with
  `mutableStateOf`.
- Recomposition cost: animations read their values in draw lambdas (`ambientGlow`,
  `animatedAmbientGlow`, `RestartGlyph`, the fades) or in a `graphicsLayer` block, so the glow dip
  and the appear fades do not recompose. The count-up and the highlight spring recompose per frame
  for their duration (700 ms, the spring), which is inherent (the text changes). Lists key rows by
  package name.
- Offscreen layers: glass and glow draw directly; fades use `ModulateAlpha`; press is a scale
  transform. The one `saveLayer` (Crossfade) is gone (M5). The snackbar's `shadow` is a
  RenderNode shadow, not an alpha buffer, and is the one shadow the design allows.
- Resource naming: everything is `cyt_` prefixed after L3 (strings, drawables, mipmaps, font,
  raw).
- Shared build-logic additions: `AndroidRoborazziConventionPlugin` and five catalog entries, all
  additive; the test task limits (one fork, 1280 MB heap) match the 3 GB forge container. See L5
  for the one wrinkle in the module's own build file.
- Tests: 156 screenshot renderings with ATF, 6 behaviour tests (count-up timing, welcome once,
  countdown announcements, pill toggle, snackbar timing), 2 glass trace tests. `GlassTraceTest`
  writes its trace to `build/outputs/glass-trace/`.

## The 5 screens or components most worth the user's eyes

1. `s04_what_we_found_dark.png`: the numeral and headline pair, the summary card, the keep line.
   The signature screen; the "23" and the light headline are the brand.
2. `s02_which_tv_dark.png` (and `c17`, `c18`): the springy glass highlight with its three rings
   on the chosen TV, the launcher's focus brought to the phone.
3. `s06_done_dark.png`: the glass check mark on the brand glow with the success core (D31),
   the display title, the restart card and the two rows.
4. `s08_customise_sheet_dark.png` (and `c07`): the Off / Kept pills, the group header counts,
   the collapsed groups, the warning line and the undo snackbar.
5. `s03_allow_dark.png` (and `c15`, `c16`): the mirrored TV dialog with the highlighted
   fingerprint tail and the code match card; the moment that decides whether users trust the app.

Contact sheet of the eight screens in dark: `cyt-wt/w7-shots/contact-sheet-dark.png`.

## R4 commits on w7

| Commit | Subject |
| :- | :- |
| 390ee36 | R4: the top bar chip keeps its state word at 200% font |
| 7e96f79 | R4: hero badge and sheet header reflow at 200% font; sheet rim and TV stand |
| 4b0ae06 | R4: package names shorten in the middle; the countdown ring grows with the font |
| b1ae56d | R4: switch rows carry their on or off state for TalkBack |
| eca2da1 | R4: thumbnail crossfade without an offscreen layer |
| 88758dc | R4: cyt_ prefix on the font and licence resources |
| 7cc3579 | R4: module chips wrap in the screen sample; API notes |
| 728e3b1 | R4: the hero badge drops under the name when they do not fit |
| c323f54 | R4: re-recorded goldens after the 200% font, light theme and hero fixes |

A trade-off to know about: with H1 fixed, the top bar chip at 200% reads "Mi … · Connected"
rather than the full TV name. The state word wins because it is the information; the TV's name
is on the screen's title or the Manage card anyway. 17 goldens changed, all in the font200 and
light sets; no dark 100% rendering moved, so the mockup fidelity is untouched. M1 took two
passes: a weighted row first (the name then broke mid-word beside the badge at 200%), then a
`FlowRow` that drops the badge under the name.

## Build

Command, run twice (after the first batch of fixes and after the hero follow-up), with
`FETCH_BACK=ui/design/src/test/screenshots` so the re-recorded goldens came back:

`scripts/build-on-forge.sh :ui:design:recordRoborazziDebug :ui:design:check checkModuleGraph`

Result: `BUILD SUCCESSFUL in 47s`, `checkComposeStability: 1 report(s), no unstable classes`,
`MotionAndBehaviourTest tests="6" failures="0"`, `GlassTraceTest tests="2" failures="0"`,
`ComponentScreenshotTest tests="156" failures="0"` (ATF on every rendering, nothing suppressed),
`checkModuleGraph` passed. `w7-shots/` is refreshed from the new goldens (158 files) and
`w7-shots/contact-sheet-dark.png` holds the eight dark screens side by side.

## For W8 (wiring notes that came out of this review)

- `FLAG_SECURE` and `accessibilityDataSensitive` on the Allow screen (D35) are the screen's job.
- Put `InfoChip`s in a `FlowRow`; put the `UndoSnackbarHost` in `ScreenScaffold(overlay = ...)`.
- `WelcomeIllustration(play = false)` once the first run has happened, if Welcome can be revisited.
- The status chip is `singleLine`; do not pass long TV names expecting them to wrap.
