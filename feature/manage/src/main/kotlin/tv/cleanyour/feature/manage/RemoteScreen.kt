@file:OptIn(ExperimentalMaterial3Api::class)

package tv.cleanyour.feature.manage

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.cleanyour.core.flows.RemoteApp
import tv.cleanyour.core.flows.RemoteButton
import tv.cleanyour.core.flows.RemoteConnection
import tv.cleanyour.core.flows.RemoteIntent
import tv.cleanyour.core.flows.RemoteMode
import tv.cleanyour.core.flows.RemoteState
import tv.cleanyour.core.flows.RemoteTab
import tv.cleanyour.core.flows.TrackpadMapper
import tv.cleanyour.core.flows.repeats
import tv.cleanyour.ui.design.theme.CytTheme

// 2.0 phone remote. Dark surface, Mi5G gold (theme `warning` token) as the single accent.
// The D-pad and the trackpad are laid out LTR in every locale so Left always sends DPAD_LEFT.

private val Gold @Composable get() = CytTheme.colors.warning

@Composable
fun RemoteScreen(state: RemoteState, onIntent: (RemoteIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val press: (RemoteButton) -> Unit = { onIntent(RemoteIntent.Press(it)) }
    var moreOpen by remember(state.showMoreSheet) { mutableStateOf(state.showMoreSheet) }
    Column(modifier.fillMaxSize().background(CytTheme.colors.bg)) {
        RemoteTopBar(state, onIntent, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            if (state.connection == RemoteConnection.NotConnected) NotConnectedCard(onIntent)
            when (state.tab) {
                RemoteTab.Remote -> {
                    ModeSwitch(state.mode, onIntent)
                    when (state.mode) {
                        RemoteMode.Buttons -> ButtonsPane(press, onMore = { moreOpen = true })
                        RemoteMode.Trackpad -> TrackpadPane(press)
                        RemoteMode.Keyboard -> KeyboardPane(state, onIntent)
                    }
                }
                RemoteTab.Apps -> AppsPane(state, onIntent)
                RemoteTab.Cast -> ComingSoon(R.string.remote_coming_soon_cast)
                RemoteTab.Settings -> ComingSoon(R.string.remote_coming_soon_settings)
            }
        }
        RemoteTabs(state.tab, onIntent)
    }
    if (moreOpen) MoreSheet(press, onDismiss = { moreOpen = false }, inline = state.showMoreSheet)
}

@Composable
private fun RemoteTopBar(state: RemoteState, onIntent: (RemoteIntent) -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.remote_back), color = Gold, style = MaterialTheme.typography.labelLarge)
        }
        Text(state.tvName, Modifier.weight(1f).padding(horizontal = 8.dp), color = CytTheme.colors.textPrimary, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val (label, dot) = when (state.connection) {
            RemoteConnection.Connected -> R.string.remote_connected to CytTheme.colors.success
            RemoteConnection.Connecting -> R.string.remote_connecting to Gold
            RemoteConnection.NotConnected -> R.string.remote_not_connected to CytTheme.colors.danger
        }
        Row(
            Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, CytTheme.colors.outline, RoundedCornerShape(20.dp))
                .clickable { onIntent(RemoteIntent.Reconnect) }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(label), color = CytTheme.colors.textPrimary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NotConnectedCard(onIntent: (RemoteIntent) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(20.dp), color = CytTheme.colors.surface2, border = BorderStroke(1.dp, Gold.copy(alpha = .5f))) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.remote_connect_prompt), color = CytTheme.colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            GoldPill(stringResource(R.string.remote_connect), filled = true) { onIntent(RemoteIntent.OpenPairing) }
        }
    }
}

@Composable
private fun ModeSwitch(mode: RemoteMode, onIntent: (RemoteIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(24.dp)).background(CytTheme.colors.surface1).padding(4.dp),
    ) {
        listOf(RemoteMode.Buttons to R.string.remote_buttons, RemoteMode.Trackpad to R.string.remote_trackpad, RemoteMode.Keyboard to R.string.remote_keyboard).forEach { (m, label) ->
            val selected = mode == m
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(20.dp)).background(if (selected) Gold else Color.Transparent)
                    .clickable(role = Role.Tab) { onIntent(RemoteIntent.SetMode(m)) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(label), color = if (selected) CytTheme.colors.bg else CytTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
        }
    }
}

