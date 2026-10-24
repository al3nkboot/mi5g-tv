@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package tv.cleanyour.feature.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.CardAnswer
import tv.cleanyour.core.flows.IndexView
import tv.cleanyour.core.flows.InstallStep
import tv.cleanyour.core.flows.ModuleDetail
import tv.cleanyour.core.flows.ModuleRow
import tv.cleanyour.core.flows.ModulesIntent
import tv.cleanyour.core.flows.ModulesState
import tv.cleanyour.core.flows.UpdateConsent
import tv.cleanyour.core.model.Tier
import tv.cleanyour.core.registry.FetchFailure
import tv.cleanyour.core.trust.IndexFailure
import tv.cleanyour.ui.design.component.ActionColumn
import tv.cleanyour.ui.design.component.AppTile
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CardPromptMirror
import tv.cleanyour.ui.design.component.CheckLine
import tv.cleanyour.ui.design.component.CheckState
import tv.cleanyour.ui.design.component.CytBottomSheet
import tv.cleanyour.ui.design.component.CytTextField
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.InfoChip
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.ModuleCard
import tv.cleanyour.ui.design.component.ModuleHero
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.ProgressBar
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.SheetHeader
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.component.StopCard
import tv.cleanyour.ui.design.component.TertiaryButton
import tv.cleanyour.ui.design.component.ltr
import tv.cleanyour.ui.design.component.rememberEmphasis
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

// M3 Cards and modules, M3a Add, M3b the module page, M3c Look at the TV (UX 5, D1, D6, D45, F9).
// Version strings come from the signed index only, never from copy (D6). Each sheet keeps its body
// in an internal *Content composable, so the screenshot tests can draw it on SheetSurface.
@Composable
fun ModulesFlow(state: ModulesState, onIntent: (ModulesIntent) -> Unit, onBack: () -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val install = state.install
    val detail = state.detail
    when {
        install != null -> InstallScreen(install, state, onIntent, modifier)
        detail != null -> DetailScreen(detail, state.tvName, onIntent, onOpenUrl, modifier, cardsOff = state.cardsOff)
        adding -> AddScreen(state, onIntent, onBack = { adding = false }, modifier)
        else -> InstalledScreen(state, onIntent, onAdd = { adding = true }, onBack, modifier)
    }
    state.consent?.let { c ->
        CytBottomSheet({ onIntent(ModulesIntent.SkipUpdate) }) { ConsentContent(c, onIntent) }
    }
}

fun megabytes(bytes: Long?): Int = if (bytes == null) 0 else maxOf(1, ((bytes + 512 * 1024) / (1024 * 1024)).toInt())

