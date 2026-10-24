package tv.cleanyour.ui.design.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.component.ActionColumn
import tv.cleanyour.ui.design.component.AdbAllowMirror
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CheckLine
import tv.cleanyour.ui.design.component.ChipMark
import tv.cleanyour.ui.design.component.ChoiceList
import tv.cleanyour.ui.design.component.CodeMatchCard
import tv.cleanyour.ui.design.component.CollapsedGroupsCard
import tv.cleanyour.ui.design.component.CountdownConfirm
import tv.cleanyour.ui.design.component.CytIconButton
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.DiffGroupSection
import tv.cleanyour.ui.design.component.DiscoveryChip
import tv.cleanyour.ui.design.component.GlassCheckMark
import tv.cleanyour.ui.design.component.GlassChip
import tv.cleanyour.ui.design.component.GroupTone
import tv.cleanyour.ui.design.component.InfoChip
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.ModuleHero
import tv.cleanyour.ui.design.component.NumeralHeadline
import tv.cleanyour.ui.design.component.Overline
import tv.cleanyour.ui.design.component.PlanSummaryCard
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.SafetyLine
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.SheetHeader
import tv.cleanyour.ui.design.component.SheetSurface
import tv.cleanyour.ui.design.component.SmallGlassButton
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.component.StepCounter
import tv.cleanyour.ui.design.component.SwitchRow
import tv.cleanyour.ui.design.component.TertiaryButton
import tv.cleanyour.ui.design.component.TitleSize
import tv.cleanyour.ui.design.component.TvStatus
import tv.cleanyour.ui.design.component.TvStatusChip
import tv.cleanyour.ui.design.component.UndoSnackbar
import tv.cleanyour.ui.design.component.WelcomeIllustration
import tv.cleanyour.ui.design.component.rememberEmphasis
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

// The eight approved mockups (mockups/index.html) rebuilt from the components, 360 x 780 dp, with a
// 32 dp band where the status bar sits. They prove the components compose into the designed
// screens; the real screens are W8's.
@OptIn(ExperimentalLayoutApi::class)
object Screens {
    @Composable
    private fun StatusBand() = Spacer(Modifier.height(32.dp))

