package tv.cleanyour.feature.manage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.cleanyour.core.flows.DnsMode
import tv.cleanyour.core.flows.ManageIntent
import tv.cleanyour.core.flows.ManageState
import tv.cleanyour.core.flows.Port853
import tv.cleanyour.core.flows.TvCard
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.SwitchRow
import tv.cleanyour.ui.design.theme.CytTheme

// Mi5G TV 1.6 items 1, 5, 6, 7 (web-preview studio.tsx HomeScreen): one health score for this TV,
// three stats, a single clean button, the port 853 card and this TV's evening mode.
private val cardShape = RoundedCornerShape(16.dp)

@Composable
internal fun HealthDashboard(state: ManageState, tv: TvCard, onIntent: (ManageIntent) -> Unit) {
    val c = CytTheme.colors
    LaunchedEffect(tv.id) { onIntent(ManageIntent.CheckPort853) }
    val score = tv.health ?: 0
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).border(1.dp, c.outline, cardShape).background(c.surface1, cardShape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val cd = msg(R.string.v16_health_cd, "score" to score)
        Box(Modifier.size(104.dp).semantics { contentDescription = cd }, contentAlignment = Alignment.Center) {
            val track = c.outline
            val ring = c.primary
            Canvas(Modifier.size(104.dp)) {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2
                val sz = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
                drawArc(ring, -90f, 360f * score / 100f, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("$score", color = c.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.v16_health_title), color = c.textPrimary, style = CytTheme.type.titleLarge)
            Body(stringResource(if (tv.clean) R.string.v16_health_clean else R.string.v16_health_not_clean))
            Body(stringResource(if (tv.restorePointAtMs != null) R.string.v16_health_restore else R.string.v16_health_no_restore), Modifier.padding(top = 4.dp))
        }
    }

    val unknown = stringResource(R.string.v16_stat_unknown)
    val st = state.stats
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat(stringResource(R.string.v16_stat_ram), st?.let { msg(R.string.v16_stat_gb, "used" to it.ramUsedGb, "total" to it.ramTotalGb) } ?: unknown, Modifier.weight(1f))
        Stat(stringResource(R.string.v16_stat_storage), st?.let { msg(R.string.v16_stat_gb, "used" to String.format(java.util.Locale.US, "%.1f", (it.storageTotalGb.toDoubleOrNull() ?: 0.0) - (it.storageFreeGb.toDoubleOrNull() ?: 0.0)), "total" to it.storageTotalGb) } ?: unknown, Modifier.weight(1f))
        Stat(
            stringResource(R.string.v16_stat_off),
            if (tv.clean) msg(R.string.v16_stat_off_done, "count" to tv.switchedOff) else stringResource(R.string.v16_stat_off_pending),
            Modifier.weight(1f),
        )
    }

    // The single clean button: the wizard keeps the guarded write and saves this TV's restore point first.
    PrimaryButton(
        stringResource(if (tv.clean) R.string.v16_clean_review else R.string.v16_clean_start),
        { onIntent(ManageIntent.CleanAgain) }, Modifier.padding(top = 12.dp), guardObscured = false,
    )

    DnsCard(state, tv, onIntent)

    ListGroup(Modifier.padding(top = 12.dp)) {
        SwitchRow(
            title = stringResource(R.string.v16_evening_title),
            subtitle = stringResource(R.string.v16_evening_sub),
            checked = tv.evening,
            enabled = tv.online && !state.busy16,
            onCheckedChange = { onIntent(ManageIntent.ToggleEvening) },
        )
    }
}

@Composable
private fun DnsCard(state: ManageState, tv: TvCard, onIntent: (ManageIntent) -> Unit) {
    val c = CytTheme.colors
    val mod = Modifier.fillMaxWidth().padding(top = 12.dp)
    if (tv.dns != DnsMode.NONE) {
        Box(mod.border(1.dp, c.outline, cardShape).background(c.surface1, cardShape).padding(12.dp)) {
            Text(stringResource(if (tv.dns == DnsMode.SAFE) R.string.v16_dns_safe_on else R.string.v16_dns_encrypted_on), color = c.textPrimary, style = CytTheme.type.bodyMedium)
        }
        return
    }
    when (state.port853) {
        Port853.UNKNOWN -> LiveLine(stringResource(R.string.v16_port_checking))
        Port853.BLOCKED -> Column(mod.border(1.dp, c.warning, cardShape).background(c.surface1, cardShape).padding(12.dp)) {
            Text(stringResource(R.string.v16_port_blocked_title), color = c.warning, fontWeight = FontWeight.SemiBold, style = CytTheme.type.bodyMedium)
            Body(stringResource(R.string.v16_port_blocked_body), Modifier.padding(top = 4.dp))
            DnsButton(R.string.v16_port_blocked_btn, c.warning, Color(0xFF2A2112), tv.online && !state.busy16) { onIntent(ManageIntent.ApplyDns) }
        }
        Port853.OPEN -> Column(mod.border(1.dp, c.outline, cardShape).background(c.surface1, cardShape).padding(12.dp)) {
            Text(stringResource(R.string.v16_port_open_title), color = c.textPrimary, fontWeight = FontWeight.SemiBold, style = CytTheme.type.bodyMedium)
            Body(stringResource(R.string.v16_port_open_body), Modifier.padding(top = 4.dp))
            DnsButton(R.string.v16_port_open_btn, c.primary, c.onPrimary, tv.online && !state.busy16) { onIntent(ManageIntent.ApplyDns) }
        }
    }
}

@Composable
private fun DnsButton(label: Int, bg: Color, fg: Color, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.padding(top = 10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
    ) { Text(stringResource(label), fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = CytTheme.colors
    Column(
        modifier.border(1.dp, c.outline, RoundedCornerShape(14.dp)).background(c.surface1, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = c.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        Text(value, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}