// The module's own accent from the signed index ("#E8A33D"), for its glow and hero card only.
internal fun accentColor(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

@Composable
private fun IndexNotice(index: IndexView, onIntent: (ModulesIntent) -> Unit) {
    when (index) {
        is IndexView.Ready -> if (index.stale) Banner(stringResource(R.string.index_stale_warning), BannerTone.WARNING, Modifier.padding(top = 16.dp),
            actionLabel = stringResource(R.string.addmodule_refresh), onAction = { onIntent(ModulesIntent.Refresh) })
        is IndexView.Failed -> Banner(indexFailureText(index), BannerTone.WARNING, Modifier.padding(top = 16.dp),
            actionLabel = stringResource(R.string.addmodule_refresh), onAction = { onIntent(ModulesIntent.Refresh) })
        IndexView.Loading -> LiveLine(stringResource(R.string.addmodule_loading))
    }
}

// F9: one copy line per failure layer.
@Composable
fun indexFailureText(f: IndexView.Failed): String = when {
    f.verdict != null -> stringResource(when (f.verdict!!) {
        IndexFailure.BAD_SIGNATURE -> R.string.index_failure_bad_signature
        IndexFailure.UNKNOWN_KEY -> R.string.index_failure_unknown_key
        IndexFailure.REVOKED_KEY -> R.string.index_failure_revoked_key
        IndexFailure.BAD_KEYS_FILE -> R.string.index_failure_bad_keys_file
        IndexFailure.EXPIRED -> R.string.index_failure_expired
        IndexFailure.ROLLBACK -> R.string.index_failure_rollback
        IndexFailure.MALFORMED -> R.string.index_failure_malformed
    })
    f.fetch != null -> fetchFailureText(f.fetch!!)
    else -> stringResource(R.string.index_stale_warning)
}

@Composable
fun fetchFailureText(f: FetchFailure): String = when (f) {
    FetchFailure.OFFLINE -> stringResource(R.string.fetch_failure_offline)
    FetchFailure.HOST_NOT_ALLOWED -> stringResource(R.string.fetch_failure_host_not_allowed)
    FetchFailure.HTTP_ERROR -> msg(R.string.fetch_failure_http_error, "code" to "")
    FetchFailure.TOO_LARGE -> stringResource(R.string.fetch_failure_too_large)
    FetchFailure.HASH_MISMATCH -> stringResource(R.string.fetch_failure_hash_mismatch)
    FetchFailure.SIZE_MISMATCH -> stringResource(R.string.fetch_failure_size_mismatch)
    FetchFailure.SIGNER_MISMATCH -> stringResource(R.string.fetch_failure_signer_mismatch)
}

@Composable
private fun tierWord(t: Tier): String = stringResource(when (t) {
    Tier.VERIFIED -> R.string.addmodule_tier_verified
    Tier.COMMUNITY -> R.string.addmodule_tier_community
    Tier.QUARANTINED, Tier.REVOKED -> R.string.addmodule_tier_paused
})

private val titleTop = Modifier.padding(top = 20.dp)

// M3: installed modules with their state, Remove and Update.
@Composable
internal fun InstalledScreen(state: ModulesState, onIntent: (ModulesIntent) -> Unit, onAdd: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) },
        bottomBar = { PrimaryButton(stringResource(R.string.modules_add), onAdd, guardObscured = false) }) {   // navigation
        ScreenTitle(stringResource(R.string.modules_title), titleTop)
        if (state.installed.isEmpty()) {
            Lead(stringResource(R.string.modules_empty), Modifier.padding(top = 12.dp))
        } else {
            // RFC-CARDS-1: the TV's cards are off until the person shows one.
            if (state.cardsOff) Lead(stringResource(R.string.modules_cards_off_note), Modifier.padding(top = 12.dp))
            ListGroup(Modifier.padding(top = 20.dp)) {
                state.installed.forEachIndexed { i, m ->
                    if (i > 0) ListDivider()
                    val stateWord = when {
                        m.tier == Tier.REVOKED || m.tier == Tier.QUARANTINED -> stringResource(R.string.addmodule_tier_paused)
                        m.card == CardAnswer.SHOWN -> stringResource(R.string.modules_state_shown)
                        m.card == CardAnswer.HIDDEN -> stringResource(R.string.modules_state_hidden)
                        else -> null
                    }
                    ListRow(m.name, leading = { AppTile(m.name) },
                        subtitle = listOfNotNull(stateWord, m.updateTo?.let { msg(R.string.modules_update_available, "version" to it) }).joinToString(" · ").ifEmpty { null })
                    // The row's actions sit under it, so a long name and two actions never squeeze at 200% font.
                    FlowRow(Modifier.padding(start = 62.dp, end = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.Center) {
                        if (m.updateTo != null) TertiaryButton(stringResource(R.string.modules_update), { onIntent(ModulesIntent.Update(m.id)) })
                        if (state.cardsOff) {
                            if (state.showing == m.id) Row(Modifier.padding(16.dp)) { Spinner() }
                            else TertiaryButton(stringResource(R.string.modules_show_card), { onIntent(ModulesIntent.ShowCard(m.id)) },
                                enabled = state.showing == null && state.removing == null, guardObscured = true)   // a write (O1)
                        }
                        if (state.removing == m.id) Row(Modifier.padding(16.dp)) { Spinner() }
                        else TertiaryButton(stringResource(R.string.modules_remove), { removing = m.id }, enabled = state.removing == null && state.showing == null, quiet = true)
                    }
                }
            }
        }
    }
    removing?.let { id ->
        val m = state.installed.firstOrNull { it.id == id }
        CytBottomSheet({ removing = null }) {
            RemoveContent(m?.name.orEmpty(), state.tvName, { onIntent(ModulesIntent.Remove(id)); removing = null }, { removing = null })
        }
    }
}

