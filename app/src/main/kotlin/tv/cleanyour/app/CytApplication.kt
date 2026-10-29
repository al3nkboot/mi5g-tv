package tv.cleanyour.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.io.File
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import tv.cleanyour.android.work.OperationService
import tv.cleanyour.core.flows.OperationNotices
import tv.cleanyour.core.flows.planOrNull

// 1.6: Arabic by default whatever the phone's language; English (or the phone's language) only when
// the person picks it in Settings. The choice lives in no-backup prefs and, on API 33+, is mirrored
// to the system per-app language (locales_config.xml) so Android's own settings agree.
internal object ArabicLocale {
    val ARABIC: Locale = Locale.forLanguageTag("ar")
    private const val PREFS = "cyt_language"
    private const val KEY = "lang"

    fun choice(context: Context): String {
        // API 33+: a language picked in Android's own app-language settings wins.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val set = context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales
            if (set != null && !set.isEmpty) return if (set[0].language == "en") AppLanguage.ENGLISH else AppLanguage.ARABIC
        }
        return AppLanguage.normalize(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun locale(context: Context): Locale = when (choice(context)) {
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.SYSTEM -> android.content.res.Resources.getSystem().configuration.locales[0]
        else -> ARABIC
    }

    fun save(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, AppLanguage.normalize(lang)).commit()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val lm = context.getSystemService(android.app.LocaleManager::class.java)
            lm?.applicationLocales = if (lang == AppLanguage.SYSTEM) android.os.LocaleList.getEmptyLocaleList()
                else android.os.LocaleList.forLanguageTags(locale(context).toLanguageTag())
        }
    }

    fun apply(context: Context) {
        val loc = locale(context)
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(loc)
        config.setLayoutDirection(loc)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
    }

    fun wrap(base: Context): Context {
        apply(base)
        val loc = locale(base)
        val config = Configuration(base.resources.configuration)
        config.setLocale(loc)
        config.setLayoutDirection(loc)
        return base.createConfigurationContext(config)
    }
}

class CytApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(ArabicLocale.wrap(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ArabicLocale.apply(this)
    }

    override fun onCreate() {
        super.onCreate()
        ArabicLocale.apply(this)
        CytLog.init(File(noBackupFilesDir, "logs"))
        graph = AppGraph(this)
        OperationService.Bus.channelName = getString(R.string.app_channel_setup_name)
        watchRuns()
        graph.sessionEnd.watch(graph.scope)
    }

    // D18: the foreground service follows the engine's runs. A plan that needs it (a transfer, an
    // install or more than 5 steps) starts it; the end of the run stops it within a second.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchRuns() {
        val g = graph
        g.scope.launch {
            g.tvs.tvs.flatMapLatest { list ->
                if (list.isEmpty()) flowOf(emptyList())
                else combine(list.map { tv -> g.seams.engine.runState(tv.id) }) { states ->
                    list.zip(states.toList()).mapNotNull { (tv, s) ->
                        val plan = s.planOrNull() ?: return@mapNotNull null
                        if (!OperationNotices.needsForeground(plan)) null else OperationNotices.of(tv.id, tv.nickname, s, g.copy)
                    }
                }
            }.collect { notices -> OperationService.Bus.publish(this@CytApplication, notices.firstOrNull()) }
        }
    }
}
