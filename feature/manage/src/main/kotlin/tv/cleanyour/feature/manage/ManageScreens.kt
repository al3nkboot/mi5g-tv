@file:OptIn(ExperimentalMaterial3Api::class)

package tv.cleanyour.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import tv.cleanyour.ui.design.component.rejectObscuredTouches
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import tv.cleanyour.core.engine.PauseReason
import tv.cleanyour.core.flows.AppConfirm
import tv.cleanyour.core.flows.AppSettingsIntent
import tv.cleanyour.core.flows.AppSettingsState
import tv.cleanyour.core.flows.LeftoversIntent
import tv.cleanyour.core.flows.LeftoversState
import tv.cleanyour.core.flows.ManageIntent
import tv.cleanyour.core.flows.ManageSection
import tv.cleanyour.core.flows.ManageState
import tv.cleanyour.core.flows.Confirmations
import tv.cleanyour.core.flows.OpenWrite
import tv.cleanyour.core.flows.OpenWriteAction
import tv.cleanyour.core.flows.Reach
import tv.cleanyour.core.flows.SwitchedOffIntent
import tv.cleanyour.core.flows.SwitchedOffState
import tv.cleanyour.core.flows.TvSettingsIntent
import tv.cleanyour.core.flows.AnimationScaleStatus
import tv.cleanyour.core.flows.CastMediaStatus
import tv.cleanyour.core.flows.CastUrlStatus
import tv.cleanyour.core.flows.PowerToolStatus
import tv.cleanyour.core.flows.RemoteStatus
import tv.cleanyour.core.flows.ScreenshotStatus
import tv.cleanyour.core.flows.SideloadStatus
import tv.cleanyour.core.flows.SpeedBoostStatus
import tv.cleanyour.core.flows.TvAppActionKind
import tv.cleanyour.core.flows.TvAppActionStatus
import tv.cleanyour.core.flows.TvAppFilter
import tv.cleanyour.core.flows.TvAppManagerIntent
import tv.cleanyour.core.flows.TvAppManagerState
import tv.cleanyour.core.flows.TvInstalledApp
import tv.cleanyour.core.flows.TvSettingsState
import tv.cleanyour.core.flows.TvToolsIntent
import tv.cleanyour.core.flows.TvToolsState
import tv.cleanyour.core.flows.DownloadCatalog
import tv.cleanyour.core.model.TvAnimationScale
import tv.cleanyour.core.model.TvKeyEvent
import tv.cleanyour.ui.design.component.AppTile
import tv.cleanyour.ui.design.component.ConfirmQuestion
import tv.cleanyour.core.flows.UpdateCheck
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.planner.PlanKind
import tv.cleanyour.ui.design.component.ActionColumn
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CheckLine
import tv.cleanyour.ui.design.component.ChoiceList
import tv.cleanyour.ui.design.component.ChoiceOption
import tv.cleanyour.ui.design.component.CytBottomSheet
import tv.cleanyour.ui.design.component.CytIconButton
import tv.cleanyour.ui.design.component.CytTextField
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.DiffGroup
import tv.cleanyour.ui.design.component.DiffGroupSection
import tv.cleanyour.ui.design.component.DiffItem
import tv.cleanyour.ui.design.component.GlassChip
import tv.cleanyour.ui.design.component.Gap
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.LinkRow
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.MonospaceBlock
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.ProgressBar
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.SheetHeader
import tv.cleanyour.ui.design.component.SmallGlassButton
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.component.StepCard
import tv.cleanyour.ui.design.component.StopCard
import tv.cleanyour.ui.design.component.SwitchRow
import tv.cleanyour.ui.design.component.TertiaryButton
import tv.cleanyour.ui.design.component.TvCard
import tv.cleanyour.ui.design.component.TvStatus
import tv.cleanyour.ui.design.component.TvStatusChip
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

// Screens M1, M1a, M4, M5, M7 and M8 (PRODUCT-SPEC 4, UX 4.3, 4.11, 4.13, 6.1), built from the
// :ui:design components (ui/design/API.md). Each sheet keeps its body in an internal *Content
// composable, so the screenshot tests can draw it on SheetSurface (a modal window is not captured).

fun shortDate(ms: Long, locale: java.util.Locale): String = SimpleDateFormat("d MMM", locale).format(Date(ms))
fun shortDateTime(ms: Long, locale: java.util.Locale): String = SimpleDateFormat("d MMM, HH:mm", locale).format(Date(ms))

private val titleTop = Modifier.padding(top = 20.dp)

// M1 Manage: the app's home once a TV exists. Nothing is more than two taps away.
@Composable
fun ManageScreen(state: ManageState, onIntent: (ManageIntent) -> Unit, modifier: Modifier = Modifier) {
    val tv = state.current
    val locale = LocalConfiguration.current.locales[0]
    ScreenScaffold(Glow.Tv, modifier, topBar = {
        CytTopBar(
            center = { if (tv != null) TvStatusChip(tv.name, tv.chip.toTvStatus()) },
            trailing = { CytIconButton(CytIcons.Settings, stringResource(R.string.manage_settings_cd), { onIntent(ManageIntent.AppSettings) }) },
        )
    }) {
        ScreenTitle(stringResource(R.string.manage_title), titleTop)
        if (state.offline) {
            Banner(
                stringResource(R.string.manage_offline_banner), BannerTone.NEUTRAL, Modifier.padding(top = 16.dp), icon = CytIcons.WifiOff,
                actionLabel = if (state.reconnecting) null else stringResource(R.string.manage_reconnect), onAction = { onIntent(ManageIntent.Reconnect) },
            )
            if (state.reconnecting) LiveLine(stringResource(R.string.manage_reconnecting))
        }
        if (tv != null) {
            val room = tv.room
            val stateWord = stringResource(if (tv.clean) R.string.manage_state_clean else R.string.manage_state_original)
            val checked = msg(R.string.manage_checked, "date" to shortDate(tv.checkedAtMs, locale))
            TvCard(
                name = if (room != null) msg(R.string.manage_tv_title, "tv" to tv.name, "room" to room) else msg(R.string.manage_tv_title_noroom, "tv" to tv.name),
                statusLine = "$stateWord. $checked.",
                thumbnail = rememberPng(tv.thumbnail),
                thumbnailDescription = msg(R.string.manage_thumbnail_cd, "tv" to tv.name),
                modifier = Modifier.padding(top = 20.dp),
                level = stringResource(if (tv.level == Level.CUSTOM) R.string.manage_level_custom else R.string.manage_level_simple),
                onClick = { onIntent(ManageIntent.RefreshThumbnail) },
            )
            // 1.6 item 1: the health dashboard holds the single clean button (navigation into the
            // wizard, whose own "Make it clean" is the guarded action, R5-W8 O1).
            HealthDashboard(state, tv, onIntent)
            // 0.3.2 B1: an unlisted TV (no exact profile) invites the person to help add it.
            if (tv.helpAddTv) {
                Banner(stringResource(R.string.manage_help_add_tv_banner), BannerTone.INFO, Modifier.padding(top = 16.dp),
                    actionLabel = stringResource(R.string.manage_help_add_tv), onAction = { onIntent(ManageIntent.HelpAddTv) })
            }
            ListGroup(Modifier.padding(top = 16.dp)) {
                // 0.3.3 D2: without the new home screen there is no dock to name; the row opens the reason.
                ListRow(stringResource(R.string.manage_row_home),
                    subtitle = if (tv.ownHome) stringResource(R.string.manage_row_home_own) else tv.dockNames.joinToString(", ").ifEmpty { null },
                    onClick = { onIntent(ManageIntent.Open(ManageSection.HOME_SCREEN)) })
                ListDivider()
                // STORES-PLAN 1: the Play build has no add-ons, so no row that leads to them.
                if (state.addOns) {
                    ListRow(stringResource(R.string.manage_row_cards), onClick = { onIntent(ManageIntent.Open(ManageSection.MODULES)) })
                    ListDivider()
                }
                ListRow(msg(R.string.manage_row_switched_off, "count" to tv.switchedOff), subtitle = stringResource(R.string.manage_row_switched_off_sub),
                    onClick = { onIntent(ManageIntent.Open(ManageSection.SWITCHED_OFF)) })
                ListDivider()
                ListRow(stringResource(R.string.manage_row_leftovers), subtitle = stringResource(R.string.manage_row_leftovers_sub),
                    onClick = { onIntent(ManageIntent.Open(ManageSection.LEFTOVERS)) })
                ListDivider()
                ListRow(stringResource(R.string.manage_row_restore), subtitle = tv.restorePointAtMs?.let { msg(R.string.manage_row_restore_sub, "date" to shortDateTime(it, locale)) },
                    onClick = { onIntent(ManageIntent.Open(ManageSection.RESTORE_POINT)) })
                ListDivider()
                ListRow(stringResource(R.string.manage_row_tools), subtitle = stringResource(R.string.manage_row_tools_sub),
                    onClick = { onIntent(ManageIntent.Open(ManageSection.TV_TOOLS)) })
                ListDivider()
                ListRow(stringResource(R.string.manage_row_apps), subtitle = stringResource(R.string.manage_row_apps_sub),
                    onClick = { onIntent(ManageIntent.Open(ManageSection.TV_APPS)) })
                ListDivider()
                ListRow(stringResource(R.string.manage_row_tvsettings), onClick = { onIntent(ManageIntent.Open(ManageSection.TV_SETTINGS)) })
            }
        }
        val others = state.cards.filter { it.id != tv?.id }
        ListGroup(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
            others.forEach { other ->
                ListRow(other.name, subtitle = other.room, onClick = { onIntent(ManageIntent.Select(other.id)) })
                ListDivider()
            }
            LinkRow(stringResource(R.string.manage_add_tv), { onIntent(ManageIntent.SetUpAnother) }, chevron = false, minHeight = 56.dp)
        }
    }
}

