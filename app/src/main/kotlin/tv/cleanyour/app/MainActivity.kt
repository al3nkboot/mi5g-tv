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

// The one exported component (SECURITY 4.2). It reads no extras and no data URI: every start
// opens the destination decided from disk.
class MainActivity : ComponentActivity() {
    private val graph get() = (application as CytApplication).graph

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
                        start = startKey(resolveStart(graph.tvs.tvs.value, graph.seams.journal))
                    }
                    val s = start
                    if (s == null) Box(Modifier.fillMaxSize().background(CytTheme.colors.bg))
                    else AppRoot(graph, s, onExit = { finish() })
                }
            }
        }
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
