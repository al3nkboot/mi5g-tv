package tv.cleanyour.feature.manage

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
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
import tv.cleanyour.core.flows.AppPrefsValue
import tv.cleanyour.core.flows.AppSettingsState
import tv.cleanyour.core.flows.AppUpdateAsset
import tv.cleanyour.core.flows.AppUpdateFailure
import tv.cleanyour.core.flows.AppUpdateRelease
import tv.cleanyour.core.flows.AppUpdateState
import tv.cleanyour.core.flows.UpdateCheck
import tv.cleanyour.core.model.Sha256

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w360dp-h2400dp-xhdpi")
class ArabicAppUpdateScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private var previousLocale: Locale = Locale.getDefault()

    @Before fun setUpLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    private val release = AppUpdateRelease(
        version = "1.6.0",
        title = "Mi5G TV 1.6.0",
        publishedAt = "2026-10-27T00:00:00Z",
        notes = "تحسينات الأداء وإصلاحات الأمان.",
        releaseUrl = "https://github.com/al3nkboot/mi5g-tv/releases/tag/v1.6.0",
        asset = AppUpdateAsset(
            "Mi5G-TV-v1.6.0.apk",
            "https://github.com/al3nkboot/mi5g-tv/releases/download/v1.6.0/Mi5G-TV-v1.6.0.apk",
            12_345_678L,
            Sha256("a".repeat(64)),
        ),
    )

    private fun settings() = AppSettingsState(
        version = "1.5.0",
        prefs = AppPrefsValue(haptics = true, largeTextFollowsSystem = true),
        launcherPkg = "com.gathin.tvhome",
        launcherVersion = "1.5.0",
        launcherDigest = "9f2c41d07a6be8135c4d0e72ab19f5c3",
        keyBacking = "STRONGBOX",
        keyFingerprint = "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:7F:2A",
        update = UpdateCheck.Idle,
    )

    private fun capture(name: String, state: AppUpdateState) {
        compose.shoot(
            Shot(name, 1100.dp) {
                AppSettingsScreen(settings(), {}, {}, {}, updateStatus = state)
            },
            Variant.RTL,
        )
    }

    @Test fun availableReleaseUsesArabicAndRtl() {
        capture("appsettings_arabic_release_available", AppUpdateState.Available(release, showPrompt = true))
        compose.onNodeWithText("تحديث Mi5G TV").assertExists()
        compose.onNodeWithText("تنزيل التحديث").assertExists()
    }

    @Test fun downloadingReleaseUsesArabicAndRtl() {
        capture("appsettings_arabic_release_downloading", AppUpdateState.Downloading(release, 6_172_839L, 12_345_678L))
        compose.onNodeWithText("جارٍ التنزيل", substring = true).assertExists()
    }

    @Test fun failedReleaseUsesArabicAndRtl() {
        capture("appsettings_arabic_release_failed", AppUpdateState.Failed(AppUpdateFailure.NETWORK, release, showPrompt = true))
        compose.onNodeWithText("تحديث Mi5G TV").assertExists()
        compose.onNodeWithText("تعذر الوصول إلى GitHub. تحقق من اتصال الإنترنت ثم أعد المحاولة.").assertExists()
    }
}