// M1a: the debugging step for a returning write; it advances on its own when the port opens
// (UX 4.3). The app lays it over whatever screen asked (TvAccess.reach).
@Composable
fun DebugStepSheet(reach: Reach, onCancel: () -> Unit) {
    val name = when (reach) {
        is Reach.NeedsDebugging -> reach.tvName
        is Reach.Connecting -> reach.tvName
        is Reach.AwaitingAllow -> reach.tvName
        is Reach.WrongNetwork -> reach.tvName
        Reach.Idle -> return
    }
    CytBottomSheet(onCancel) { DebugStepContent(name, connecting = reach is Reach.Connecting, onCancel) }
}

@Composable
internal fun ColumnScope.DebugStepContent(tvName: String, connecting: Boolean, onCancel: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { TvStatusChip(tvName, TvStatus.WAITING) }
    StepCard(stringResource(R.string.debugstep_title), stringResource(R.string.debugstep_body), Modifier.padding(top = 16.dp))
    Body(stringResource(R.string.debugstep_note), Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp))
    LiveLine(stringResource(if (connecting) R.string.debugstep_connecting else R.string.debugstep_waiting))
    TertiaryButton(stringResource(R.string.action_cancel), onCancel, Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp), quiet = true)
}

// A Manage write that paused left its run open (R5-W8 O2): every later write on that TV would be
// refused, so the app lays this card over whatever screen asked. A setup run opens the wizard's
// resume card instead, which owns the home screen check.
@Composable
fun OpenWriteSheet(open: OpenWrite, onAction: (OpenWriteAction) -> Unit, onOpenSetup: () -> Unit, onDismiss: () -> Unit, onReport: () -> Unit = {}) {
    CytBottomSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            OpenWriteContent(open, onAction, onOpenSetup, onDismiss, onReport = onReport)
        }
    }
}

@Composable
internal fun ColumnScope.OpenWriteContent(
    open: OpenWrite, onAction: (OpenWriteAction) -> Unit, onOpenSetup: () -> Unit, onDismiss: () -> Unit,
    initiallyAsking: OpenWriteAction? = null, onReport: () -> Unit = {},
) {
    val setup = open.kind == PlanKind.SETUP
    val body = when {
        open.notTaken -> stringResource(R.string.openwrite_not_taken)
        setup -> msg(R.string.openwrite_body_setup, "tv" to open.tvName)
        open.reason == PauseReason.CONNECTION_LOST -> stringResource(R.string.openwrite_body_lost)
        else -> stringResource(R.string.openwrite_body)
    }
    val title = msg(R.string.openwrite_title, "tv" to open.tvName)
    Gap(8.dp)
    if (setup) {
        StopCard(title, body,
            primaryLabel = stringResource(R.string.openwrite_open_setup), onPrimary = onOpenSetup,
            tertiaryLabel = stringResource(R.string.openwrite_later), onTertiary = onDismiss,
            // 0.3.2 B2: a setup that would not finish is worth a report (issue #2).
            detailsLabel = stringResource(R.string.openwrite_report), onDetails = onReport,
            primaryGuarded = false)   // navigation: it only opens the wizard's resume card
    } else {
        // "Undo what we did" and "Stop here" ask first, in this same sheet; the action is sent on yes.
        var asking by rememberSaveable(open.planId.value) { mutableStateOf(initiallyAsking) }
        val pending = asking
        val question = pending?.let { Confirmations.forOpenWrite(it) }
        if (pending != null && question != null && !open.busy) {
            ConfirmQuestionFor(question, onConfirm = { asking = null; onAction(pending) }, onCancel = { asking = null })
            return
        }
        fun act(a: OpenWriteAction) { if (Confirmations.forOpenWrite(a) != null) asking = a else onAction(a) }
        StopCard(title, body,
            primaryLabel = if (open.busy) null else stringResource(R.string.openwrite_carry_on), onPrimary = { act(OpenWriteAction.CARRY_ON) },
            secondaryLabel = if (open.busy) null else stringResource(R.string.openwrite_undo), onSecondary = { act(OpenWriteAction.UNDO) },
            tertiaryLabel = if (open.busy) null else stringResource(R.string.openwrite_stop), onTertiary = { act(OpenWriteAction.STOP_HERE) },
            busyText = if (open.busy) stringResource(R.string.openwrite_busy) else null)
    }
}

// M4 Switched off: "Put back" is one op with a 5 s undo snackbar (the app shows the snackbar).
@Composable
fun SwitchedOffScreen(state: SwitchedOffState, onIntent: (SwitchedOffIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) }) {
        ScreenTitle(msg(R.string.switchedoff_title, "count" to state.count), titleTop)
        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) { Spinner(size = 24.dp) }
            return@ScreenScaffold
        }
        if (state.groups.isEmpty() && state.askGroups.isEmpty()) {
            Lead(stringResource(R.string.switchedoff_empty), Modifier.padding(top = 12.dp))
            Body(stringResource(R.string.switchedoff_empty_sub), Modifier.padding(top = 8.dp))
        } else {
            Lead(stringResource(R.string.switchedoff_intro), Modifier.padding(top = 12.dp))
        }
        state.groups.forEach { g ->
            SectionLabel(g.title.ifEmpty { stringResource(R.string.switchedoff_group_other) })
            ListGroup {
                g.rows.forEachIndexed { i, r ->
                    if (i > 0) ListDivider()
                    val cd = msg(R.string.switchedoff_put_back_cd, "name" to r.name)
                    ListRow(r.name, subtitle = r.description, trailing = {
                        if (state.busy == r.pkg) Box(Modifier.padding(horizontal = 16.dp)) { Spinner() }
                        else TertiaryButton(stringResource(R.string.switchedoff_put_back), { onIntent(SwitchedOffIntent.PutBack(r.pkg)) },
                            Modifier.semantics { contentDescription = cd }, enabled = state.busy == null)
                    })
                }
            }
        }
        // Custom adds the ask groups with Off / Kept pills and the package names in grey (D2).
        state.askGroups.forEach { g ->
            val group = DiffGroup(g.id, g.title, g.rows.map { DiffItem(it.pkg.value, it.name, it.description.orEmpty(), it.pkg.value, it.off) })
            DiffGroupSection(
                group, { item -> if (state.busy == null) onIntent(SwitchedOffIntent.SetOff(PackageName(item.key), !item.off)) },
                Modifier.padding(top = 20.dp), showPackageNames = true,
            )
        }
        Gap(24.dp)
    }
}