@Composable
internal fun ColumnScope.RemoveContent(name: String, tvName: String, onRemove: () -> Unit, onCancel: () -> Unit) {
    SheetHeader(msg(R.string.modules_remove_title, "name" to name, "tv" to tvName))
    Body(stringResource(R.string.modules_remove_body), Modifier.padding(top = 8.dp))
    ActionColumn(Modifier.padding(top = 20.dp)) {
        PrimaryButton(stringResource(R.string.modules_remove_confirm), onRemove)
        TertiaryButton(stringResource(R.string.action_cancel), onCancel, quiet = true)
    }
}

// The update re-consent (UX 5.4): a new version that asks for more.
@Composable
internal fun ColumnScope.ConsentContent(c: UpdateConsent, onIntent: (ModulesIntent) -> Unit) {
    Body(msg(R.string.modules_update_consent, "name" to c.name, "version" to c.version, "permissions" to c.added.joinToString(", ") { it.plainWords }),
        Modifier.padding(top = 12.dp), color = CytTheme.colors.textPrimary)
    ActionColumn(Modifier.padding(top = 20.dp)) {
        PrimaryButton(stringResource(R.string.modules_update), { onIntent(ModulesIntent.ConfirmUpdate) })
        TertiaryButton(stringResource(R.string.modules_update_skip), { onIntent(ModulesIntent.SkipUpdate) }, quiet = true)
    }
}

// M3a Add to Mi TV 4A: search, module cards with their accent glow, the community footer.
@Composable
internal fun AddScreen(state: ModulesState, onIntent: (ModulesIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var tiers by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) }) {
        ScreenTitle(msg(R.string.addmodule_title, "tv" to state.tvName), titleTop)
        CytTextField(state.query, { onIntent(ModulesIntent.Search(it)) }, stringResource(R.string.addmodule_search_hint), Modifier.padding(top = 16.dp))
        IndexNotice(state.index, onIntent)
        if (state.index !is IndexView.Loading && state.filtered.isEmpty()) Body(stringResource(R.string.addmodule_empty), Modifier.padding(top = 16.dp, start = 4.dp))
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.filtered.forEach { m ->
                ModuleCard(m.name, m.oneLine, moduleChips(m.tier, m.licence, m.sizeBytes), accentColor(m.accent) ?: CytTheme.colors.glowBrand,
                    { onIntent(ModulesIntent.Open(m.id)) })
            }
        }
        Body(stringResource(R.string.addmodule_footer), Modifier.padding(top = 20.dp, start = 4.dp, end = 4.dp))
        TertiaryButton(stringResource(R.string.addmodule_footer_link), { tiers = true }, Modifier.padding(bottom = 16.dp))
    }
    if (tiers) CytBottomSheet({ tiers = false }) { TiersContent() }
}

@Composable
private fun moduleChips(tier: Tier, licence: String, sizeBytes: Long?): List<String> = listOfNotNull(
    tierWord(tier),
    if (licence.isNotBlank()) stringResource(R.string.addmodule_open_source) else null,
    sizeBytes?.let { msg(R.string.addmodule_size, "size" to megabytes(it)) },
)

@Composable
internal fun ColumnScope.TiersContent() {
    SheetHeader(stringResource(R.string.addmodule_tiers_title))
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Body(stringResource(R.string.addmodule_tiers_community))
        Body(stringResource(R.string.addmodule_tiers_verified))
        Body(stringResource(R.string.addmodule_tiers_paused))
    }
}

