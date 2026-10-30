@file:OptIn(ExperimentalMaterial3Api::class)

package tv.cleanyour.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.*
import tv.cleanyour.ui.design.theme.CytTheme
import kotlinx.coroutines.delay

@Composable
fun RemoteScreen(state: RemoteState, onIntent: (RemoteIntent) -> Unit, onBack: () -> Unit) {
    val gold = CytTheme.colors.warning
    fun press(button: RemoteButton) { onIntent(RemoteIntent.Press(button)) }
    Column(Modifier.fillMaxSize().background(CytTheme.colors.bg).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.remote_back), color = gold) }
            Text(state.tvName, Modifier.weight(1f), color = CytTheme.colors.textPrimary, style = MaterialTheme.typography.titleLarge)
            AssistChip(onClick = { onIntent(RemoteIntent.Reconnect) }, label = { Text(stringResource(if (state.connection == RemoteConnection.Connected) R.string.remote_connected else R.string.remote_not_connected)) })
        }
        if (state.connection == RemoteConnection.NotConnected) Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.padding(16.dp)) { Text(stringResource(R.string.remote_connect_prompt)); Button(onClick = { onIntent(RemoteIntent.OpenPairing) }) { Text(stringResource(R.string.remote_connect)) } }
        }
        when (state.tab) {
            RemoteTab.Remote -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    listOf(RemoteMode.Buttons to R.string.remote_buttons, RemoteMode.Trackpad to R.string.remote_trackpad, RemoteMode.Keyboard to R.string.remote_keyboard).forEach { (m, label) -> FilterChip(selected = state.mode == m, onClick = { onIntent(RemoteIntent.SetMode(m)) }, label = { Text(stringResource(label)) }) }
                }
                when (state.mode) {
                    RemoteMode.Buttons -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { KeyButton("+", gold, repeatButton = RemoteButton.VOL_UP) { press(RemoteButton.VOL_UP) }; KeyButton("−", gold, repeatButton = RemoteButton.VOL_DOWN) { press(RemoteButton.VOL_DOWN) }; Text(stringResource(R.string.remote_volume), color = CytTheme.colors.textPrimary) }
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                KeyButton("↑", gold, size = 80.dp, repeatButton = RemoteButton.UP) { press(RemoteButton.UP) }
                                Row(verticalAlignment = Alignment.CenterVertically) { KeyButton("←", gold, size = 80.dp, repeatButton = RemoteButton.LEFT) { press(RemoteButton.LEFT) }; KeyButton(stringResource(R.string.remote_ok), gold, CircleShape, 80.dp) { press(RemoteButton.OK) }; KeyButton("→", gold, size = 80.dp, repeatButton = RemoteButton.RIGHT) { press(RemoteButton.RIGHT) } }
                                KeyButton("↓", gold, size = 80.dp, repeatButton = RemoteButton.DOWN) { press(RemoteButton.DOWN) }
                            } }
                            Column { KeyButton("+", gold, repeatButton = RemoteButton.CH_UP) { press(RemoteButton.CH_UP) }; KeyButton("−", gold, repeatButton = RemoteButton.CH_DOWN) { press(RemoteButton.CH_DOWN) }; Text(stringResource(R.string.remote_channel), color = CytTheme.colors.textPrimary) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(RemoteButton.BACK to R.string.remote_back, RemoteButton.HOME to R.string.remote_home, RemoteButton.MUTE to R.string.remote_mute, RemoteButton.PLAY_PAUSE to R.string.remote_play_pause).forEach { (b, l) -> Button(onClick = { press(b) }) { Text(stringResource(l)) } }
                        }
                        var more by remember(state.showMoreSheet) { mutableStateOf(state.showMoreSheet) }
                        Button(onClick = { more = true }, Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.remote_more)) }
                        if (more) AlertDialog(
                            onDismissRequest = { more = false }, title = { Text(stringResource(R.string.remote_more)) },
                            text = { Column {
                                listOf(RemoteButton.MENU to R.string.remote_menu, RemoteButton.SETTINGS to R.string.remote_settings, RemoteButton.POWER to R.string.remote_power, RemoteButton.SLEEP to R.string.remote_sleep, RemoteButton.INPUT to R.string.remote_input, RemoteButton.REWIND to R.string.remote_rewind, RemoteButton.FAST_FORWARD to R.string.remote_fast_forward).forEach { (b, l) -> TextButton(onClick = { press(b) }) { Text(stringResource(l)) } }
                                (0..9).toList().chunked(5).forEach { row -> Row { row.forEach { digit ->
                                    val button = when (digit) { 0 -> RemoteButton.DIGIT_0; 1 -> RemoteButton.DIGIT_1; 2 -> RemoteButton.DIGIT_2; 3 -> RemoteButton.DIGIT_3; 4 -> RemoteButton.DIGIT_4; 5 -> RemoteButton.DIGIT_5; 6 -> RemoteButton.DIGIT_6; 7 -> RemoteButton.DIGIT_7; 8 -> RemoteButton.DIGIT_8; else -> RemoteButton.DIGIT_9 }
                                    TextButton(onClick = { press(button) }) { Text(stringResource(digitString(digit))) }
                                } } }
                            } }, confirmButton = { TextButton(onClick = { more = false }) { Text(stringResource(R.string.remote_done)) } },
                        )
                    }
                    RemoteMode.Trackpad -> {
                        var dx by remember { mutableFloatStateOf(0f) }; var dy by remember { mutableFloatStateOf(0f) }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp).background(CytTheme.colors.surface2, RoundedCornerShape(28.dp))
                                .pointerInput(Unit) { detectTapGestures { press(TrackpadMapper.tap()) } }
                                .pointerInput(Unit) { detectDragGestures(onDragEnd = { TrackpadMapper.swipe(dx, dy, 48f)?.let(::press); dx = 0f; dy = 0f }) { change, amount -> change.consume(); dx += amount.x; dy += amount.y } }, Alignment.Center) { Text(stringResource(R.string.remote_trackpad_hint), color = CytTheme.colors.textPrimary) }
                        }
                    }
                    RemoteMode.Keyboard -> { OutlinedTextField(state.textInput, { onIntent(RemoteIntent.UpdateText(it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.remote_text)) }); if (state.unicodeWarning) Text(stringResource(R.string.remote_unicode_warning), color = gold); Row { Button(onClick = { onIntent(RemoteIntent.SendText) }) { Text(stringResource(R.string.remote_send)) }; Button(onClick = { onIntent(RemoteIntent.Backspace) }) { Text(stringResource(R.string.remote_backspace)) } } }
                }
            }
            RemoteTab.Apps -> { LaunchedEffect(Unit) { onIntent(RemoteIntent.RefreshApps) }; state.installedApps.sortedWith(compareByDescending<RemoteApp> { it.pkg in state.favorites }.thenBy { it.pkg }).forEach { app -> val favorite = app.pkg in state.favorites; val favoriteDescription = stringResource(if (favorite) R.string.remote_favorite_remove else R.string.remote_favorite_add); Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { onIntent(RemoteIntent.LaunchApp(app.pkg)) }) { Text(app.label, color = CytTheme.colors.textPrimary) }; TextButton(modifier = Modifier.semantics { contentDescription = favoriteDescription }, onClick = { onIntent(RemoteIntent.ToggleFavorite(app.pkg)) }) { Text(if (favorite) "★" else "☆", color = gold) } } } }
            RemoteTab.Cast -> Text(stringResource(R.string.remote_coming_soon), color = CytTheme.colors.textPrimary)
            RemoteTab.Settings -> Text(stringResource(R.string.remote_coming_soon), color = CytTheme.colors.textPrimary)
        }
        Spacer(Modifier.weight(1f))
        NavigationBar(containerColor = CytTheme.colors.surface1) { listOf(RemoteTab.Remote to R.string.remote_tab_remote, RemoteTab.Apps to R.string.remote_tab_apps, RemoteTab.Cast to R.string.remote_tab_cast, RemoteTab.Settings to R.string.remote_tab_settings).forEach { (tab, label) -> NavigationBarItem(selected = state.tab == tab, onClick = { onIntent(RemoteIntent.SelectTab(tab)) }, icon = {}, label = { Text(stringResource(label)) }) } }
    }
}

private fun digitString(digit: Int): Int = when (digit) { 0 -> R.string.remote_digit_0; 1 -> R.string.remote_digit_1; 2 -> R.string.remote_digit_2; 3 -> R.string.remote_digit_3; 4 -> R.string.remote_digit_4; 5 -> R.string.remote_digit_5; 6 -> R.string.remote_digit_6; 7 -> R.string.remote_digit_7; 8 -> R.string.remote_digit_8; else -> R.string.remote_digit_9 }

@Composable private fun KeyButton(text: String, color: Color, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp), size: androidx.compose.ui.unit.Dp = 64.dp, repeatButton: RemoteButton? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, repeatButton) {
        if (pressed && repeatButton != null && repeats(repeatButton)) {
            delay(400)
            repeated = true
            onClick()
            while (true) { delay(120); onClick() }
        } else if (!pressed) repeated = false
    }
    FilledTonalButton(onClick = { if (!repeated) onClick() }, interactionSource = interaction, modifier = Modifier.size(size).padding(3.dp), shape = shape, colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(alpha = .18f), contentColor = color)) { Text(text) }
}