// M5 Check for leftovers (D22).
@Composable
fun LeftoversScreen(state: LeftoversState, onIntent: (LeftoversIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val bottom: (@Composable ColumnScope.() -> Unit)? = when (state) {
        is LeftoversState.Found -> ({
            ActionColumn {
                PrimaryButton(stringResource(R.string.leftovers_switch_off), { onIntent(LeftoversIntent.SwitchOff) })
                TertiaryButton(stringResource(R.string.leftovers_not_now), { onIntent(LeftoversIntent.NotNow) })
            }
        })
        LeftoversState.Failed -> ({ SecondaryButton(stringResource(R.string.leftovers_title), { onIntent(LeftoversIntent.CheckAgain) }) })
        else -> null
    }
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) }, bottomBar = bottom) {
        ScreenTitle(stringResource(R.string.leftovers_title), titleTop)
        when (state) {
            LeftoversState.Checking -> LiveLine(stringResource(R.string.leftovers_checking))
            is LeftoversState.Found -> {
                Lead(msg(R.string.leftovers_found, "count" to state.names.size), Modifier.padding(top = 12.dp))
                ListGroup(Modifier.padding(top = 20.dp)) {
                    state.names.forEachIndexed { i, name ->
                        if (i > 0) ListDivider()
                        ListRow(name)
                    }
                }
            }
            LeftoversState.Nothing -> Lead(stringResource(R.string.leftovers_none), Modifier.padding(top = 12.dp))
            is LeftoversState.Working -> {
                LiveLine(stringResource(R.string.leftovers_checking))
                ProgressBar(state.percent / 100f, Modifier.padding(top = 16.dp))
            }
            is LeftoversState.Done -> CheckLine(stringResource(R.string.leftovers_done), Modifier.padding(top = 20.dp, start = 4.dp))
            LeftoversState.Failed -> Lead(stringResource(R.string.leftovers_error), Modifier.padding(top = 12.dp))
        }
    }
}

// M7 TV settings (D46; "Keep debugging on" is Expert, 0.2, and absent). The level choice carries
// the one-line explanation of each level.
@Composable
fun TvSettingsScreen(state: TvSettingsState, onIntent: (TvSettingsIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    var name by rememberSaveable(state.name) { mutableStateOf(state.name) }
    var room by rememberSaveable(state.room) { mutableStateOf(state.room.orEmpty()) }
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) }) {
        ScreenTitle(stringResource(R.string.tvsettings_title), titleTop)
        SectionLabel(stringResource(R.string.tvsettings_name_room))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CytTextField(name, { name = it.take(40) }, stringResource(R.string.tvsettings_name_label))
            CytTextField(room, { room = it.take(40) }, stringResource(R.string.tvsettings_room_label))
        }
        if (name != state.name || room != state.room.orEmpty()) {
            SecondaryButton(stringResource(R.string.action_done), { onIntent(TvSettingsIntent.Rename(name, room)) }, Modifier.padding(top = 12.dp), enabled = name.isNotBlank())
        }
        SectionLabel(stringResource(R.string.tvsettings_level_label))
        ChoiceList(
            listOf(
                ChoiceOption(Level.SIMPLE.name, stringResource(R.string.tvsettings_level_simple), stringResource(R.string.manage_level_simple_note), icon = null),
                ChoiceOption(Level.CUSTOM.name, stringResource(R.string.tvsettings_level_custom), stringResource(R.string.manage_level_custom_note), icon = null),
            ),
            state.level.name,
            { onIntent(TvSettingsIntent.SetLevel(Level.valueOf(it.key))) },
        )
        Body(stringResource(R.string.tvsettings_level_note), Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp))
        ListGroup(Modifier.padding(top = 20.dp)) {
            ListRow(stringResource(R.string.tvsettings_address), subtitle = state.lastSeenAtMs?.let { msg(R.string.tvsettings_last_seen, "date" to shortDateTime(it, locale)) },
                value = state.address)
        }
        SecondaryButton(stringResource(R.string.tvsettings_forget), { onIntent(TvSettingsIntent.AskForget) }, Modifier.padding(top = 24.dp), enabled = !state.forgetting)
        Body(stringResource(R.string.tvsettings_forget_explain), Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 24.dp))
    }
    if (state.confirmingForget) CytBottomSheet({ onIntent(TvSettingsIntent.CancelForget) }) { ForgetContent(state.name, onIntent) }
}

@Composable
internal fun ColumnScope.ForgetContent(tvName: String, onIntent: (TvSettingsIntent) -> Unit) {
    SheetHeader(msg(R.string.tvsettings_forget_title, "tv" to tvName))
    Body(stringResource(R.string.tvsettings_forget_explain), Modifier.padding(top = 8.dp))
    ActionColumn(Modifier.padding(top = 20.dp)) {
        PrimaryButton(stringResource(R.string.tvsettings_forget_confirm), { onIntent(TvSettingsIntent.Forget) })
        TertiaryButton(stringResource(R.string.action_cancel), { onIntent(TvSettingsIntent.CancelForget) }, quiet = true)
    }
}