// M3b the module page (UX 5.2 with D1 and D6; mockup 07).
@Composable
internal fun DetailScreen(d: ModuleDetail, tvName: String, onIntent: (ModulesIntent) -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier, cardsOff: Boolean = false) {
    val m = d.module
    val v = d.version
    val paused = m.tier == Tier.REVOKED || m.tier == Tier.QUARANTINED
    val accent = accentColor(m.accent)
    val c = CytTheme.colors
    ScreenScaffold(accent?.let { Glow.Module(it) } ?: Glow.Brand, modifier,
        topBar = { CytTopBar(onBack = { onIntent(ModulesIntent.CloseDetail) }) },
        bottomBar = when {
            paused && d.installed -> ({ PrimaryButton(stringResource(R.string.modules_remove), { onIntent(ModulesIntent.Remove(m.id)) }) })
            !paused && !d.installed -> ({ PrimaryButton(msg(R.string.module_add, "tv" to tvName), { onIntent(ModulesIntent.Add(m.id)) }) })
            else -> null
        }) {
        ModuleHero(m.name, m.oneLine, accent ?: c.glowBrand, Modifier.padding(top = 8.dp),
            badge = if (m.type == "card") stringResource(R.string.module_hero_badge) else null)
        ScreenTitle(m.name, Modifier.padding(top = 16.dp))
        Body(m.description, Modifier.padding(top = 4.dp))
        // F-Droid Inclusion Policy (security 5): before the first download, say where add-ons come
        // from. Back declines with one tap, as Add accepts with one, and nothing downloads before
        // the credential prompt that follows Add.
        if (!paused && !d.installed) Body(stringResource(R.string.module_outside_store), Modifier.padding(top = 8.dp))
        // RFC-CARDS-1: the add's plan also turns the TV's cards on; say so before the person taps it.
        if (cardsOff && !paused && !d.installed) Body(stringResource(R.string.module_add_turns_cards_on), Modifier.padding(top = 8.dp), color = c.textPrimary)
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            moduleChips(m.tier, m.licence, v.artifact.size).forEach { InfoChip(it) }
        }
        if (paused) Banner(msg(R.string.module_paused_reason, "reason" to m.description), BannerTone.WARNING, Modifier.padding(top = 16.dp))
        SectionLabel(stringResource(R.string.module_what_title))
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            v.permissions.forEach { p ->
                val cd = msg(R.string.module_perm_can_cd, "statement" to p.plainWords)
                CheckLine(p.plainWords, Modifier.semantics { contentDescription = cd }, emphasised = true)
            }
            if (v.networkStatement.isNotBlank()) CheckLine(v.networkStatement, emphasised = true)
            if (m.type == "service") CheckLine(msg(R.string.module_runs_background, "reason" to m.oneLine), emphasised = true)
            else CheckLine(stringResource(R.string.module_no_background), state = CheckState.NONE)
        }
        SectionLabel(stringResource(R.string.module_who_title))
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rememberEmphasis(msg(R.string.module_maker, "handle" to m.publisher), m.publisher), color = c.textSecondary, style = CytTheme.type.bodyMedium)
            Body(msg(R.string.module_version_line, "version" to v.versionName, "date" to v.releasedAt))
            // D1: the first 12 hex of the signer digest in groups of 4, exactly what the TV prompt prints.
            val key = ltr(d.key12)
            Text(rememberEmphasis(msg(R.string.module_key_line, "key" to key), key), color = c.textSecondary, style = CytTheme.type.bodyMedium)
            Body(msg(R.string.module_checks, "count" to v.permissions.size))
            Body(stringResource(R.string.module_not_reviewed))
        }
        FlowRow(Modifier.padding(top = 8.dp, bottom = 16.dp)) {
            TertiaryButton(stringResource(R.string.module_source), { onOpenUrl(m.sourceUrl) })
            TertiaryButton(stringResource(R.string.module_report), { onOpenUrl(m.sourceUrl.trimEnd('/') + "/issues") })
        }
    }
}