    val all: List<Sample> = listOf(
        Sample("s01_welcome", screen = true, reducedMotion = true) {
            ScreenScaffold(
                Glow.Brand,
                topBar = { StatusBand(); Overline("Clean Your TV", Modifier.padding(start = 20.dp, top = 28.dp)) },
                bottomBar = {
                    ActionColumn { PrimaryButton("Start", {}); TertiaryButton("I've set up a TV before", {}) }
                },
            ) {
                WelcomeIllustration(Modifier.padding(top = 28.dp))
                ScreenTitle("Your TV, tidied up.", Modifier.padding(top = 32.dp), size = TitleSize.DISPLAY)
                Lead("Switch off the clutter, keep the apps you use, and get a calm home screen. Five minutes. Everything can be put back.", Modifier.padding(top = 12.dp))
                DiscoveryChip("Found Mi TV 4A", found = true, secondary = "(Living room)", modifier = Modifier.padding(top = 20.dp))
            }
        },
        Sample("s02_which_tv", screen = true) {
            ScreenScaffold(
                Glow.Tv,
                topBar = { StatusBand(); CytTopBar(onBack = {}, center = { StepCounter(1, 5) }, trailing = { CytIconButton(CytIcons.Help, "Help", {}) }) },
                bottomBar = { ActionColumn { PrimaryButton("Use Mi TV 4A", {}); TertiaryButton("Enter the TV's address", {}) } },
            ) {
                ScreenTitle("Which TV?", Modifier.padding(top = 20.dp))
                Lead("Not sure? The TV you pick will show a message on its screen in a moment.", Modifier.padding(top = 12.dp))
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Still looking", mark = ChipMark.SPINNER)
                }
                Overline("Found 2 TVs", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp))
                ChoiceList(Fx.tvs, "mitv", {})
                Banner("Phone and TV must be on the same Wi-Fi.", BannerTone.INFO, Modifier.padding(top = 20.dp))
            }
        },
        Sample("s03_allow", screen = true) {
            ScreenScaffold(
                Glow.Tv,
                topBar = { StatusBand(); CytTopBar(onBack = {}, center = { TvStatusChip("Mi TV 4A", TvStatus.WAITING) }) },
                bottomBar = { SecondaryButton("I don't see it on the TV", {}) },
            ) {
                StepCounter(2, 5, Modifier.padding(top = 12.dp))
                ScreenTitle("Look at the TV", Modifier.padding(top = 8.dp))
                Lead(rememberEmphasis("The TV is asking whether to trust this phone. Tick \"Always allow\", then choose OK.", "\"Always allow\"", "OK."), Modifier.padding(top = 12.dp))
                AdbAllowMirror(Fx.FINGERPRINT, Modifier.padding(top = 24.dp))
                CodeMatchCard("The code on the TV ends in", "That is this phone.", "7F:2A", Modifier.padding(top = 24.dp))
                Row(Modifier.padding(top = 20.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spinner()
                    androidx.compose.material3.Text("Waiting for your tap on the TV...", color = CytTheme.colors.textPrimary, style = CytTheme.type.bodyMedium)
                }
            }
        },
        Sample("s04_what_we_found", screen = true, reducedMotion = true) {
            ScreenScaffold(
                Glow.Tv,
                topBar = { StatusBand(); CytTopBar(onBack = {}, center = { TvStatusChip("Mi TV 4A", TvStatus.CONNECTED) }) },
                bottomBar = { ActionColumn { PrimaryButton("Make it clean", {}); TertiaryButton("Customise", {}) } },
            ) {
                StepCounter(3, 5, Modifier.padding(top = 8.dp), extra = "We found")
                NumeralHeadline(23, "things to switch off", Modifier.padding(top = 6.dp))
                PlanSummaryCard(Fx.summary, "See the list", {}, Modifier.padding(top = 20.dp))
                androidx.compose.material3.Text(
                    rememberEmphasis("We keep everything you use: Netflix, YouTube, Prime Video, Live TV and HDMI, the remote, the keyboard and software updates.", "Netflix, YouTube, Prime Video, Live TV and HDMI"),
                    Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp), color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyMedium,
                )
                SafetyLine("A restore point is saved first, so everything can be put back.", Modifier.padding(start = 4.dp, top = 14.dp))
            }
        },
        Sample("s05_can_you_see_it", screen = true) {
            ScreenScaffold(
                Glow.Tv, scrollable = false,
                topBar = { StatusBand(); CytTopBar(center = { TvStatusChip("Mi TV 4A", TvStatus.CONNECTED) }) },
            ) {
                CountdownConfirm(
                    overline = "4 of 5 · Look at the TV", title = "Can you see it?",
                    thumbnail = Fx.homeShot, thumbnailDescription = "The TV shows the new home screen with four apps in the dock",
                    sentence = "The new home screen, with Netflix, YouTube, Prime Video and Live TV along the bottom.",
                    instruction = "Press Home on the remote once.", liveCheck = "Home opens the new screen", liveCheckPassed = true,
                    remainingMs = 84_000, totalMs = 90_000,
                    timeoutSentence = "If we don't hear from you, we put the old home screen back on our own. Nothing to worry about.",
                    yesLabel = "Yes, I see it", noLabel = "No, put it back", onYes = {}, onNo = {},
                )
            }
        },
        Sample("s06_done", screen = true) {
            ScreenScaffold(
                Glow.Done,
                topBar = { StatusBand(); CytTopBar(center = { TvStatusChip("Mi TV 4A", TvStatus.CONNECTED) }) },
                bottomBar = { PrimaryButton("Finish", {}) },
            ) {
                GlassCheckMark(Modifier.padding(top = 16.dp, start = 6.dp))
                ScreenTitle("Your TV is ready", Modifier.padding(top = 22.dp), size = TitleSize.DISPLAY)
                Lead("23 things switched off. New home screen on. Everything can be put back.", Modifier.padding(top = 8.dp))
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckLine("Restore point saved on this phone (19 Oct, 14:32)")
                    CheckLine("Debugging on the TV switches off when you tap Finish")
                }
                ListGroup(Modifier.padding(top = 18.dp), tone = GroupTone.RAISED) {
                    ListRow("Restart the TV to check it all sticks", subtitle = "Optional", trailing = { SmallGlassButton("Restart", {}) })
                }
                ListGroup(Modifier.padding(top = 10.dp)) {
                    ListRow("Add a card to the home screen", subtitle = "Optional. You can add cards later in Manage.", onClick = {})
                    ListDivider()
                    ListRow("Save a restore note for the family", onClick = {})
                }
            }
        },
        Sample("s07_module_retro", screen = true) {
            ScreenScaffold(
                Glow.Module(Fx.retro),
                topBar = { StatusBand(); CytTopBar(onBack = {}) },
                bottomBar = { PrimaryButton("Add to Mi TV 4A", {}) },
            ) {
                ModuleHero("Retro", "NES games", Fx.retro, badge = "Its card on your TV")
                ScreenTitle("Retro", Modifier.padding(top = 16.dp))
                androidx.compose.material3.Text(
                    "NES games on the TV. Adds a Retro card that opens a library of free, legally shared games. Works with the remote.",
                    Modifier.padding(top = 4.dp), color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyMedium,
                )
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip("Community"); InfoChip("Open source"); InfoChip("4 MB")
                }
                Overline("What it can do on the TV", Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CheckLine("Uses the internet to fetch game files", emphasised = true)
                    CheckLine("Reads USB storage, for your own games", emphasised = true)
                    CheckLine("No background service · No tracking", state = tv.cleanyour.ui.design.component.CheckState.NONE)
                }
                Overline("Who made it", Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
                androidx.compose.material3.Text(
                    rememberEmphasis("udhaykumarbala on GitHub · v0.1.0\nKey 6966 ed15 1f52, the same as on the TV\nChecked automatically. Not yet reviewed by a person.", "udhaykumarbala", "6966 ed15 1f52"),
                    Modifier.padding(horizontal = 4.dp), color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyMedium,
                )
            }
        },
        Sample("s08_customise_sheet", screen = true) {
            Box(Modifier.fillMaxSize()) {
                ScreenScaffold(Glow.Tv, topBar = { StatusBand(); CytTopBar(onBack = {}, center = { TvStatusChip("Mi TV 4A", TvStatus.CONNECTED) }) }) {}
                Box(Modifier.fillMaxSize().background(CytTheme.colors.scrim))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(690.dp)) {
                    SheetSurface(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SheetHeader("Customise", trailing = rememberEmphasis("22 of 23 off", "22"), subtitle = "Nothing is deleted; \"off\" means asleep.")
                        DiffGroupSection(Fx.adverts, {}, Modifier.padding(top = 16.dp), showPackageNames = true, visibleCount = 2, onShowMore = {})
                        CollapsedGroupsCard(Fx.collapsed(), {}, Modifier.padding(top = 10.dp))
                        Overline("Afterwards", Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp))
                        ListGroup { SwitchRow("Keep debugging on", false, {}, subtitle = "Others on your Wi-Fi could ask to connect", warning = true) }
                    }
                }
                UndoSnackbar("Mi TV+ promos will be kept.", {}, Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 28.dp))
            }
        },
    )
}