// M8 App settings (D12, F2, F13). The Reset my key confirmation is FLAG_SECURE (D35, set by the app).
@Composable
fun AppSettingsScreen(
    state: AppSettingsState,
    onIntent: (AppSettingsIntent) -> Unit,
    onBack: () -> Unit,
    onOpenRelease: () -> Unit,
    modifier: Modifier = Modifier,
    onSupportPayPal: (() -> Unit)? = null,
    onOpenDeveloper: (() -> Unit)? = null,
) {
    var licences by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) }) {
        ScreenTitle(stringResource(R.string.appsettings_title), titleTop)
        ListGroup(Modifier.padding(top = 20.dp)) {
            SwitchRow(stringResource(R.string.appsettings_large_text), state.prefs.largeTextFollowsSystem, { onIntent(AppSettingsIntent.SetLargeText(it)) },
                subtitle = stringResource(R.string.appsettings_large_text_note))
            ListDivider()
            SwitchRow(stringResource(R.string.appsettings_haptics), state.prefs.haptics, { onIntent(AppSettingsIntent.SetHaptics(it)) },
                subtitle = stringResource(R.string.appsettings_haptics_note))
        }
        SectionLabel(stringResource(R.string.appsettings_about))
        ListGroup {
            ListRow(msg(R.string.appsettings_version, "version" to state.version))
            ListDivider()
            ListRow(stringResource(R.string.appsettings_launcher_pin_label))
            MonospaceBlock(
                listOf(msg(R.string.appsettings_launcher_pin_line, "package" to state.launcherPkg, "version" to state.launcherVersion, "digest" to state.launcherDigest)),
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            ListDivider()
            ListRow(stringResource(R.string.appsettings_key_label), subtitle = stringResource(when (state.keyBacking) {
                "STRONGBOX" -> R.string.appsettings_key_strongbox
                "TEE" -> R.string.appsettings_key_tee
                else -> R.string.appsettings_key_wrapped
            }))
            if (state.keyFingerprint.isNotEmpty()) MonospaceBlock(listOf(state.keyFingerprint), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
            // STORES-PLAN 1: the Play build is updated by Google Play and has no update check.
            if (state.updateCheck) {
                ListDivider()
                val update = state.update
                ListRow(stringResource(R.string.appsettings_check_updates), subtitle = when (update) {
                    UpdateCheck.Checking -> stringResource(R.string.appsettings_checking)
                    is UpdateCheck.Available -> msg(R.string.appsettings_update_available, "version" to update.version)
                    UpdateCheck.Latest -> stringResource(R.string.appsettings_up_to_date)
                    UpdateCheck.Failed -> stringResource(R.string.appsettings_update_error)
                    UpdateCheck.Idle -> null
                }, trailing = if (update == UpdateCheck.Checking) ({ Spinner() }) else null,
                    onClick = { if (update is UpdateCheck.Available) onOpenRelease() else onIntent(AppSettingsIntent.CheckUpdates) })
            }
        }
        ListGroup(Modifier.padding(top = 12.dp)) {
            if (state.updateCheck) {
                ListRow(
                    stringResource(R.string.appsettings_download_github),
                    subtitle = stringResource(R.string.appsettings_download_github_note),
                    onClick = onOpenRelease,
                )
                ListDivider()
            }
            if (onSupportPayPal != null) {
                ListRow(
                    stringResource(R.string.appsettings_support_paypal),
                    subtitle = stringResource(R.string.appsettings_support_paypal_note),
                    onClick = onSupportPayPal,
                )
                ListDivider()
            }
            if (onOpenDeveloper != null) {
                ListRow(
                    stringResource(R.string.appsettings_developer_x),
                    subtitle = "x.com/midroid5G",
                    onClick = onOpenDeveloper,
                )
                ListDivider()
            }
            ListRow(stringResource(R.string.appsettings_licences), onClick = { licences = true })
            ListDivider()
            ListRow(stringResource(R.string.appsettings_privacy), onClick = { privacy = true })
            ListDivider()
            ListRow(stringResource(R.string.appsettings_report), subtitle = stringResource(R.string.appsettings_report_note),
                onClick = { onIntent(AppSettingsIntent.ReportProblem) })
        }
        ListGroup(Modifier.padding(top = 12.dp, bottom = 24.dp)) {
            ListRow(stringResource(R.string.appsettings_reset_key), subtitle = stringResource(R.string.appsettings_reset_key_note),
                onClick = { onIntent(AppSettingsIntent.Ask(AppConfirm.RESET_KEY)) })
            ListDivider()
            ListRow(stringResource(R.string.appsettings_delete_all), subtitle = stringResource(R.string.appsettings_delete_all_note),
                onClick = { onIntent(AppSettingsIntent.Ask(AppConfirm.DELETE_EVERYTHING)) })
        }
    }
    if (licences) CytBottomSheet({ licences = false }) { LicencesContent() }
    if (privacy) CytBottomSheet({ privacy = false }) { PrivacyContent(online = state.updateCheck || state.addOns) }
    state.confirm?.let { what ->
        CytBottomSheet({ onIntent(AppSettingsIntent.CancelConfirm) }) { ConfirmContent(what, state.working, onIntent) }
    }
}

@Composable
internal fun ColumnScope.LicencesContent() {
    SheetHeader(stringResource(R.string.appsettings_licences))
    ListGroup(Modifier.padding(top = 16.dp)) {
        listOf(R.string.appsettings_licence_app, R.string.appsettings_licence_font, R.string.appsettings_licence_apache, R.string.appsettings_licence_bouncycastle)
            .forEachIndexed { i, id ->
                if (i > 0) ListDivider()
                ListRow(stringResource(id))
            }
    }
}

@Composable
internal fun ColumnScope.PrivacyContent(online: Boolean = true) {
    SheetHeader(stringResource(R.string.appsettings_privacy))
    // `online`: the build checks GitHub for updates or downloads add-ons from it (STORES-PLAN 1).
    Body(stringResource(if (online) R.string.appsettings_privacy_text else R.string.appsettings_privacy_text_offline), Modifier.padding(top = 8.dp),
        color = CytTheme.colors.textPrimary)
}

@Composable
internal fun ColumnScope.ConfirmContent(what: AppConfirm, working: Boolean, onIntent: (AppSettingsIntent) -> Unit) {
    val reset = what == AppConfirm.RESET_KEY
    SheetHeader(stringResource(if (reset) R.string.appsettings_reset_key_title else R.string.appsettings_delete_all_title))
    Body(stringResource(if (reset) R.string.appsettings_reset_key_note else R.string.appsettings_delete_all_note), Modifier.padding(top = 8.dp))
    ActionColumn(Modifier.padding(top = 20.dp)) {
        PrimaryButton(stringResource(if (reset) R.string.appsettings_reset_key_confirm else R.string.appsettings_delete_all_confirm),
            { onIntent(if (reset) AppSettingsIntent.ResetKey else AppSettingsIntent.DeleteEverything) }, enabled = !working)
        TertiaryButton(stringResource(R.string.action_cancel), { onIntent(AppSettingsIntent.CancelConfirm) }, quiet = true)
    }
}

// M9 TV remote and tools (v1.1.0 / v1.3.0 / v1.5.0): Bundled cards quick launch, Speed boost, Downloader codes & SmartTube, Button Mapper, Force Landscape, TV Banner, Quick Cast, D-Pad remote, screenshot, and APK sideloader.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvToolsScreen(state: TvToolsState, onIntent: (TvToolsIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sending = state.remoteStatus is RemoteStatus.Sending
    val boosting = state.speedBoostStatus is SpeedBoostStatus.Boosting
    val applyingScale = state.animationScaleStatus is AnimationScaleStatus.Applying
    val powerWorking = state.powerToolStatus is PowerToolStatus.Working
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) }) {
        ScreenTitle(stringResource(R.string.tvtools_title), titleTop)

        when (val ps = state.powerToolStatus) {
            is PowerToolStatus.Working -> LiveLine(msg(R.string.tvtools_power_working, "name" to ps.label))
            is PowerToolStatus.Done -> CheckLine(msg(R.string.tvtools_power_done, "name" to ps.label), Modifier.padding(top = 8.dp, start = 4.dp))
            is PowerToolStatus.Failed -> Body(msg(R.string.tvtools_power_failed, "name" to ps.label), Modifier.padding(top = 8.dp, start = 4.dp), color = CytTheme.colors.danger)
            PowerToolStatus.Idle -> {}
        }

        // Quick Launch Bundled Mi5G TV Cards (solves Xiaomi TV Stick / Stock Launcher)
        SectionLabel(stringResource(R.string.tvtools_cards_section))
        Body(stringResource(R.string.tvtools_cards_note), Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp))
        val bundledCards = listOf(
            Triple("tv-matches", "com.midroid.mi5gtv.matches", stringResource(R.string.tvtools_card_matches)),
            Triple("prayer-weather", "com.midroid.mi5gtv.prayer", stringResource(R.string.tvtools_card_prayer)),
            Triple("adguard-dns", "com.midroid.mi5gtv.dns", stringResource(R.string.tvtools_card_dns)),
            Triple("tv-files", "com.midroid.mi5gtv.files", stringResource(R.string.tvtools_card_files)),
            Triple("tv-speedtest", "com.midroid.mi5gtv.speedtest", stringResource(R.string.tvtools_card_speed)),
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            bundledCards.forEach { (modId, pkg, label) ->
                GlassChip(
                    text = label,
                    onClick = { if (!powerWorking) onIntent(TvToolsIntent.LaunchBundledCard(modId, pkg, label)) },
                )
            }
        }

        SectionLabel(stringResource(R.string.tvtools_perf_section))
        val perf = state.performanceStats
        if (perf != null) {
            ListGroup(Modifier.padding(bottom = 12.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = msg(R.string.tvtools_ram_status, "used" to perf.ramUsedGb, "total" to perf.ramTotalGb),
                        color = CytTheme.colors.textPrimary,
                        style = CytTheme.type.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                    ProgressBar(perf.ramFraction)
                    Gap(4.dp)
                    Text(
                        text = msg(R.string.tvtools_storage_status, "free" to perf.storageFreeGb, "total" to perf.storageTotalGb),
                        color = CytTheme.colors.textPrimary,
                        style = CytTheme.type.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                    ProgressBar(perf.storageUsedFraction)
                }
            }
        }
        PrimaryButton(
            text = stringResource(R.string.tvtools_speed_boost_btn),
            onClick = { onIntent(TvToolsIntent.RunSpeedBoost) },
            enabled = !boosting,
            loading = boosting,
        )
        when (val sb = state.speedBoostStatus) {
            SpeedBoostStatus.Boosting -> LiveLine(stringResource(R.string.tvtools_speed_boosting))
            is SpeedBoostStatus.Boosted -> CheckLine(
                msg(R.string.tvtools_speed_boosted, "count" to sb.stoppedApps),
                Modifier.padding(top = 8.dp, start = 4.dp),
            )
            SpeedBoostStatus.Failed -> Body(
                stringResource(R.string.tvtools_speed_failed),
                Modifier.padding(top = 8.dp, start = 4.dp),
                color = CytTheme.colors.danger,
            )
            SpeedBoostStatus.Idle -> {}
        }

        Body(stringResource(R.string.tvtools_anim_label), Modifier.padding(top = 14.dp, bottom = 8.dp, start = 4.dp, end = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteActionKey(
                text = stringResource(R.string.tvtools_anim_fast),
                onClick = { onIntent(TvToolsIntent.SetAnimationScale(TvAnimationScale.FAST_0_5X)) },
                modifier = Modifier.weight(1f),
                enabled = !applyingScale,
                accented = state.animationScale == TvAnimationScale.FAST_0_5X,
            )
            RemoteActionKey(
                text = stringResource(R.string.tvtools_anim_off),
                onClick = { onIntent(TvToolsIntent.SetAnimationScale(TvAnimationScale.OFF_0X)) },
                modifier = Modifier.weight(1f),
                enabled = !applyingScale,
                accented = state.animationScale == TvAnimationScale.OFF_0X,
            )
            RemoteActionKey(
                text = stringResource(R.string.tvtools_anim_default),
                onClick = { onIntent(TvToolsIntent.SetAnimationScale(TvAnimationScale.DEFAULT_1X)) },
                modifier = Modifier.weight(1f),
                enabled = !applyingScale,
                accented = state.animationScale == TvAnimationScale.DEFAULT_1X,
            )
        }
        when (state.animationScaleStatus) {
            AnimationScaleStatus.Applying -> LiveLine(stringResource(R.string.tvtools_anim_applying))
            is AnimationScaleStatus.Applied -> CheckLine(
                stringResource(R.string.tvtools_anim_applied),
                Modifier.padding(top = 8.dp, start = 4.dp),
            )
            AnimationScaleStatus.Failed -> Body(
                stringResource(R.string.tvtools_anim_failed),
                Modifier.padding(top = 8.dp, start = 4.dp),
                color = CytTheme.colors.danger,
            )
            AnimationScaleStatus.Idle -> {}
        }

        // 1.6 item 4: the Downloader catalog first; the free-form code or link sits under «متقدم».
        SectionLabel(stringResource(R.string.v16_catalog_title))
        Body(stringResource(R.string.v16_catalog_note), Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp))
        ListGroup {
            DownloadCatalog.ENTRIES.forEachIndexed { i, entry ->
                if (i > 0) ListDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = CytTheme.colors.textPrimary, style = CytTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("${entry.code} · ${entry.note}", color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyMedium)
                    }
                    SmallGlassButton(stringResource(R.string.v16_catalog_install), { if (!powerWorking) onIntent(TvToolsIntent.OpenInDownloader(entry.code)) })
                }
            }
        }
        var advanced by rememberSaveable { mutableStateOf(false) }
        TertiaryButton(
            stringResource(if (advanced) R.string.v16_advanced_hide else R.string.v16_advanced_show),
            { advanced = !advanced }, Modifier.padding(top = 8.dp),
        )
        if (advanced) {
        CytTextField(
            value = state.downloaderInput,
            onValueChange = { onIntent(TvToolsIntent.UpdateDownloaderInput(it)) },
            label = stringResource(R.string.tvtools_downloader_label),
        )
        val downloaderPresets = listOf(
            "كود 401740 (متجر هلا ويب)" to "401740",
            "SmartTube (يوتيوب بدون إعلانات وبدون Shorts)" to "https://github.com/yuliskov/SmartTube/releases/latest/download/SmartTube_stable_arm64-v8a.apk",
            "Downloader الرسمي" to "250931",
            "Yacine TV (البث الرياضي)" to "401740",
            "DMSS (كاميرات المراقبة)" to "https://play.google.com/store/apps/details?id=com.mm.android.DMSS",
            "ProtonVPN" to "https://play.google.com/store/apps/details?id=ch.protonvpn.android",
            "VLC Player" to "https://play.google.com/store/apps/details?id=org.videolan.vlc",
            "MX Player" to "https://play.google.com/store/apps/details?id=com.mxtech.videoplayer.ad",
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            downloaderPresets.forEach { (chipLabel, codeOrUrl) ->
                GlassChip(
                    text = chipLabel,
                    onClick = { onIntent(TvToolsIntent.UpdateDownloaderInput(codeOrUrl)) },
                )
            }
        }
        ActionColumn(Modifier.padding(top = 12.dp)) {
            PrimaryButton(
                stringResource(R.string.tvtools_downloader_open_btn),
                { onIntent(TvToolsIntent.OpenInDownloader()) },
                enabled = state.downloaderInput.isNotBlank() && !powerWorking,
            )
            SecondaryButton(
                stringResource(R.string.tvtools_downloader_install_btn),
                {
                    val target = state.downloaderInput.ifBlank { "https://github.com/yuliskov/SmartTube/releases/latest/download/SmartTube_stable_arm64-v8a.apk" }
                    onIntent(TvToolsIntent.DownloadAndInstallUrlApk(target, "tv-app.apk"))
                },
                enabled = !powerWorking && state.sideloadStatus !is SideloadStatus.Installing,
                guardObscured = true,
            )
        }
        }
        Banner(stringResource(R.string.tvtools_smarttube_tip), BannerTone.INFO, Modifier.padding(top = 12.dp))

        // 2) Button Mapper + Xiaomi Settings Key Fix + Force Landscape + Screensaver & Quiet Hours
        SectionLabel(stringResource(R.string.tvtools_mapper_section))
        ActionColumn {
            PrimaryButton(
                stringResource(R.string.tvtools_mapper_settings_fix),
                { onIntent(TvToolsIntent.QuickSettingsOverlay) },
                enabled = !powerWorking,
            )
            SecondaryButton(
                stringResource(R.string.tvtools_mapper_yt_smarttube),
                { onIntent(TvToolsIntent.MapRemoteKey(190, "com.teamsmart.videomanager.tv", "تحويل زر يوتيوب إلى SmartTube")) },
                enabled = !powerWorking,
            )
            SecondaryButton(
                stringResource(R.string.tvtools_mapper_yt_official),
                { onIntent(TvToolsIntent.MapRemoteKey(190, "com.google.android.youtube.tv", "تحويل زر يوتيوب إلى يوتيوب الرسمي")) },
                enabled = !powerWorking,
            )
            SecondaryButton(
                stringResource(R.string.tvtools_mapper_nf_shahid),
                { onIntent(TvToolsIntent.MapRemoteKey(191, "net.mbc.shahid.tv", "تحويل زر نتفلكس إلى شاهد")) },
                enabled = !powerWorking,
            )
            SecondaryButton(
                stringResource(R.string.tvtools_mapper_nf_matches),
                { onIntent(TvToolsIntent.MapRemoteKey(191, "com.midroid.mi5gtv.matches", "تحويل زر نتفلكس إلى مباريات اليوم")) },
                enabled = !powerWorking,
            )
            SecondaryButton(
                stringResource(R.string.tvtools_rotation_landscape),
                { onIntent(TvToolsIntent.SetForceLandscape(true)) },
                enabled = !powerWorking,
            )
            SecondaryButton(
                stringResource(R.string.tvtools_rotation_auto),
                { onIntent(TvToolsIntent.SetForceLandscape(false)) },
                enabled = !powerWorking,
            )
            SecondaryButton(
                stringResource(R.string.tvtools_screensaver_now),
                { onIntent(TvToolsIntent.StartClockScreensaver) },
                enabled = !powerWorking,
            )
            SecondaryButton(
                stringResource(R.string.tvtools_quiet_night),
                { onIntent(TvToolsIntent.ApplyQuietNightVolume) },
                enabled = !powerWorking,
            )
            SecondaryButton(
                stringResource(R.string.tvtools_hdmi_switch),
                { onIntent(TvToolsIntent.SwitchHdmiInput) },
                enabled = !powerWorking,
            )
        }

        // 3) Instant TV Notification / Toast Banner
        SectionLabel(stringResource(R.string.tvtools_banner_section))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CytTextField(
                value = state.tvBannerInput,
                onValueChange = { onIntent(TvToolsIntent.UpdateTvBannerInput(it)) },
                label = stringResource(R.string.tvtools_banner_label),
                modifier = Modifier.weight(1f),
            )
            SmallGlassButton(
                stringResource(R.string.tvtools_banner_send),
                { onIntent(TvToolsIntent.SendTvBanner()) },
                enabled = state.tvBannerInput.isNotBlank() && !powerWorking,
            )
        }
        val bannerPresets = listOf(
            "🕌 Prayer Time Alert" to "Prayer time now - Hayya ala as-Salah",
            "🍽️ Dinner is Ready" to "Dinner is ready",
            "🔉 Lower TV Volume" to "Please lower the TV volume",
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            bannerPresets.forEach { (chipLabel, msgVal) ->
                GlassChip(
                    text = chipLabel,
                    onClick = { if (!powerWorking) onIntent(TvToolsIntent.SendTvBanner(msgVal)) },
                )
            }
        }

        SectionLabel(stringResource(R.string.tvtools_cast_section))
        Body(stringResource(R.string.tvtools_cast_url_note), Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CytTextField(
                value = state.castUrlInput,
                onValueChange = { onIntent(TvToolsIntent.UpdateCastUrl(it)) },
                label = stringResource(R.string.tvtools_cast_url_label),
                modifier = Modifier.weight(1f),
            )
            SmallGlassButton(
                stringResource(R.string.tvtools_cast_url_open),
                { onIntent(TvToolsIntent.OpenUrlOnTv) },
                enabled = state.castUrlInput.isNotBlank() && state.castUrlStatus !is CastUrlStatus.Opening,
            )
        }
        when (state.castUrlStatus) {
            CastUrlStatus.Opening -> LiveLine(stringResource(R.string.tvtools_cast_url_opening))
            is CastUrlStatus.Opened -> CheckLine(
                stringResource(R.string.tvtools_cast_url_opened),
                Modifier.padding(top = 8.dp, start = 4.dp),
            )
            CastUrlStatus.InvalidUrl -> Body(
                stringResource(R.string.tvtools_cast_url_invalid),
                Modifier.padding(top = 8.dp, start = 4.dp),
                color = CytTheme.colors.danger,
            )
            CastUrlStatus.Failed -> Body(
                stringResource(R.string.tvtools_cast_url_failed),
                Modifier.padding(top = 8.dp, start = 4.dp),
                color = CytTheme.colors.danger,
            )
            CastUrlStatus.Idle -> {}
        }

        Body(stringResource(R.string.tvtools_cast_media_title), Modifier.padding(top = 14.dp, bottom = 8.dp, start = 4.dp, end = 4.dp))
        SecondaryButton(
            stringResource(R.string.tvtools_cast_media_pick),
            { onIntent(TvToolsIntent.PickCastMedia) },
            enabled = state.castMediaStatus !is CastMediaStatus.Casting,
            guardObscured = true,
        )
        when (val cm = state.castMediaStatus) {
            is CastMediaStatus.Casting -> {
                LiveLine(msg(R.string.tvtools_cast_media_casting, "name" to cm.fileName, "percent" to cm.percent))
                ProgressBar(cm.percent / 100f, Modifier.padding(top = 12.dp))
            }
            is CastMediaStatus.CastDone -> CheckLine(
                msg(R.string.tvtools_cast_media_done, "name" to cm.fileName),
                Modifier.padding(top = 8.dp, start = 4.dp),
            )
            is CastMediaStatus.Failed -> Body(
                msg(R.string.tvtools_cast_media_failed, "name" to cm.fileName),
                Modifier.padding(top = 8.dp, start = 4.dp),
                color = CytTheme.colors.danger,
            )
            CastMediaStatus.Idle -> {}
        }

        SectionLabel(stringResource(R.string.tvtools_remote_section))
        val upLabel = stringResource(R.string.tvtools_dpad_up)
        val downLabel = stringResource(R.string.tvtools_dpad_down)
        val leftLabel = stringResource(R.string.tvtools_dpad_left)
        val rightLabel = stringResource(R.string.tvtools_dpad_right)
        val okLabel = stringResource(R.string.tvtools_dpad_ok)
        val chassisShape = RoundedCornerShape(32.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(chassisShape)
                .background(CytTheme.colors.surface1)
                .border(1.dp, CytTheme.colors.outline, chassisShape)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Circular D-Pad ring: physical directions stay fixed in LTR (Left on left, Right on right).
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    Modifier
                        .size(204.dp)
                        .clip(CircleShape)
                        .background(CytTheme.colors.surface2)
                        .border(1.5.dp, CytTheme.colors.outline, CircleShape),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(140.dp)
                            .border(1.dp, CytTheme.colors.outline.copy(alpha = 0.45f), CircleShape),
                    )
                    DpadSegmentButton(
                        symbol = "▲",
                        label = upLabel,
                        onClick = { onIntent(TvToolsIntent.PressKey(TvKeyEvent.DPAD_UP)) },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                        enabled = !sending,
                    )
                    DpadSegmentButton(
                        symbol = "▼",
                        label = downLabel,
                        onClick = { onIntent(TvToolsIntent.PressKey(TvKeyEvent.DPAD_DOWN)) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                        enabled = !sending,
                    )
                    DpadSegmentButton(
                        symbol = "◀",
                        label = leftLabel,
                        onClick = { onIntent(TvToolsIntent.PressKey(TvKeyEvent.DPAD_LEFT)) },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
                        enabled = !sending,
                    )
                    DpadSegmentButton(
                        symbol = "▶",
                        label = rightLabel,
                        onClick = { onIntent(TvToolsIntent.PressKey(TvKeyEvent.DPAD_RIGHT)) },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                        enabled = !sending,
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(CytTheme.colors.accent.copy(alpha = 0.22f))
                            .border(1.5.dp, CytTheme.colors.accent, CircleShape)
                            .clickable(enabled = !sending, role = Role.Button) {
                                onIntent(TvToolsIntent.PressKey(TvKeyEvent.DPAD_CENTER))
                            }
                            .rejectObscuredTouches(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = okLabel,
                            color = if (!sending) CytTheme.colors.textPrimary else CytTheme.colors.textTertiary,
                            style = CytTheme.type.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                    }
                }
            }

            // Row 1: Back, Home (accented center button), Menu
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteActionKey(
                    text = stringResource(R.string.tvtools_btn_back),
                    onClick = { onIntent(TvToolsIntent.PressKey(TvKeyEvent.BACK)) },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                )
                RemoteActionKey(
                    text = stringResource(R.string.tvtools_btn_home),
                    onClick = { onIntent(TvToolsIntent.PressKey(TvKeyEvent.HOME)) },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                    accented = true,
                )
                RemoteActionKey(
                    text = stringResource(R.string.tvtools_btn_menu),
                    onClick = { onIntent(TvToolsIntent.PressKey(TvKeyEvent.MENU)) },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                )
            }

            // Row 2: Settings, Play/Pause, Mute
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteActionKey(
                    text = stringResource(R.string.tvtools_btn_settings),
                    onClick = { onIntent(TvToolsIntent.OpenTvSettings) },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                )
                RemoteActionKey(
                    text = stringResource(R.string.tvtools_btn_play_pause),
                    onClick = { onIntent(TvToolsIntent.PressKey(TvKeyEvent.PLAY_PAUSE)) },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                )
                RemoteActionKey(
                    text = stringResource(R.string.tvtools_btn_mute),
                    onClick = { onIntent(TvToolsIntent.PressKey(TvKeyEvent.VOLUME_MUTE)) },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                )
            }

            // Row 3: Unified equal-height Volume Rocker Pill (never wraps onto 2 lines)
            val rockerShape = RoundedCornerShape(999.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(rockerShape)
                    .background(CytTheme.colors.surface2)
                    .border(1.dp, CytTheme.colors.outline, rockerShape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = !sending, role = Role.Button) {
                            onIntent(TvToolsIntent.PressKey(TvKeyEvent.VOLUME_UP))
                        }
                        .rejectObscuredTouches()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tvtools_btn_vol_up),
                        color = if (!sending) CytTheme.colors.textPrimary else CytTheme.colors.textTertiary,
                        style = CytTheme.type.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.width(1.dp).height(24.dp).background(CytTheme.colors.outline))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = !sending, role = Role.Button) {
                            onIntent(TvToolsIntent.PressKey(TvKeyEvent.VOLUME_DOWN))
                        }
                        .rejectObscuredTouches()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tvtools_btn_vol_down),
                        color = if (!sending) CytTheme.colors.textPrimary else CytTheme.colors.textTertiary,
                        style = CytTheme.type.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when (state.remoteStatus) {
            RemoteStatus.Sending -> LiveLine(stringResource(R.string.tvtools_remote_sending))
            RemoteStatus.Sent -> CheckLine(stringResource(R.string.tvtools_remote_sent), Modifier.padding(top = 8.dp, start = 4.dp))
            RemoteStatus.Failed -> Body(stringResource(R.string.tvtools_remote_failed), Modifier.padding(top = 8.dp, start = 4.dp), color = CytTheme.colors.danger)
            RemoteStatus.Idle -> {}
        }

        SectionLabel(stringResource(R.string.tvtools_text_section))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CytTextField(
                value = state.textInput,
                onValueChange = { onIntent(TvToolsIntent.UpdateText(it)) },
                label = stringResource(R.string.tvtools_text_label),
                modifier = Modifier.weight(1f),
            )
            SmallGlassButton(
                stringResource(R.string.tvtools_text_send),
                { onIntent(TvToolsIntent.SendText) },
                enabled = state.textInput.isNotBlank() && !sending,
            )
        }

        SectionLabel(stringResource(R.string.tvtools_screenshot_section))
        Body(stringResource(R.string.tvtools_screenshot_note), Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp))
        SecondaryButton(
            stringResource(R.string.tvtools_screenshot_take),
            { onIntent(TvToolsIntent.TakeScreenshot) },
            enabled = state.screenshotStatus !is ScreenshotStatus.Capturing,
            guardObscured = true,
        )
        when (state.screenshotStatus) {
            ScreenshotStatus.Capturing -> LiveLine(stringResource(R.string.tvtools_screenshot_capturing))
            is ScreenshotStatus.Captured -> {
                CheckLine(stringResource(R.string.tvtools_screenshot_captured), Modifier.padding(top = 8.dp, start = 4.dp))
                TertiaryButton(stringResource(R.string.tvtools_screenshot_share), { onIntent(TvToolsIntent.ShareScreenshot) }, Modifier.padding(top = 4.dp))
            }
            ScreenshotStatus.Failed -> Body(stringResource(R.string.tvtools_screenshot_failed), Modifier.padding(top = 8.dp, start = 4.dp), color = CytTheme.colors.danger)
            ScreenshotStatus.Idle -> {}
        }

        SectionLabel(stringResource(R.string.tvtools_apk_section))
        Body(stringResource(R.string.tvtools_apk_note), Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp))
        when (val s = state.sideloadStatus) {
            SideloadStatus.Idle -> {
                SecondaryButton(stringResource(R.string.tvtools_apk_pick), { onIntent(TvToolsIntent.PickApk) })
            }
            is SideloadStatus.Ready -> {
                val mb = ((s.sizeBytes + 1024L * 1024L - 1L) / (1024L * 1024L)).coerceAtLeast(1L)
                ListGroup {
                    ListRow(msg(R.string.tvtools_apk_selected, "name" to s.fileName, "size" to mb))
                }
                ActionColumn(Modifier.padding(top = 12.dp)) {
                    PrimaryButton(stringResource(R.string.tvtools_apk_install), { onIntent(TvToolsIntent.InstallApk) })
                    TertiaryButton(stringResource(R.string.tvtools_apk_change), { onIntent(TvToolsIntent.ClearApk) })
                }
            }
            is SideloadStatus.Installing -> {
                LiveLine(msg(R.string.tvtools_apk_installing, "name" to s.fileName, "percent" to s.percent))
                ProgressBar(s.percent / 100f, Modifier.padding(top = 12.dp))
            }
            is SideloadStatus.Installed -> {
                CheckLine(msg(R.string.tvtools_apk_installed, "name" to s.fileName), Modifier.padding(top = 8.dp, start = 4.dp))
                SecondaryButton(stringResource(R.string.tvtools_apk_pick), { onIntent(TvToolsIntent.PickApk) }, Modifier.padding(top = 12.dp))
            }
            is SideloadStatus.Failed -> {
                Body(msg(R.string.tvtools_apk_failed, "name" to s.fileName), Modifier.padding(top = 8.dp, start = 4.dp), color = CytTheme.colors.danger)
                SecondaryButton(stringResource(R.string.tvtools_apk_pick), { onIntent(TvToolsIntent.PickApk) }, Modifier.padding(top = 12.dp))
            }
        }
        Gap(48.dp)
    }
}

