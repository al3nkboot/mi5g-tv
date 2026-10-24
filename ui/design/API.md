# `:ui:design` public API

The design system of DESIGN-SYSTEM.md as Compose code. Screens (W8) use these components and
tokens only; they never hard-code colours, sizes or fonts. Screen copy is passed in as strings;
the few words that belong to a component (Off, Kept, Undo, the status words of D42, "Live from
the TV", the countdown announcements, the mirrored TV dialogs) come from this module's resources
and can be overridden where a parameter exists.

Packages: `tv.cleanyour.ui.design.theme` (tokens, glass, glow, motion), `...ui.design.icon`
(`CytIcons`), `...ui.design.component` (everything else).

## Theme and tokens (`theme`)

| Symbol | Shape |
| :- | :- |
| `CytTheme(darkTheme: Boolean = true, reducedMotion: Boolean? = null, content)` | Root theme. Dark is the brand default; pass `isSystemInDarkTheme()` if wanted. `reducedMotion = null` reads `ANIMATOR_DURATION_SCALE`. Maps into `MaterialTheme` so stock M3 looks right |
| `CytTheme.colors / type / shapes / glass / highlight / reducedMotion` | Token accessors inside composition |
| `CytColors` (`DarkCytColors`, `LightCytColors`) | `bg, surface1..3, rim, outline, outlineStrong, textPrimary/Secondary/Tertiary, primary, onPrimary, primaryPressed, accent, glowBrand, glowTv, success*, warning*, danger*, scrim`, glass and highlight colours, `isDark` |
| `CytType` (`DefaultCytType`) | `display, headline, titleLarge, titleMedium, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, overline, numeral, mono` (Outfit, `Outfit` FontFamily) |
| `CytShapes` | `pill, small (12), row (16), medium (20), large (28), sheet (28 top)` |
| `CytSpacing` | `s4..s56, gutter (20), gutterNarrow (16), titleTop, titleGap, primaryAboveInset, touchTarget, buttonHeight, chipHeight, topBarHeight, ...`, `gutterFor(width)` |
| `CytMotion` | Springs (`highlight`, `press`), easings (`emphasizedDecelerate/Accelerate`) and every duration of DESIGN-SYSTEM 6 |
| `LocalReducedMotion`, `Context.isReducedMotion()` | Reduced motion flag |
| `stepTransition(forward, reduced, layoutDirection, density)` | `AnimatedContent` transitionSpec for wizard steps (shared axis X, RTL aware, fade only when reduced) |
| `titleTransition(reduced, density, layoutDirection)` | Same screen, new title |
| `Modifier.pressScale(interactionSource)` | Press to 0.97 |
| `Modifier.glass(shape, style: GlassStyle)` | The glass recipe: three draws, no offscreen layer. `CytTheme.glass` or `CytTheme.highlight` |
| `Modifier.ambientGlow(color, core, coreAlpha, midAlpha, alpha: () -> Float)` | Raw radial glow |
| `Glow` = `Brand, Tv, Done, None, Module(accent)` and `Modifier.animatedAmbientGlow(glow)` | The screen glow with the launcher's dip on change |
| `R.raw.cyt_outfit_ofl` | The OFL text for the Licences screen (M8) |

## Icons (`icon`)

`CytIcons.Back, Close, Help, Check, ChevronForward, ChevronDown, ChevronUp, Info, Warning, Stopped,
Shield, Wifi, WifiOff, Tv, Minus, Plus, Refresh, Copy, Settings, ArrowUp, ArrowDown, Search, Cards,
Home, Undo, Restart, Key, Download, Phone, Delete, Share, Edit, Lock, External, Reorder, Grip, Circle, Dash`
(`CytIcons.all`). Back, ChevronForward and Undo mirror in RTL.

## Components (`component`)

### Text and layout

| Composable | Parameters |
| :- | :- |
| `ScreenScaffold` | `glow: Glow, modifier, topBar: @Composable () -> Unit = {}, bottomBar: (@Composable ColumnScope.() -> Unit)? = null, overlay: @Composable () -> Unit = {}, scrollable = true, content: @Composable ColumnScope.() -> Unit`. Insets, gutter, glow, pinned bottom actions. The content's last 28 dp fade into the background while it can scroll further; a sentence that must stay above the primary (the S3 safety line) goes in `bottomBar`; a card that appears late (a stop card on Welcome or Done) should carry a `bringIntoViewRequester` and call `bringIntoView()` when it appears |
| `CytTopBar` | `modifier, onBack: (() -> Unit)? = null, center: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null` |
| `ScreenTitle` | `text, modifier, size: TitleSize = HEADLINE (or DISPLAY), color`. Heading semantics |
| `Lead` | `text: String or AnnotatedString, modifier`. The one supporting sentence |
| `rememberEmphasis(text, vararg emphasised): AnnotatedString` | Parts in text.primary ("Tick Always allow, then choose OK") |
| `Overline` | `text, modifier, color`. Upper-cased in the locale |
| `StepCounter` | `current: Int, total: Int, modifier, extra: String? = null` ("2 of 5", "4 of 5 · Look at the TV", D33) |
| `Gap(height)`, `ListDivider(modifier)` | Spacing helpers |
| `ActionColumn(modifier, content)` | Bottom actions, 8 dp apart |
| `LtrText(text, modifier, style, color, maxLines, overflow = Clip)`, `ltr(s): String` | Package names, digests: always left to right (`TextOverflow.MiddleEllipsis` with `maxLines = 1` keeps both ends of a long package) |

### Actions

| Composable | Parameters |
| :- | :- |
| `PrimaryButton` | `text, onClick, modifier, enabled = true, loading = false, guardObscured = true` (pass the verb as text while loading; `guardObscured = false` only when the primary is Back, a plain OK or navigation) |
| `SecondaryButton` | `text, onClick, modifier, enabled = true, guardObscured = false` (glass pill; `guardObscured = true` when it writes to the TV, "Undo what we did") |
| `TertiaryButton` | `text, onClick, modifier, enabled = true, quiet = false, guardObscured = false` (accent text; quiet = text.secondary, "Stop and undo"; `guardObscured` as above) |
| `DestructiveButton` | `text, onClick, modifier, enabled = true` (outlined danger, "Put everything back" only; always guarded) |
| `SmallGlassButton` | `text, onClick, modifier, enabled = true, guardObscured = true` ("Restart" inside a card) |
| `MorphingPrimaryButton` | `text, onClick, progress: Float?, modifier, enabled = true` ("Make it clean" morphs into the 4 dp bar when progress is set) |
| `CytIconButton` | `icon, contentDescription: String, onClick, modifier, tint` |
| `Modifier.rejectObscuredTouches(onRejected = {})` | Drops a touch flagged `FLAG_WINDOW_IS_OBSCURED` or `FLAG_WINDOW_IS_PARTIALLY_OBSCURED` (SECURITY 4.3, P8). Put it after `clickable`. The guarded controls use it and show the hint `cyt_obscured_hint` under themselves for 6 s after a dropped tap. The rule (R4-W8): every control whose tap starts a write to the TV is guarded, whatever it looks like (primary, destructive, small card action, the countdown "Yes", and by parameter a secondary or tertiary, a `Banner` action and the `ThemePicker` on Manage); never Back, Cancel, close or navigation (R5-W8 O1) |

### Status and feedback

| Composable | Parameters |
| :- | :- |
| `TvStatusChip` | `tvName, status: TvStatus, modifier, stateWord: String = status.word(), onClick: (() -> Unit)? = null`. `TvStatus` = `CONNECTED, WAITING, WORKING, DEBUGGING_OFF, OFFLINE` |
| `DiscoveryChip` | `text, found: Boolean, modifier, secondary: String? = null` (Welcome live line, polite live region) |
| `GlassChip` | `text, modifier, mark: ChipMark = NONE, icon: ImageVector? = null, secondary: String? = null, separator = false, onClick = null, liveRegion = false, pulse = false, singleLine = false` (`singleLine`, as the status chip uses it: the text shortens with an ellipsis at large font sizes and the secondary word is never cut) |
| `Banner` | `text: String or AnnotatedString, tone: BannerTone, modifier, icon = default for tone, actionLabel: String? = null, onAction, guardAction = false` (`guardAction = true` when the action writes to the TV, "Restore mine"). `BannerTone` = `SUCCESS, WARNING, DANGER, NEUTRAL, INFO` |
| `StopCard` | `title, body, modifier, icon = CytIcons.Stopped, primaryLabel?, onPrimary, secondaryLabel?, onSecondary, tertiaryLabel?, onTertiary, detailsLabel?, onDetails, busyText: String? = null, primaryGuarded = true, secondaryGuarded = false, tertiaryGuarded = false` |
| `UndoSnackbarState` (`rememberUndoSnackbarState()`) | `show(message, actionLabel: String? = null, onAction)`, `dismiss()`, `current: UndoMessage?`. 5 s, 10 s with TalkBack |
| `UndoSnackbarHost` | `state, modifier` |
| `UndoSnackbar` | `message, onAction, modifier, actionLabel = "Undo"` |
| `Spinner` | `modifier, size = 16.dp, color, strokeWidth` |
| `ProgressBar` | `progress: Float, modifier` |
| `ProgressNarration` | `title, percent: Int, lines: List<NarrationLine>, modifier, footnote: String? = null, stopLabel: String? = null, onStop, maxVisible = 6`. `NarrationLine(key, text, state: NarrationState)`, `NarrationState` = `DOING, DONE, UPCOMING, SKIPPED` |
| `NarrationRow` | `line, modifier, animate = true` |
| `rememberCytHaptics(enabled = true): CytHaptics` | `light()`, `double()`, `firm()` |

### Cards, rows, lists

| Composable | Parameters |
| :- | :- |
| `StepCard` | `title, body, modifier, overline: String? = null, state: StepState = CURRENT, illustration: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null`. `StepState` = `UPCOMING, CURRENT, DONE, STOPPED` |
| `PagerDots` | `count, selected, modifier, description: String? = null` |
| `ListGroup` | `modifier, tone: GroupTone = LOW (or RAISED), content: ColumnScope` |
| `ListRow` | `title, modifier, subtitle?, subtitleColor, value: String?, leading?, trailing?, onClick?, chevron = onClick != null && trailing == null, titleColor, emphasised = false` |
| `DockRow` | `name, modifier, tile = { AppTile(name) }, onClick?, controls?` (S3b: the controls sit beside the name while its longest word fits, else under it, so names never break mid-word at 200% font) |
| `DockEditor` | `state: DockEditorState, apps: List<DockApp>, modifier, haptics = true, scroll: ScrollState? = null, undo: UndoSnackbarState? = null` (S3b: the dock, then Row 2 and Row 3, each a group of up to `columns` apps, then the other apps. Each app has a small take out button and a grip: touch the grip and move to drag at once (a long press on the row also lifts it), within or across rows; the app lifts in the glass highlight, the others spring out of the way, a light tick on pick up and drop, and with `scroll` the sheet scrolls near its edges. Nothing lifts or springs under reduced motion. TalkBack gets Move up, Move down, Move to the dock or row n and Take out. Rows under the dock have "Remove row", undone from `undo`. Its words are this module's `cyt_dock_*` strings) |
| `DockEditorState(initial: List<DockApp>, columns, maxRows = 3)` | One ordered list cut into rows of `columns`: `apps`, `rowCount`, `filledRows`, `hasRoom`, `canAddRow`, `add`, `remove`, `move`, `moveToRow`, `addRow`, `removeRow` (returns a `DockSnapshot` for `restore`), `dragging`. A row below holds apps only once the rows above are full; "Add a row under the dock" opens one when they are |
| `DockApp(key, name)` | An app the editor places (the key is the package name) |
| `ConfirmSheet` / `ColumnScope.ConfirmQuestion` | `title, body, confirmLabel, onConfirm, cancelLabel, onCancel` (a question before Stop or Undo: the title asks, one calm sentence, the verb as a primary that drops taps through overlays, a quiet way back that does not) |
| `LinkRow` | `text, onClick, modifier, chevron = true, minHeight = 48.dp` |
| `SwitchRow` | `title, checked, onCheckedChange, modifier, subtitle?, warning = false, enabled = true` |
| `CytSwitch` | `checked, onCheckedChange?, modifier, enabled = true` |
| `TextActionRow` | `text, actionLabel, onAction, modifier` (D4 "Home screen: ...  Change") |
| `SafetyLine` | `text, modifier, icon = CytIcons.Shield` |
| `CheckLine` | `text, modifier, state: CheckState = DONE (NONE, PENDING), emphasised = false` |
| `InfoChip` | `text, modifier, icon: ImageVector? = null` ("Community", level "Simple"). Put several in a `FlowRow`, never a `Row`: they wrap at 200% font |
| `IconTile` | `icon, modifier, tint, fill` |
| `AppTile` | `name, modifier, icon: ImageBitmap? = null, size = 40.dp` (TV icon or letter tile) |
| `CytTextField` | `value, onValueChange, label, modifier, error: String? = null, supporting: String? = null, keyboardType, singleLine = true` |
| `TvCard` | `name, statusLine, thumbnail: ImageBitmap?, thumbnailDescription, modifier, level: String? = null, onClick?` (Manage) |

### Plan preview (D2, DESIGN-SYSTEM 8.4)

| Composable or type | Parameters |
| :- | :- |
| `SummaryGroup(key, name, count)`, `DiffItem(key, name, description, packageName?, off)`, `DiffGroup(key, title, items)` (`offCount`) | State classes, `@Immutable` |
| `NumeralHeadline` | `count: Int, headline: String, modifier, animate = true` ("23" + "things to switch off", counts up, static when reduced) |
| `PlanSummaryCard` | `groups: List<SummaryGroup>, seeListLabel, onSeeList, modifier` |
| `OffKeptPill` | `off: Boolean, modifier` |
| `DiffRow` | `item, onToggle: ((DiffItem) -> Unit)?, modifier, showPackageName = false` (null onToggle = protected, no pill) |
| `DiffGroupHeader` | `title, offCount, total, modifier` |
| `DiffGroupSection` | `group, onToggle, modifier, showPackageNames = false, visibleCount = all, onShowMore?` |
| `CollapsedGroupsCard` | `groups, onExpand: (DiffGroup) -> Unit, modifier` |
| `ProtectedGroupSection` | `title, items, onWhy, modifier, showPackageNames = false` |
| `Modifier.listAppear(index)` | The 8-row stagger fade |

### Sheets

| Composable | Parameters |
| :- | :- |
| `CytBottomSheet` | `onDismissRequest, modifier, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), content: ColumnScope` |
| `SheetSurface` | `modifier, content` (the same look inline, for previews and tests) |
| `SheetHeader` | `title, modifier, trailing: AnnotatedString? = null, subtitle: String? = null` ("Customise", "22 of 23 off") |
| `SheetDragHandle` | `modifier` |

### Single choice

| Composable or type | Parameters |
| :- | :- |
| `ChoiceOption(key, title, subtitle?, icon = CytIcons.Tv)` | |
| `ChoiceList` | `options, selectedKey: String?, onSelect: (ChoiceOption) -> Unit, modifier` (S1b, glass highlight springs) |
| `ThemeOption(id, name, description, swatch: List<Color>)` | swatch = background, glow, tile colours |
| `ThemePicker` | `options, selectedId: String?, onSelect: (ThemeOption) -> Unit, modifier, guardObscured = false` (`guardObscured = true` on Manage, where a tap applies the look to the TV). `themeSwatch(id)` gives the preview colours of the three launcher themes, shared by S3a and M2 |
| `HighlightColumn` | `count, selectedIndex, modifier, gap = 10.dp, row: @Composable (index, selected) -> Unit` (build other single-choice lists) |

### The TV on the phone

| Composable | Parameters |
| :- | :- |
| `TvThumbnail` | `image: ImageBitmap?, contentDescription, modifier, label: String? = "Live from the TV", placeholder` (150 ms crossfade, D32) |
| `CountdownRing` | `remainingMs: Long, totalMs: Long, modifier, stopped = false, updateIntervalMs = 1000` (announces at 60, 30, 10 s) |
| `CountdownConfirm` | `overline, title, thumbnail, thumbnailDescription, sentence, instruction, liveCheck, liveCheckPassed, remainingMs, totalMs, timeoutSentence, yesLabel, noLabel, onYes, onNo, modifier, stopped = false, noThumbnailSentence: String? = null` (S4b body; Yes and No are the first focus stops after the heading; the ring and the timeout sentence stay pinned above the buttons while the picture and the sentence scroll at large font sizes) |
| `formatCountdown(remainingMs): String` | "1:24" |
| `TvFrame` | `modifier, contentDescription? = null, showStand = true, aspect, screen: BoxScope` |
| `AdbAllowMirror` | `fingerprint: String, modifier, highlightBytes = 2, contentDescription? = null` (S2c, FLAG_SECURE screen per D35 is W8's job) |
| `CardPromptMirror` | `appName, packageName, keyHex, modifier, contentDescription? = null` (M3c) |
| `CodeMatchCard` | `label, caption: String?, code, modifier` ("The code on the TV ends in 7F:2A", "6966 ed15 1f52") |
| `WelcomeIllustration` | `modifier, play = true, contentDescription? = null` (plays once, 3 s, D5) |
| `TvMenuArt` | `highlight: Int, modifier, rows = 5, toggleOn: Boolean? = null, taps = 0` (text-free step card art) |
| `GlassCheckMark` | `modifier, size = 56.dp` (Done, D31; use `Glow.Done` on the screen) |
| `RestartGlyph` | `breathing: Boolean, modifier` |

### Modules

| Composable | Parameters |
| :- | :- |
| `ModuleCard` | `name, summary, chips: List<String>, accent: Color, onClick, modifier, icon: ImageBitmap? = null` (M3a) |
| `ModuleHero` | `name, tagline, accent: Color, modifier, badge: String? = null, contentDescription? = null, art: (@Composable BoxScope.() -> Unit)? = null` (M3b) |
| `MonospaceBlock` | `lines: List<String>, modifier, copyLabel: String? = null, onCopy: (() -> Unit)? = null` (Show commands, S2x, M8) |

## Rules the components already follow

48 dp touch targets; every state has a word (pills, chip, narration, the switch row's on or off);
headings marked; titles wrap; RTL mirrors layout, chevrons, the step axis and the ring; package
names, digests and commands stay LTR; no animation under reduced motion beyond fades; the
countdown never changes colour; nothing red except `DestructiveButton` and danger banners. At
200% font the top bar grows with its chip, the sheet header puts its total under the title, the
hero badge shares a row with the name, and the countdown ring grows with the digits.

## Decisions taken in W7

- D-W7-1 `CytTheme(darkTheme = true)` by default: dark is the brand (DESIGN-SYSTEM 2). W8 decides
  whether to follow the system setting.
- D-W7-2 The ambient glow follows the approved mockups where they differ from DESIGN-SYSTEM 5.2's
  numbers: centre (32%, 26%), radius about 108% of the width, 10% stop at about half the radius.
  Core alpha stays 24% (14% light).
- D-W7-3 Done uses `Glow.Done`: the brand glow with a success core, as mockup 06 draws it (D31).
- D-W7-4 Icons are hand-drawn outline vectors on a 24 grid (1.5 stroke), matching the mockups'
  SVGs, instead of Material Symbols glyphs: no icon font or icons-extended dependency.
- D-W7-5 TV art (frame, mirrored dialogs, illustrations) keeps the TV's own colours in light theme,
  stays left to right and ignores the phone's font scale: it is a picture of the TV.
- D-W7-6 The app icon ships as `@mipmap/cyt_ic_launcher` (adaptive, with monochrome) and
  `@drawable/cyt_ic_notification` from this module, for `:app` to reference.
- D-W7-7 The glass specular is a gradient that ends transparent at 40% of the height, so no clip is
  needed; the rim is the same outline inset by half the stroke. `GlassTraceTest` records the draw
  calls (no saveLayer, no clip). Fades use `CompositingStrategy.ModulateAlpha`, never an offscreen
  layer.
