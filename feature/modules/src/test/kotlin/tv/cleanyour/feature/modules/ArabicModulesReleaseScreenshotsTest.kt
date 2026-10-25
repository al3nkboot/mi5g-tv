package tv.cleanyour.feature.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.flows.IndexView
import tv.cleanyour.core.flows.ModuleDetail
import tv.cleanyour.core.flows.ModuleRow
import tv.cleanyour.core.flows.ModulesState
import tv.cleanyour.core.trust.IndexDocument
import tv.cleanyour.ui.design.theme.CytTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w360dp-h1600dp-xhdpi")
class ArabicModulesReleaseScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private var prevLocale: Locale = Locale.getDefault()

    @Before fun setUpLocale() {
        prevLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreLocale() {
        Locale.setDefault(prevLocale)
    }

    @Test fun renderArabicModulesCatalogAndDetail() {
        val outDir = File("/workspace/apps/mi5g-tv-out/screens/1.2.0").apply { mkdirs() }
        val repoRoot = File(System.getProperty("cleanyourtv.repoRoot") ?: "/workspace/apps/mi5g-tv")
        val indexBytes = File(repoRoot, "android/platform/src/main/assets/bundled/index.json").readBytes()
        val doc = IndexDocument.parse(indexBytes)
        val rows = doc.modules.map { m ->
            val v = m.versions.first()
            ModuleRow(
                id = m.id,
                name = m.name,
                oneLine = m.oneLine,
                sizeBytes = v.artifact.size,
                licence = m.licence,
                tier = m.tier,
                accent = m.accent,
                installed = false,
                card = null,
                updateTo = null,
            )
        }
        val state = ModulesState(
            tvName = "AndroidTV",
            index = IndexView.Ready(stale = false),
            installed = emptyList(),
            available = rows,
        )
        val dnsModule = doc.modules.first { it.id == "adguard-dns" }
        val dnsDetail = ModuleDetail(
            module = dnsModule,
            version = dnsModule.versions.first(),
            key12 = "c1f3 1796 5f15",
            installed = false,
        )

        var currentHeight by mutableStateOf(1220.dp)
        var currentScreen by mutableStateOf<@Composable () -> Unit>({ AddScreen(state, {}, {}) })

        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CytTheme(darkTheme = true, reducedMotion = true) {
                    Box(Modifier.testTag("shot").size(360.dp, currentHeight).background(CytTheme.colors.bg)) {
                        currentScreen()
                    }
                }
            }
        }

        fun record(fileName: String, height: Dp, screen: @Composable () -> Unit) {
            currentHeight = height
            currentScreen = screen
            compose.waitForIdle()
            compose.onNodeWithTag("shot").captureRoboImage(File(outDir, fileName))
        }

        record("02_add_modules_catalog_ar.png", 1460.dp) {
            AddScreen(state, {}, {})
        }

        record("03_module_detail_ar.png", 1180.dp) {
            DetailScreen(dnsDetail, "AndroidTV", {}, {}, cardsOff = false)
        }
    }
}