@Composable
private fun DpadSegmentButton(
    symbol: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .rejectObscuredTouches()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = symbol,
            color = if (enabled) CytTheme.colors.accent else CytTheme.colors.textTertiary,
            style = CytTheme.type.bodySmall,
            maxLines = 1,
        )
        Text(
            text = label,
            color = if (enabled) CytTheme.colors.textPrimary else CytTheme.colors.textTertiary,
            style = CytTheme.type.bodySmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun RemoteActionKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accented: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    val bg = if (accented) CytTheme.colors.accent.copy(alpha = 0.14f) else CytTheme.colors.surface2
    val border = if (accented) CytTheme.colors.accent.copy(alpha = 0.6f) else CytTheme.colors.outline
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .rejectObscuredTouches()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) CytTheme.colors.textPrimary else CytTheme.colors.textTertiary,
            style = CytTheme.type.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


// M10 Full TV App Manager and APK backup puller (v1.3.0 / v1.5.0: System bloatware toggle + 7 full actions).
@Composable
fun TvAppManagerScreen(
    state: TvAppManagerState,
    onIntent: (TvAppManagerIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) }) {
        ScreenTitle(stringResource(R.string.tvapps_title), titleTop)

        CytTextField(
            value = state.searchQuery,
            onValueChange = { onIntent(TvAppManagerIntent.UpdateSearch(it)) },
            label = stringResource(R.string.tvapps_search_label),
            modifier = Modifier.padding(top = 14.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1.6 item 8: «تطبيقاتي» and «المصنع» are two lists.
            RemoteActionKey(
                text = stringResource(R.string.v16_mine),
                onClick = { onIntent(TvAppManagerIntent.SetFilter(TvAppFilter.USER)) },
                modifier = Modifier.weight(1f),
                accented = state.filter == TvAppFilter.USER,
            )
            RemoteActionKey(
                text = stringResource(R.string.v16_factory),
                onClick = { onIntent(TvAppManagerIntent.SetFilter(TvAppFilter.SYSTEM)) },
                modifier = Modifier.weight(1f),
                accented = state.filter == TvAppFilter.SYSTEM,
            )
            RemoteActionKey(
                text = stringResource(R.string.tvapps_refresh),
                onClick = { onIntent(TvAppManagerIntent.Refresh) },
                modifier = Modifier.weight(1f),
                enabled = !state.loading,
            )
        }

        Body(stringResource(if (state.filter == TvAppFilter.USER) R.string.v16_mine_hint else R.string.v16_factory_hint), Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp))

        AppActionFeedback(state.actionStatus)

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                Spinner(size = 24.dp)
            }
            return@ScreenScaffold
        }

        val visible = state.filteredApps
        if (visible.isEmpty()) {
            Lead(stringResource(R.string.tvapps_empty), Modifier.padding(top = 20.dp))
        } else {
            ListGroup(Modifier.padding(top = 16.dp)) {
                visible.forEachIndexed { i, app ->
                    if (i > 0) ListDivider()
                    val statusText = if (app.protectedStreaming) stringResource(R.string.v16_protected_detail)
                        else if (app.system && !app.enabled) stringResource(R.string.v16_frozen)
                        else stringResource(
                        when {
                            !app.system && app.enabled -> R.string.tvapps_status_user_active
                            !app.system && !app.enabled -> R.string.tvapps_status_user_off
                            app.system && app.enabled -> R.string.tvapps_status_system_active
                            else -> R.string.tvapps_status_system_off
                        },
                    )
                    ListRow(
                        title = app.name,
                        subtitle = "${app.pkg.value} · $statusText",
                        leading = { AppTile(app.name) },
                        onClick = { onIntent(TvAppManagerIntent.SelectApp(app)) },
                    )
                }
            }
        }
        Gap(48.dp)
    }

    state.selectedApp?.let { selected ->
        CytBottomSheet({ onIntent(TvAppManagerIntent.DismissApp) }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                TvAppActionsContent(
                    app = selected,
                    confirmingUninstall = state.confirmingUninstall,
                    actionStatus = state.actionStatus,
                    uninstallWord = state.uninstallWord,
                    hasRestorePoint = state.hasRestorePoint,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
internal fun ColumnScope.TvAppActionsContent(
    app: TvInstalledApp,
    confirmingUninstall: Boolean,
    actionStatus: TvAppActionStatus,
    onIntent: (TvAppManagerIntent) -> Unit,
    uninstallWord: String = "",
    hasRestorePoint: Boolean = false,
) {
    val working = actionStatus is TvAppActionStatus.Working
    if (confirmingUninstall && !working) {
        // 1.6 item 3: permanent removal needs this TV's restore point and the typed word «احذف».
        SheetHeader(title = msg(R.string.v16_uninstall_title, "name" to app.name), subtitle = app.pkg.value)
        Body(
            stringResource(R.string.v16_uninstall_body) + " " +
                stringResource(if (hasRestorePoint) R.string.v16_uninstall_has_restore else R.string.v16_uninstall_no_restore),
            Modifier.padding(top = 12.dp),
        )
        CytTextField(
            value = uninstallWord,
            onValueChange = { onIntent(TvAppManagerIntent.UpdateUninstallWord(it)) },
            label = stringResource(R.string.v16_uninstall_field),
            modifier = Modifier.padding(top = 12.dp),
        )
        ActionColumn(Modifier.padding(top = 12.dp)) {
            PrimaryButton(
                stringResource(if (hasRestorePoint) R.string.v16_uninstall_confirm else R.string.v16_uninstall_need_restore),
                { onIntent(TvAppManagerIntent.UninstallApp(app.pkg)) },
                enabled = tv.cleanyour.core.flows.uninstallUnlocked(uninstallWord, hasRestorePoint),
            )
            TertiaryButton(stringResource(R.string.action_cancel), { onIntent(TvAppManagerIntent.CancelUninstall) }, quiet = true)
        }
        return
    }
    SheetHeader(title = app.name, subtitle = app.pkg.value)
    if (app.protectedStreaming) Banner(stringResource(R.string.v16_protected_note), BannerTone.INFO, Modifier.padding(top = 12.dp))
    AppActionFeedback(actionStatus)
    ActionColumn(Modifier.padding(top = 16.dp)) {
        // 1.6 item 3: for factory apps, freezing is the default action, shown first.
        if (app.system && app.canDisable) {
            PrimaryButton(
                text = stringResource(if (app.enabled) R.string.v16_freeze else R.string.v16_unfreeze),
                onClick = { onIntent(if (app.enabled) TvAppManagerIntent.DisableApp(app.pkg) else TvAppManagerIntent.EnableApp(app.pkg)) },
                enabled = !working,
            )
        }
        // 1. Launch on TV
        PrimaryButton(
            text = stringResource(R.string.tvapps_action_launch),
            onClick = { onIntent(TvAppManagerIntent.LaunchApp(app.pkg)) },
            enabled = !working,
        )
        // 2. Force Stop
        SecondaryButton(
            text = stringResource(R.string.tvapps_action_force_stop),
            onClick = { onIntent(TvAppManagerIntent.ForceStopApp(app.pkg)) },
            enabled = !working && app.canForceStop,
            guardObscured = true,
        )
        // 3. Restrict Background Work
        SecondaryButton(
            text = stringResource(R.string.tvapps_action_restrict_bg),
            onClick = { onIntent(TvAppManagerIntent.RestrictBackgroundApp(app.pkg)) },
            enabled = !working && app.canForceStop,
            guardObscured = true,
        )
        // 4. Freeze / Disable (user apps; factory apps have it first, above)
        if (app.canDisable && !app.system) {
            SecondaryButton(
                text = stringResource(R.string.tvapps_action_disable),
                onClick = { onIntent(TvAppManagerIntent.DisableApp(app.pkg)) },
                enabled = !working,
                guardObscured = true,
            )
        }
        // 5. Unfreeze / Enable & Restore
        if (!app.system) SecondaryButton(
            text = stringResource(R.string.tvapps_action_enable),
            onClick = { onIntent(TvAppManagerIntent.EnableApp(app.pkg)) },
            enabled = !working,
            guardObscured = true,
        )
        // 6. Pull APK Backup to Phone
        SecondaryButton(
            text = stringResource(R.string.tvapps_action_pull_apk),
            onClick = { onIntent(TvAppManagerIntent.PullApk(app.pkg)) },
            enabled = !working,
            guardObscured = true,
        )
        // Extra: Clear Cache & Data
        SecondaryButton(
            text = stringResource(R.string.tvapps_action_clear_data),
            onClick = { onIntent(TvAppManagerIntent.ClearAppData(app.pkg)) },
            enabled = !working && app.canClearData,
            guardObscured = true,
        )
        // 7. Permanent Uninstall for User (works for user apps and preloaded bloatware like Netflix/Prime)
        if (app.canUninstall) {
            TertiaryButton(
                text = stringResource(R.string.v16_uninstall_btn),
                onClick = { onIntent(TvAppManagerIntent.AskUninstall) },
                enabled = !working,
            )
        }
        TertiaryButton(
            text = stringResource(R.string.action_cancel),
            onClick = { onIntent(TvAppManagerIntent.DismissApp) },
            quiet = true,
        )
    }
}

@Composable
private fun AppActionFeedback(status: TvAppActionStatus) {
    when (status) {
        TvAppActionStatus.Idle -> {}
        is TvAppActionStatus.Working -> {
            val resId = when (status.kind) {
                TvAppActionKind.LAUNCH -> R.string.tvapps_working_launch
                TvAppActionKind.FORCE_STOP -> R.string.tvapps_working_force_stop
                TvAppActionKind.RESTRICT_BG -> R.string.tvapps_working_restrict_bg
                TvAppActionKind.DISABLE_APP -> R.string.tvapps_working_disable
                TvAppActionKind.ENABLE_APP -> R.string.tvapps_working_enable
                TvAppActionKind.CLEAR_DATA -> R.string.tvapps_working_clear_data
                TvAppActionKind.UNINSTALL -> R.string.tvapps_working_uninstall
                TvAppActionKind.PULL_APK -> R.string.tvapps_working_pull_apk
            }
            LiveLine(msg(resId, "name" to status.appName))
        }
        is TvAppActionStatus.Done -> {
            val resId = when (status.kind) {
                TvAppActionKind.LAUNCH -> R.string.tvapps_done_launch
                TvAppActionKind.FORCE_STOP -> R.string.tvapps_done_force_stop
                TvAppActionKind.RESTRICT_BG -> R.string.tvapps_done_restrict_bg
                TvAppActionKind.DISABLE_APP -> R.string.tvapps_done_disable
                TvAppActionKind.ENABLE_APP -> R.string.tvapps_done_enable
                TvAppActionKind.CLEAR_DATA -> R.string.tvapps_done_clear_data
                TvAppActionKind.UNINSTALL -> R.string.tvapps_done_uninstall
                TvAppActionKind.PULL_APK -> R.string.tvapps_done_pull_apk
            }
            CheckLine(msg(resId, "name" to status.appName), Modifier.padding(top = 12.dp, start = 4.dp))
        }
        is TvAppActionStatus.Failed -> {
            Body(
                msg(R.string.tvapps_failed_action, "name" to status.appName),
                Modifier.padding(top = 12.dp, start = 4.dp),
                color = CytTheme.colors.danger,
            )
        }
    }
}