// The install narration, then M3c (D1, D45).
@Composable
internal fun InstallScreen(step: InstallStep, state: ModulesState, onIntent: (ModulesIntent) -> Unit, modifier: Modifier = Modifier) {
    val name = state.detail?.module?.name ?: ""
    // Done only leaves the screen, so it is not guarded against overlays (R5-W8 O1).
    val done: @Composable ColumnScope.() -> Unit = { PrimaryButton(stringResource(R.string.action_done), { onIntent(ModulesIntent.DismissInstall) }, guardObscured = false) }
    val bottom: (@Composable ColumnScope.() -> Unit)? = when (step) {
        // 0.1 cannot read the TV's answer without the provider: the person tells us (CardAnswerDetector).
        is InstallStep.Asking -> ({
            ActionColumn {
                SecondaryButton(msg(R.string.cardprompt_shown, "name" to step.name), { onIntent(ModulesIntent.Answered(CardAnswer.SHOWN)) })
                TertiaryButton(stringResource(R.string.cardprompt_art_keep), { onIntent(ModulesIntent.Answered(CardAnswer.HIDDEN)) })
            }
        })
        is InstallStep.Shown, is InstallStep.Hidden -> done
        else -> null
    }
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = { onIntent(ModulesIntent.DismissInstall) }) }, bottomBar = bottom) {
        when (step) {
            is InstallStep.Downloading -> {
                ScreenTitle(msg(R.string.module_install_adding, "name" to name), titleTop)
                LiveLine(msg(R.string.module_install_downloading, "size" to megabytes(step.total)))
                ProgressBar(if (step.total > 0) step.done.toFloat() / step.total else 0f, Modifier.padding(top = 16.dp))
            }
            InstallStep.Checking -> {
                ScreenTitle(msg(R.string.module_install_adding, "name" to name), titleTop)
                LiveLine(stringResource(R.string.module_install_checking))
            }
            is InstallStep.Installing -> {
                ScreenTitle(msg(R.string.module_install_adding, "name" to name), titleTop)
                LiveLine(stringResource(R.string.module_install_installing))
                ProgressBar(step.percent / 100f, Modifier.padding(top = 16.dp))
            }
            is InstallStep.Asking -> {
                val key = ltr(step.key12)
                ScreenTitle(stringResource(R.string.cardprompt_title), titleTop)
                CardPromptMirror(step.name, step.pkg, step.key12, Modifier.padding(top = 20.dp),
                    contentDescription = msg(R.string.cardprompt_art_cd, "name" to step.name, "key" to step.key12))
                Lead(stringResource(R.string.cardprompt_body), Modifier.padding(top = 20.dp))
                Text(rememberEmphasis(msg(R.string.cardprompt_instruction, "key" to key), "\"Show card\"", key),
                    Modifier.padding(top = 10.dp), color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyMedium)
                LiveLine(stringResource(R.string.cardprompt_waiting))
                Body(stringResource(R.string.cardprompt_later), Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 8.dp))
            }
            is InstallStep.Shown -> {
                ScreenTitle(stringResource(R.string.cardprompt_title), titleTop)
                Banner(msg(R.string.cardprompt_shown, "name" to step.name), BannerTone.SUCCESS, Modifier.padding(top = 16.dp))
            }
            is InstallStep.Hidden -> {
                ScreenTitle(stringResource(R.string.cardprompt_title), titleTop)
                Banner(msg(R.string.cardprompt_hidden, "name" to step.name), BannerTone.NEUTRAL, Modifier.padding(top = 16.dp))
            }
            is InstallStep.Failed -> {
                StopCard(msg(R.string.module_install_failed, "name" to step.name), step.fetch?.let { fetchFailureText(it) } ?: "", Modifier.padding(top = 20.dp),
                    primaryLabel = stringResource(R.string.action_ok), onPrimary = { onIntent(ModulesIntent.DismissInstall) }, primaryGuarded = false)
            }
        }
    }
}
