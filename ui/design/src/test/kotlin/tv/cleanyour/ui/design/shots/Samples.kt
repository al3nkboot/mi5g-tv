package tv.cleanyour.ui.design.shots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.component.AdbAllowMirror
import tv.cleanyour.ui.design.component.AppTile
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CardPromptMirror
import tv.cleanyour.ui.design.component.CheckLine
import tv.cleanyour.ui.design.component.CheckState
import tv.cleanyour.ui.design.component.ChipMark
import tv.cleanyour.ui.design.component.ChoiceList
import tv.cleanyour.ui.design.component.CodeMatchCard
import tv.cleanyour.ui.design.component.CollapsedGroupsCard
import tv.cleanyour.ui.design.component.CountdownRing
import tv.cleanyour.ui.design.component.CytIcon
import tv.cleanyour.ui.design.component.CytTextField
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.CytIconButton
import tv.cleanyour.ui.design.component.DestructiveButton
import tv.cleanyour.ui.design.component.DiffGroupSection
import tv.cleanyour.ui.design.component.DiscoveryChip
import tv.cleanyour.ui.design.component.ConfirmQuestion
import tv.cleanyour.ui.design.component.DockApp
import tv.cleanyour.ui.design.component.DockEditor
import tv.cleanyour.ui.design.component.DockEditorState
import tv.cleanyour.ui.design.component.GlassCheckMark
import tv.cleanyour.ui.design.component.GlassChip
import tv.cleanyour.ui.design.component.GroupTone
import tv.cleanyour.ui.design.component.InfoChip
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.LinkRow
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.ModuleCard
import tv.cleanyour.ui.design.component.ModuleHero
import tv.cleanyour.ui.design.component.MonospaceBlock
import tv.cleanyour.ui.design.component.MorphingPrimaryButton
import tv.cleanyour.ui.design.component.NumeralHeadline
import tv.cleanyour.ui.design.component.OffKeptPill
import tv.cleanyour.ui.design.component.Overline
import tv.cleanyour.ui.design.component.PagerDots
import tv.cleanyour.ui.design.component.PlanSummaryCard
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.ProgressNarration
import tv.cleanyour.ui.design.component.ProtectedGroupSection
import tv.cleanyour.ui.design.component.RestartGlyph
import tv.cleanyour.ui.design.component.SafetyLine
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.SheetHeader
import tv.cleanyour.ui.design.component.SheetSurface
import tv.cleanyour.ui.design.component.SmallGlassButton
import tv.cleanyour.ui.design.component.StepCard
import tv.cleanyour.ui.design.component.StepCounter
import tv.cleanyour.ui.design.component.StepState
import tv.cleanyour.ui.design.component.StopCard
import tv.cleanyour.ui.design.component.SwitchRow
import tv.cleanyour.ui.design.component.TertiaryButton
import tv.cleanyour.ui.design.component.TextActionRow
import tv.cleanyour.ui.design.component.ThemePicker
import tv.cleanyour.ui.design.component.TitleSize
import tv.cleanyour.ui.design.component.TvCard
import tv.cleanyour.ui.design.component.TvMenuArt
import tv.cleanyour.ui.design.component.TvStatus
import tv.cleanyour.ui.design.component.TvStatusChip
import tv.cleanyour.ui.design.component.TvThumbnail
import tv.cleanyour.ui.design.component.UndoSnackbar
import tv.cleanyour.ui.design.component.WelcomeIllustration
import tv.cleanyour.ui.design.component.rememberEmphasis
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme

@Composable
private fun Stack(gap: Int = 12, content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap.dp)) { content() }

// The shell long-option prefix, built so no double hyphen appears in the source.
private val LONG = "-".repeat(2)

