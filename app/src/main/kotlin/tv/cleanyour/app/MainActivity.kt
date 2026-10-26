package tv.cleanyour.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import tv.cleanyour.android.platform.AndroidCredentialGate
import tv.cleanyour.core.flows.resolveStart
import tv.cleanyour.ui.design.theme.CytTheme

// The one exported component (SECURITY 4.2). It ignores data URIs and all external payloads; the
// only extra is a harmless request to open app settings from the update notification.
class MainActivity : ComponentActivity() {
    private val graph get() = (application as CytApplication).graph
    internal var updateOpenRequest by mutableStateOf(0)
        private set

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ArabicLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Always dark (D-W8-27): light icons on transparent bars whatever the phone's own mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        ArabicLocale.apply(this)
        graph.credentialGate.attach(this)
        setContent {
            // D-W8-27: 0.1 is always the dark, black-and-glass design (mockups, DESIGN-SYSTEM 2),
            // whatever the phone's own mode. The light tokens stay in :ui:design, unused.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CytTheme(darkTheme = true) {
                    var start by remember { mutableStateOf<Dest?>(null) }
                    LaunchedEffect(Unit) {
                        graph.tvs.load()
                        start = if (intent.getBooleanExtra(AndroidAppUpdateController.EXTRA_OPEN_UPDATES, false) && graph.store.rules.updateCheck)
                            Dest.AppSettings else startKey(resolveStart(graph.tvs.tvs.value, graph.seams.journal))
                    }
                    val s = start
                    if (s == null) Box(Modifier.fillMaxSize().background(CytTheme.colors.bg))
                    else AppRoot(graph, s, updateOpenRequest = updateOpenRequest, onExit = { finish() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(AndroidAppUpdateController.EXTRA_OPEN_UPDATES, false)) updateOpenRequest++
    }

    override fun onStart() { super.onStart(); graph.foreground.set(true) }
    override fun onStop() { graph.foreground.set(false); super.onStop() }

    override fun onDestroy() {
        graph.credentialGate.attach(null)
        super.onDestroy()
    }

    // API 28's keyguard confirm-credential screen (AndroidCredentialGate).
    @Deprecated("Activity result API is used everywhere else; the keyguard intent needs this on API 28")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AndroidCredentialGate.REQUEST_CONFIRM) graph.credentialGate.onConfirmCredentialResult(resultCode == RESULT_OK)
    }
}