@Composable
private fun ColumnScope.ButtonsPane(press: (RemoteButton) -> Unit, onMore: () -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        // Physical layout: VOL on the left, CH on the right, in every locale (like a real remote).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Rocker(R.string.remote_volume, RemoteButton.VOL_UP, RemoteButton.VOL_DOWN, press)
            DpadRing(press)
            Rocker(R.string.remote_channel, RemoteButton.CH_UP, RemoteButton.CH_DOWN, press)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(RemoteButton.BACK to R.string.remote_back, RemoteButton.HOME to R.string.remote_home, RemoteButton.MUTE to R.string.remote_mute, RemoteButton.PLAY_PAUSE to R.string.remote_play_pause).forEach { (b, l) ->
                RemoteKey(stringResource(l), b, press, Modifier.weight(1f).height(64.dp), RoundedCornerShape(18.dp), small = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        GoldPill(stringResource(R.string.remote_more), filled = false, onClick = onMore)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DpadRing(press: (RemoteButton) -> Unit) {
    val ring = 236.dp
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Box(
        Modifier.size(ring).clip(CircleShape).background(CytTheme.colors.surface2).border(2.dp, Gold.copy(alpha = .55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val seg = 72.dp
        RemoteKey("▲", RemoteButton.UP, press, Modifier.align(Alignment.TopCenter).size(seg), CircleShape, R.string.remote_up, ghost = true)
        RemoteKey("▼", RemoteButton.DOWN, press, Modifier.align(Alignment.BottomCenter).size(seg), CircleShape, R.string.remote_down, ghost = true)
        RemoteKey("◀", RemoteButton.LEFT, press, Modifier.align(Alignment.CenterStart).size(seg), CircleShape, R.string.remote_left, ghost = true)
        RemoteKey("▶", RemoteButton.RIGHT, press, Modifier.align(Alignment.CenterEnd).size(seg), CircleShape, R.string.remote_right, ghost = true)
        RemoteKey(stringResource(R.string.remote_ok), RemoteButton.OK, press, Modifier.size(92.dp), CircleShape, filled = true)
    } }
}

@Composable
private fun Rocker(@StringRes label: Int, up: RemoteButton, down: RemoteButton, press: (RemoteButton) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.width(60.dp).clip(RoundedCornerShape(30.dp)).background(CytTheme.colors.surface2).border(1.dp, CytTheme.colors.outline, RoundedCornerShape(30.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RemoteKey("+", up, press, Modifier.size(60.dp, 76.dp), RoundedCornerShape(30.dp), ghost = true, description = stringResource(label) + " +")
            Box(Modifier.width(28.dp).height(1.dp).background(CytTheme.colors.outline))
            RemoteKey("−", down, press, Modifier.size(60.dp, 76.dp), RoundedCornerShape(30.dp), ghost = true, description = stringResource(label) + " −")
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(label), color = CytTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// One remote key sends on press, then repeats after 400 ms and every 120 ms for repeating keys.
@Composable
private fun RemoteKey(
    text: String, button: RemoteButton, press: (RemoteButton) -> Unit, modifier: Modifier, shape: Shape,
    @StringRes descriptionRes: Int? = null, filled: Boolean = false, ghost: Boolean = false, small: Boolean = false, description: String? = null,
) {
    val scope = rememberCoroutineScope()
    val onPress by rememberUpdatedState(press)
    var down by remember { mutableStateOf(false) }
    val desc = description ?: descriptionRes?.let { stringResource(it) } ?: text
    val bg = when { filled -> Gold; down -> Gold.copy(alpha = .28f); ghost -> Color.Transparent; else -> CytTheme.colors.surface2 }
    val fg = if (filled) CytTheme.colors.bg else if (ghost) Gold else CytTheme.colors.textPrimary
    Box(
        modifier.clip(shape).background(bg)
            .then(if (!ghost && !filled) Modifier.border(1.dp, CytTheme.colors.outline, shape) else Modifier)
            .semantics { contentDescription = desc; role = Role.Button }
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    down = true
                    onPress(button)
                    var job: Job? = null
                    if (repeats(button)) job = scope.launch {
                        delay(400)
                        while (true) { onPress(button); delay(120) }
                    }
                    waitForUpOrCancellation()
                    job?.cancel()
                    down = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = fg, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (small) 13.sp else if (filled) 18.sp else 20.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun GoldPill(text: String, filled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(26.dp)).background(if (filled) Gold else Color.Transparent)
            .border(1.dp, Gold, RoundedCornerShape(26.dp))
            .clickable(role = Role.Button) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (filled) CytTheme.colors.bg else Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ColumnScope.TrackpadPane(press: (RemoteButton) -> Unit) {
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    val fire: (RemoteButton) -> Unit = press
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(28.dp)).background(CytTheme.colors.surface2)
                .border(1.dp, Gold.copy(alpha = .45f), RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { fire(TrackpadMapper.tap()) } }
                .pointerInput(Unit) {
                    // Every threshold of travel along the dominant axis is one D-pad step.
                    detectDragGestures(onDragEnd = { dx = 0f; dy = 0f }, onDragCancel = { dx = 0f; dy = 0f }) { change, amount ->
                        change.consume(); dx += amount.x; dy += amount.y
                        TrackpadMapper.swipe(dx, dy, threshold)?.let { fire(it); dx = 0f; dy = 0f }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).border(2.dp, Gold.copy(alpha = .6f), CircleShape))
                Spacer(Modifier.height(16.dp))
                run {
                    Text(stringResource(R.string.remote_trackpad_hint), color = CytTheme.colors.textSecondary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.KeyboardPane(state: RemoteState, onIntent: (RemoteIntent) -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
        OutlinedTextField(
            state.textInput, { onIntent(RemoteIntent.UpdateText(it)) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.remote_text)) }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold, focusedLabelColor = Gold, cursorColor = Gold,
                focusedTextColor = CytTheme.colors.textPrimary, unfocusedTextColor = CytTheme.colors.textPrimary,
            ),
        )
        if (state.unicodeWarning) Text(
            stringResource(R.string.remote_unicode_warning), color = Gold, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Gold).clickable(role = Role.Button) { onIntent(RemoteIntent.SendText) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.remote_send), color = CytTheme.colors.bg, fontWeight = FontWeight.SemiBold) }
            }
            RemoteKey(stringResource(R.string.remote_backspace), RemoteButton.BACKSPACE, { onIntent(RemoteIntent.Backspace) }, Modifier.size(72.dp, 56.dp), RoundedCornerShape(28.dp), R.string.remote_backspace_desc)
        }
    }
}

@Composable
private fun ColumnScope.AppsPane(state: RemoteState, onIntent: (RemoteIntent) -> Unit) {
    LaunchedEffect(Unit) { if (state.installedApps.isEmpty()) onIntent(RemoteIntent.RefreshApps) }
    val favorites = state.installedApps.filter { it.pkg in state.favorites }
    val others = state.installedApps.filter { it.pkg !in state.favorites }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.remote_favorites_header), color = Gold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            GoldPill(stringResource(R.string.remote_refresh), filled = false) { onIntent(RemoteIntent.RefreshApps) }
        }
        favorites.forEach { AppRow(it, true, onIntent) }
        if (others.isNotEmpty()) {
            Text(stringResource(R.string.remote_all_apps_header), color = CytTheme.colors.textSecondary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            others.forEach { AppRow(it, false, onIntent) }
        }
        if (state.installedApps.isEmpty() && !state.appsLoading) Text(stringResource(R.string.remote_apps_empty), color = CytTheme.colors.textSecondary, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun AppRow(app: RemoteApp, favorite: Boolean, onIntent: (RemoteIntent) -> Unit) {
    val starDesc = stringResource(if (favorite) R.string.remote_favorite_remove else R.string.remote_favorite_add)
    val openDesc = stringResource(R.string.remote_open) + " " + app.label
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(CytTheme.colors.surface2)
            .clickable(role = Role.Button) { onIntent(RemoteIntent.LaunchApp(app.pkg)) }.semantics { contentDescription = openDesc }
            .padding(start = 16.dp, end = 4.dp).heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(Gold.copy(alpha = .18f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Text(app.label.substringAfterLast('.').take(1).uppercase(), color = Gold, fontWeight = FontWeight.Bold)
        }
        Text(app.label, Modifier.weight(1f).padding(horizontal = 12.dp), color = CytTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        Box(
            Modifier.size(56.dp).clip(CircleShape).clickable(role = Role.Checkbox) { onIntent(RemoteIntent.ToggleFavorite(app.pkg)) }.semantics { contentDescription = starDesc },
            contentAlignment = Alignment.Center,
        ) { Text(if (favorite) "★" else "☆", color = Gold, fontSize = 24.sp) }
    }
}

@Composable
private fun ColumnScope.ComingSoon(@StringRes body: Int) {
    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.remote_coming_soon), color = Gold, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(body), color = CytTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RemoteTabs(tab: RemoteTab, onIntent: (RemoteIntent) -> Unit) {
    NavigationBar(containerColor = CytTheme.colors.surface1) {
        listOf(RemoteTab.Remote to R.string.remote_tab_remote, RemoteTab.Apps to R.string.remote_tab_apps, RemoteTab.Cast to R.string.remote_tab_cast, RemoteTab.Settings to R.string.remote_tab_settings).forEach { (t, label) ->
            NavigationBarItem(
                selected = tab == t, onClick = { onIntent(RemoteIntent.SelectTab(t)) },
                icon = { Box(Modifier.size(6.dp).background(if (tab == t) Gold else Color.Transparent, CircleShape)) },
                label = { Text(stringResource(label), maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(selectedTextColor = Gold, unselectedTextColor = CytTheme.colors.textSecondary, indicatorColor = Gold.copy(alpha = .15f)),
            )
        }
    }
}

@Composable
private fun MoreSheet(press: (RemoteButton) -> Unit, onDismiss: () -> Unit, inline: Boolean) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.remote_more), color = Gold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            listOf(
                listOf(RemoteButton.MENU to R.string.remote_menu, RemoteButton.SETTINGS to R.string.remote_settings, RemoteButton.INPUT to R.string.remote_input),
                listOf(RemoteButton.POWER to R.string.remote_power, RemoteButton.SLEEP to R.string.remote_sleep),
                listOf(RemoteButton.REWIND to R.string.remote_rewind, RemoteButton.FAST_FORWARD to R.string.remote_fast_forward),
            ).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (b, l) -> RemoteKey(stringResource(l), b, press, Modifier.weight(1f).height(56.dp), RoundedCornerShape(16.dp), small = true) }
                }
            }
            Text(stringResource(R.string.remote_number_pad), color = CytTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column {
                    listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(null, 0, null)).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { d ->
                                if (d == null) Spacer(Modifier.weight(1f).height(56.dp))
                                else RemoteKey(stringResource(digitString(d)), digitButton(d), press, Modifier.weight(1f).height(56.dp), RoundedCornerShape(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (inline) {
        // Screenshot tests: Robolectric cannot capture a separate sheet window, so render the same content in place.
        Box(Modifier.fillMaxSize().background(CytTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
            Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = CytTheme.colors.surface1) { content() }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CytTheme.colors.surface1) { content() }
    }
}

private fun digitButton(d: Int): RemoteButton = RemoteButton.valueOf("DIGIT_$d")

private fun digitString(digit: Int): Int = when (digit) {
    0 -> R.string.remote_digit_0; 1 -> R.string.remote_digit_1; 2 -> R.string.remote_digit_2; 3 -> R.string.remote_digit_3; 4 -> R.string.remote_digit_4
    5 -> R.string.remote_digit_5; 6 -> R.string.remote_digit_6; 7 -> R.string.remote_digit_7; 8 -> R.string.remote_digit_8; else -> R.string.remote_digit_9
}