@OptIn(ExperimentalLayoutApi::class)
object Samples {
    val components: List<Sample> = listOf(
        Sample("c01_primary_buttons") {
            Stack(8) {
                PrimaryButton("Make it clean", {})
                PrimaryButton("Connecting", {}, loading = true)
                PrimaryButton("Use Mi TV 4A", {}, enabled = false)
            }
        },
        Sample("c02_other_buttons") {
            Stack(8) {
                SecondaryButton("No, put it back", {})
                TertiaryButton("Customise", {})
                TertiaryButton("Stop and undo", {}, quiet = true)
                DestructiveButton("Put everything back", {})
                Row { SmallGlassButton("Restart", {}) }
            }
        },
        Sample("c03_morphing_button") {
            Stack(16) {
                MorphingPrimaryButton("Make it clean", {}, progress = null)
                MorphingPrimaryButton("Make it clean", {}, progress = 0.58f)
            }
        },
        Sample("c04_status_chips") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvStatusChip("Mi TV 4A", TvStatus.CONNECTED)
                TvStatusChip("Mi TV 4A", TvStatus.WAITING)
                TvStatusChip("Mi TV 4A", TvStatus.WORKING)
                TvStatusChip("Mi TV 4A", TvStatus.DEBUGGING_OFF)
                TvStatusChip("Mi TV 4A", TvStatus.OFFLINE)
                DiscoveryChip("Looking for TVs on your Wi-Fi...", found = false)
                DiscoveryChip("Found Mi TV 4A", found = true, secondary = "(Living room)")
                GlassChip("Still looking", mark = ChipMark.SPINNER)
            }
        },
        Sample("c05_step_cards") {
            Stack {
                StepCard(
                    "Open Developer options", "Settings, Device Preferences, About, then press OK on Build seven times.",
                    overline = "Step 1 of 3", state = StepState.CURRENT,
                    illustration = { TvMenuArt(highlight = 3, Modifier.width(240.dp), taps = 7) },
                )
                PagerDots(3, 0)
                StepCard("Turn on USB debugging", "Developer options, USB debugging.", overline = "Step 2 of 3", state = StepState.UPCOMING)
                StepCard("Developer options are on", "", state = StepState.DONE)
                StepCard("Network debugging is missing", "Some TVs need Network debugging too.", state = StepState.STOPPED)
            }
        },
        Sample("c06_plan_summary") {
            Stack(16) {
                Overline("3 of 5 · We found")
                NumeralHeadline(23, "things to switch off")
                PlanSummaryCard(Fx.summary, "See the list", {})
                SafetyLine("A restore point is saved first, so everything can be put back.")
                TextActionRow("Home screen: Netflix, YouTube, Prime Video, Live TV", "Change", {})
            }
        },
        Sample("c07_diff_list") {
            Stack(14) {
                DiffGroupSection(Fx.adverts, {}, showPackageNames = true, visibleCount = 2, onShowMore = {})
                CollapsedGroupsCard(Fx.collapsed(), {})
                ProtectedGroupSection("We keep these", Fx.protectedItems, {})
            }
        },
        Sample("c08_off_kept_pills") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OffKeptPill(true); OffKeptPill(false) }
        },
        Sample("c09_snackbar") {
            Stack { UndoSnackbar("Mi TV+ promos will be kept.", {}); UndoSnackbar("Put back: Screensaver adverts.", {}) }
        },
        Sample("c10_countdown_ring") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CountdownRing(84_000, 90_000)
                CountdownRing(45_000, 90_000)
                CountdownRing(8_000, 90_000)
                CountdownRing(20_000, 90_000, stopped = true)
            }
        },
        Sample("c11_narration") {
            Stack(24) {
                ProgressNarration(
                    "Making it clean", 58, Fx.narration,
                    footnote = "Keep the TV on. You can put the phone down; we'll buzz when it's time to look at the TV.",
                    stopLabel = "Stop and undo",
                )
                ProgressNarration("Making it clean", 91, Fx.narrationLong)
            }
        },
        Sample("c12_sheet") {
            SheetSurface {
                SheetHeader(
                    "Customise",
                    trailing = rememberEmphasis("22 of 23 off", "22"),
                    subtitle = "Nothing is deleted; \"off\" means asleep.",
                )
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiffGroupSection(Fx.adverts, {}, showPackageNames = true, visibleCount = 2, onShowMore = {})
                    Overline("Afterwards", Modifier.padding(start = 4.dp))
                    ListGroup {
                        SwitchRow("Keep debugging on", false, {}, subtitle = "Others on your Wi-Fi could ask to connect", warning = true)
                        ListDivider()
                        SwitchRow("Keep debugging on", true, {}, subtitle = "Others on your Wi-Fi could ask to connect", warning = true)
                    }
                    Overline("Show commands", Modifier.padding(start = 4.dp))
                    MonospaceBlock(listOf("pm disable-user ${LONG}user 0 com.mitv.tvhome.atv", "pm disable-user ${LONG}user 0 com.miui.systemAdSolution"), onCopy = {})
                }
            }
        },
        Sample("c13_stop_card") {
            Stack {
                StopCard(
                    "The old home screen is back",
                    "We stopped. The new home screen didn't start, so we put the old one back.",
                    primaryLabel = "Try with the wizard off", secondaryLabel = "Leave the old home screen",
                    detailsLabel = "Show details",
                )
                StopCard(
                    "The TV stopped answering",
                    "The TV stopped answering after we switched off 14 of 23 things. Usually it's back in a few seconds.",
                    busyText = "Reconnecting...", icon = CytIcons.WifiOff,
                )
            }
        },
        Sample("c14_banners") {
            Stack(10) {
                Banner("Retro is on the home screen.", BannerTone.SUCCESS)
                Banner("Retro's new version is signed with a different key than before.", BannerTone.WARNING)
                Banner("We couldn't save the restore point.", BannerTone.DANGER)
                Banner("Lost the TV. Is it still on?", BannerTone.NEUTRAL, icon = CytIcons.WifiOff, actionLabel = "Reconnect", onAction = {})
                Banner("Phone and TV must be on the same Wi-Fi.", BannerTone.INFO)
            }
        },
        Sample("c15_allow_mirror") {
            Stack(20) {
                AdbAllowMirror(Fx.FINGERPRINT)
                CodeMatchCard("The code on the TV ends in", "That is this phone.", "7F:2A")
            }
        },
        Sample("c16_card_prompt_mirror") {
            Stack(20) {
                CardPromptMirror("Retro", "io.github.udhaykumarbala.retro", "6966 ed15 1f52")
                CodeMatchCard("The key on the TV should read", null, "6966 ed15 1f52")
            }
        },
        Sample("c17_choice_list") { ChoiceList(Fx.tvs, "mitv", {}) },
        Sample("c18_theme_picker") { ThemePicker(Fx.themes, "aurora", {}) },
        Sample("c19_module_card") {
            Stack {
                ModuleCard("Retro", "NES games on the TV. Open source. 4 MB.", listOf("Community", "Open source", "4 MB"), Fx.retro, {})
                ModuleCard("Stremio shelf", "A row of what you are watching.", listOf("Community"), Color(0xFF7B5BFF), {})
            }
        },
        Sample("c20_module_hero") {
            ModuleHero("Retro", "NES games", Fx.retro, badge = "Its card on your TV")
        },
        Sample("c21_rows") {
            Stack {
                ListGroup {
                    ListRow("Home screen", onClick = {}, leading = { AppTile("Home") })
                    ListDivider()
                    ListRow("Switched off", value = "23", onClick = {})
                    ListDivider()
                    ListRow("Add a card to the home screen", subtitle = "Retro games, a Stremio shelf", onClick = {})
                    ListDivider()
                    LinkRow("+ Set up another TV", {})
                }
                ListGroup(tone = GroupTone.RAISED) {
                    ListRow("Restart the TV to check it all sticks", subtitle = "2 minutes, recommended", trailing = { SmallGlassButton("Restart", {}) })
                }
                CheckLine("Restore point saved on this phone (19 Oct, 14:32)")
                CheckLine("Uses the internet to fetch game files", emphasised = true)
                CheckLine("No background service · No tracking", state = CheckState.NONE)
                CheckLine("Saving the restore point", state = CheckState.PENDING)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { InfoChip("Community"); InfoChip("Simple"); InfoChip("4 MB") }
            }
        },
        Sample("c22_thumbnail") {
            Stack {
                TvThumbnail(Fx.homeShot, "The TV shows the new home screen with four apps in the dock")
                TvThumbnail(null, "No picture from the TV yet")
            }
        },
        Sample("c23_tv_card") {
            TvCard("Mi TV 4A, Living room", "Clean. Checked 19 Oct.", Fx.homeShot, "The TV's home screen", level = "Simple", onClick = {})
        },
        Sample("c24_welcome_illustration", reducedMotion = true) { WelcomeIllustration() },
        Sample("c25_tv_menu_art") {
            Stack { TvMenuArt(highlight = 1, toggleOn = true); TvMenuArt(highlight = 2, toggleOn = false) }
        },
        Sample("c26_done_marks") {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassCheckMark()
                RestartGlyph(breathing = false)
            }
        },
        Sample("c27_text_field") {
            Stack {
                CytTextField("192.168.1.50", {}, "The TV's address", keyboardType = KeyboardType.Number)
                CytTextField("8.8.8.8", {}, "The TV's address", error = "That isn't an address on your home network")
            }
        },
        Sample("c28_top_bars") {
            Stack(8) {
                CytTopBar(onBack = {}, center = { TvStatusChip("Mi TV 4A", TvStatus.CONNECTED) })
                CytTopBar(onBack = {}, center = { StepCounter(1, 5) }, trailing = { CytIconButton(CytIcons.Help, "Help", {}) })
                StepCounter(4, 5, extra = "Look at the TV")
            }
        },
        Sample("c29_type_scale") {
            Stack(6) {
                ScreenTitle("Your TV, tidied up.", size = TitleSize.DISPLAY)
                ScreenTitle("Which TV?")
                Text("Title large", style = CytTheme.type.titleLarge, color = CytTheme.colors.textPrimary)
                Text("Title medium", style = CytTheme.type.titleMedium, color = CytTheme.colors.textPrimary)
                Lead("Body large, the one sentence under a title.")
                Text("Body medium, row descriptions and banners.", style = CytTheme.type.bodyMedium, color = CytTheme.colors.textSecondary)
                Text("com.miui.tv.analytics", style = CytTheme.type.bodySmall, color = CytTheme.colors.textTertiary)
                Overline("Overline label")
                Text("23", style = CytTheme.type.numeral, color = CytTheme.colors.textPrimary)
            }
        },
        Sample("c30_icons") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CytIcons.all.forEach { CytIcon(it, it.name, size = 24.dp) }
            }
        },
        Sample("c31_app_icon") {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(tv.cleanyour.ui.design.R.drawable.cyt_ic_launcher_foreground), "App icon",
                    Modifier.size(108.dp).clip(shape).background(Color.Black),
                )
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(tv.cleanyour.ui.design.R.drawable.cyt_ic_launcher_foreground), "App icon, small",
                    Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black),
                )
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(tv.cleanyour.ui.design.R.drawable.cyt_ic_launcher_monochrome), "Themed icon",
                    Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(CytTheme.colors.surface3), tint = CytTheme.colors.accent,
                )
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(tv.cleanyour.ui.design.R.drawable.cyt_ic_notification), "Notification icon",
                    Modifier.size(24.dp), tint = CytTheme.colors.textPrimary,
                )
            }
        },
        // The dock editor (S3b): the dock alone, the dock with a row under it, a row just added,
        // and an app lifted mid-drag (with motion and under reduced motion).
        Sample("c32_dock_editor") { DockEditor(androidx.compose.runtime.remember { DockEditorState(dockApps.take(4), 4) }, dockApps) },
        Sample("c33_dock_editor_rows") { DockEditor(androidx.compose.runtime.remember { DockEditorState(dockApps.take(6), 4) }, dockApps) },
        Sample("c34_dock_editor_new_row") { DockEditor(androidx.compose.runtime.remember { DockEditorState(dockApps.take(4), 4).apply { addRow() } }, dockApps) },
        Sample("c35_dock_editor_drag") {
            DockEditor(androidx.compose.runtime.remember { DockEditorState(dockApps.take(6), 4).apply { previewDrag(from = 1, to = 2, nudgePx = 36f) } }, dockApps)
        },
        Sample("c36_dock_editor_drag_reduced", reducedMotion = true) {
            DockEditor(androidx.compose.runtime.remember { DockEditorState(dockApps.take(6), 4).apply { previewDrag(from = 4, to = 3, nudgePx = -36f) } }, dockApps)
        },
        // The question before a stop or an undo (ConfirmQuestion), as the sheet shows it.
        Sample("c37_confirm_question") {
            Column {
                ConfirmQuestion(
                    "Stop now?", "We stop here and put back what we changed so far. Your TV stays safe.", "Stop and undo", {}, "Keep going", {},
                )
            }
        },
    )

    private val dockApps = listOf(
        DockApp("com.netflix.ninja", "Netflix"), DockApp("com.google.android.youtube.tv", "YouTube"),
        DockApp("com.amazon.amazonvideo.livingroom", "Prime Video"), DockApp("com.droidlogic.tvinput", "Live TV"),
        DockApp("com.spotify.tv.android", "Spotify"), DockApp("com.plexapp.android", "Plex"), DockApp("org.videolan.vlc", "VLC"),
    )

    val all: List<Sample> get() = components + Screens.all
}
