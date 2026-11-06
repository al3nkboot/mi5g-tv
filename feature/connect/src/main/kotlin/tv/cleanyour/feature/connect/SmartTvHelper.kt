package tv.cleanyour.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tv.cleanyour.core.flows.smarttv.SmartTvBrand
import tv.cleanyour.core.flows.smarttv.SmartTvKey
import tv.cleanyour.core.flows.smarttv.SmartTvLink
import tv.cleanyour.core.flows.smarttv.SmartTvProtocol
import tv.cleanyour.core.flows.smarttv.SmartTvRemote
import tv.cleanyour.ui.design.component.CytTextField
import tv.cleanyour.ui.design.component.GlassChip
import tv.cleanyour.ui.design.component.MonospaceBlock
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.SheetHeader
import tv.cleanyour.ui.design.theme.CytTheme

// M4 item 31: Samsung Tizen and LG webOS cannot be cleaned over ADB. The helper gives them a Wi-Fi
// remote, a guided AdGuard DNS step list, and where to switch their own ads off. Nothing is changed
// on the TV except the key presses the user sends.
enum class SmartTvTab { REMOTE, DNS, ADS }

private enum class LinkState { IDLE, CONNECTING, CONNECTED, DENIED, UNREACHABLE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SmartTvHelperContent(startTab: SmartTvTab = SmartTvTab.REMOTE, onDone: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var brand by rememberSaveable { mutableStateOf(SmartTvBrand.SAMSUNG) }
    var ip by rememberSaveable { mutableStateOf("") }
    var pairing by rememberSaveable { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf(LinkState.IDLE) }
    val remote = remember { SmartTvRemote() }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { remote.close() } }

    SheetHeader(stringResource(R.string.smarttv_title))
    FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassChip(text = stringResource(R.string.smarttv_samsung), mark = if (brand == SmartTvBrand.SAMSUNG) tv.cleanyour.ui.design.component.ChipMark.DOT_SUCCESS else tv.cleanyour.ui.design.component.ChipMark.NONE,
            onClick = { brand = SmartTvBrand.SAMSUNG; link = LinkState.IDLE; pairing = null })
        GlassChip(text = stringResource(R.string.smarttv_lg), mark = if (brand == SmartTvBrand.LG) tv.cleanyour.ui.design.component.ChipMark.DOT_SUCCESS else tv.cleanyour.ui.design.component.ChipMark.NONE,
            onClick = { brand = SmartTvBrand.LG; link = LinkState.IDLE; pairing = null })
    }
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmartTvTab.entries.forEach { t ->
            GlassChip(text = stringResource(when (t) { SmartTvTab.REMOTE -> R.string.smarttv_tab_remote; SmartTvTab.DNS -> R.string.smarttv_tab_dns; SmartTvTab.ADS -> R.string.smarttv_tab_ads }),
                mark = if (tab == t) tv.cleanyour.ui.design.component.ChipMark.DOT_SUCCESS else tv.cleanyour.ui.design.component.ChipMark.NONE, onClick = { tab = t })
        }
    }
    when (tab) {
        SmartTvTab.REMOTE -> {
            Body(stringResource(R.string.smarttv_remote_note), Modifier.padding(top = 12.dp))
            val valid = SmartTvProtocol.isPrivateLan(ip.trim())
            CytTextField(ip, { ip = it.filter { c -> c.isDigit() || c == '.' }.take(15) }, stringResource(R.string.smarttv_ip_label),
                Modifier.padding(top = 8.dp), error = if (ip.isNotEmpty() && !valid) stringResource(R.string.smarttv_ip_error) else null)
            PrimaryButton(stringResource(R.string.smarttv_connect), {
                link = LinkState.CONNECTING
                scope.launch {
                    link = when (val r = remote.connect(brand, ip.trim(), pairing)) {
                        is SmartTvLink.Connected -> { pairing = r.pairing; LinkState.CONNECTED }
                        SmartTvLink.Denied -> LinkState.DENIED
                        SmartTvLink.Unreachable -> LinkState.UNREACHABLE
                    }
                }
            }, Modifier.padding(top = 8.dp), enabled = valid && link != LinkState.CONNECTING, loading = link == LinkState.CONNECTING, guardObscured = false)
            val status = when (link) {
                LinkState.IDLE -> null
                LinkState.CONNECTING -> stringResource(if (brand == SmartTvBrand.SAMSUNG) R.string.smarttv_allow_samsung else R.string.smarttv_allow_lg)
                LinkState.CONNECTED -> stringResource(R.string.smarttv_connected)
                LinkState.DENIED -> stringResource(R.string.smarttv_denied)
                LinkState.UNREACHABLE -> stringResource(R.string.smarttv_unreachable)
            }
            if (status != null) Body(status, Modifier.padding(top = 8.dp), color = CytTheme.colors.textPrimary)
            SmartTvPad(enabled = link == LinkState.CONNECTED) { key -> scope.launch { if (!remote.press(key)) link = LinkState.UNREACHABLE } }
        }
        SmartTvTab.DNS -> {
            Body(stringResource(R.string.smarttv_dns_note), Modifier.padding(top = 12.dp))
            Body(stringResource(if (brand == SmartTvBrand.SAMSUNG) R.string.smarttv_dns_samsung else R.string.smarttv_dns_lg),
                Modifier.padding(top = 8.dp), color = CytTheme.colors.textPrimary)
            MonospaceBlock(listOf("DNS 1: 94.140.14.14", "DNS 2: 94.140.15.15"), Modifier.padding(top = 12.dp))
            Body(stringResource(R.string.smarttv_dns_undo), Modifier.padding(top = 8.dp))
        }
        SmartTvTab.ADS -> {
            Body(stringResource(if (brand == SmartTvBrand.SAMSUNG) R.string.smarttv_ads_samsung else R.string.smarttv_ads_lg),
                Modifier.padding(top = 12.dp), color = CytTheme.colors.textPrimary)
            Body(stringResource(R.string.smarttv_ads_note), Modifier.padding(top = 8.dp))
        }
    }
    SecondaryButton(stringResource(R.string.action_ok), onDone, Modifier.padding(top = 16.dp))
}

@Composable
private fun SmartTvPad(enabled: Boolean, press: (SmartTvKey) -> Unit) {
    @Composable fun K(label: String, key: SmartTvKey) =
        GlassChip(text = label, mark = if (enabled) tv.cleanyour.ui.design.component.ChipMark.NONE else tv.cleanyour.ui.design.component.ChipMark.DOT_HOLLOW,
            onClick = { if (enabled) press(key) })
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        K(stringResource(R.string.smarttv_power), SmartTvKey.POWER); K("🏠", SmartTvKey.HOME); K("↩", SmartTvKey.BACK)
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) { K("▲", SmartTvKey.UP) }
    // A remote is not mirrored: left stays on the left in Arabic too.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            K("◀", SmartTvKey.LEFT); K("OK", SmartTvKey.OK); K("▶", SmartTvKey.RIGHT)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) { K("▼", SmartTvKey.DOWN) }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        K("🔉", SmartTvKey.VOL_DOWN); K("🔇", SmartTvKey.MUTE); K("🔊", SmartTvKey.VOL_UP)
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        K("CH −", SmartTvKey.CH_DOWN); K("CH +", SmartTvKey.CH_UP)
    }
}
