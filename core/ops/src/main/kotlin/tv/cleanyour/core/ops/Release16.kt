package tv.cleanyour.core.ops

import tv.cleanyour.core.model.AdbError

// 1.6 stability helpers. Pure: the machines decide when to call them, and the
// strings are the user-facing reason plus one next step. Pairing codes never
// enter the diagnostic text.
object Release16 {
    const val MIN_SDK = 26
    const val LAUNCHER_PKG = "com.gathin.tvhome"

    enum class LinkFailure {
        SAME_WIFI,
        PORT_CLOSED,
        PAIRING_REFUSED,
        SCREEN_COVERED,
        TOO_OLD,
        WIRELESS_MISSING,
        GENERIC,
    }

    fun classifyLink(error: AdbError, sdk: Int?, onWifi: Boolean, wirelessPresent: Boolean): LinkFailure = when {
        sdk != null && sdk < MIN_SDK -> LinkFailure.TOO_OLD
        !onWifi -> LinkFailure.SAME_WIFI
        error == AdbError.Transport.Refused || error is AdbError.Transport.Timeout -> LinkFailure.PORT_CLOSED
        error == AdbError.Auth.Denied || error == AdbError.Auth.PendingTooLong || error is AdbError.Auth.TrustChanged -> LinkFailure.PAIRING_REFUSED
        error == AdbError.Device.Busy || error == AdbError.Device.InstallRefusedByFirmware -> LinkFailure.SCREEN_COVERED
        error == AdbError.Transport.Unreachable && !wirelessPresent -> LinkFailure.WIRELESS_MISSING
        error == AdbError.Transport.Unreachable || error == AdbError.Transport.Closed -> LinkFailure.PORT_CLOSED
        error == AdbError.Transport.NoShellV2 -> LinkFailure.TOO_OLD
        else -> LinkFailure.GENERIC
    }

    fun linkMessage(failure: LinkFailure, arabic: Boolean): String = if (arabic) arabicLink(failure) else englishLink(failure)

    fun tooOldMessage(sdk: Int, arabic: Boolean): String {
        val base = linkMessage(LinkFailure.TOO_OLD, arabic)
        return if (arabic) "$base الإصدار المكتشف: Android API $sdk، والحد الأدنى هو $MIN_SDK (أندرويد 8)."
        else "$base Detected API $sdk. Minimum is $MIN_SDK (Android 8)."
    }

    fun planRefusalMessage(reasons: List<String>, arabic: Boolean): String {
        val joined = reasons.joinToString(" ").lowercase()
        val failure = when {
            "older android" in joined || "android 8" in joined -> LinkFailure.TOO_OLD
            "refuses other home" in joined || "refuses app installs" in joined || "fire" in joined -> LinkFailure.SCREEN_COVERED
            "not possible" in joined || "not one of the known" in joined -> LinkFailure.GENERIC
            else -> LinkFailure.GENERIC
        }
        val head = linkMessage(failure, arabic)
        val detail = reasons.firstOrNull()?.take(180).orEmpty()
        return if (detail.isEmpty()) head else "$head\n$detail"
    }

    fun diagnostic(
        brand: String?,
        model: String?,
        release: String?,
        sdk: Int?,
        step: String,
        error: String,
        arabic: Boolean,
    ): String {
        val safeError = redact(error).ifBlank { if (arabic) "غير معروف" else "unknown" }
        val b = brand?.let(::redact).orEmpty().ifBlank { "-" }
        val m = model?.let(::redact).orEmpty().ifBlank { "-" }
        val rel = release?.let(::redact).orEmpty().ifBlank { "-" }
        val api = sdk?.toString() ?: "-"
        return if (arabic) {
            """
            تشخيص Mi5G TV 1.6
            الماركة: $b
            الموديل: $m
            أندرويد: $rel (API $api)
            الخطوة: ${redact(step)}
            الخطأ: $safeError
            لا يتضمن هذا النص رمز اقتران أو مفاتيح.
            """.trimIndent()
        } else {
            """
            Mi5G TV 1.6 diagnostic
            Brand: $b
            Model: $m
            Android: $rel (API $api)
            Step: ${redact(step)}
            Error: $safeError
            This text has no pairing code or keys.
            """.trimIndent()
        }
    }

    fun redact(raw: String): String = raw
        .replace(Regex("\\b\\d{6}\\b"), "[redacted]")
        .replace(Regex("(?i)pairing code\\s*[:=]?\\s*\\S+"), "pairing code [redacted]")
        .take(400)

    data class HttpFailure(val name: String, val code: Int, val message: String)

    fun httpFailure(name: String, code: Int, arabic: Boolean): HttpFailure {
        val safe = name.ifBlank { if (arabic) "عنصر" else "item" }.take(80)
        val message = if (arabic) {
            "فشل $safe برمز $code. أعد المحاولة، أو استخدم المصدر المحلي إن وُجد. لا تثبيت صامت."
        } else {
            "$safe failed with status $code. Retry, or use the local copy if one exists. Nothing was installed silently."
        }
        return HttpFailure(safe, code, message)
    }

    data class InstallChoice(
        val id: String,
        val label: String,
        val url: String?,
        val code: String?,
        val similarPackages: List<String>,
    )

    fun optionalInstalls(): List<InstallChoice> = listOf(
        InstallChoice("smarttube", "SmartTube", "https://github.com/yuliskov/SmartTube/releases/latest/download/SmartTube_stable_arm64-v8a.apk", null, listOf("com.teamsmart.videomanager.tv", "com.google.android.youtube.tv")),
        InstallChoice("yacine", "Yacine TV", null, "401740", listOf("com.yacine.tv")),
        InstallChoice("dns", "DNS", null, null, listOf("com.midroid.mi5gtv.dns")),
        InstallChoice("downloader", "Downloader", null, "250931", listOf("com.esaba.downloader")),
        InstallChoice("vlc", "VLC", "https://play.google.com/store/apps/details?id=org.videolan.vlc", null, listOf("org.videolan.vlc")),
    )

    fun bundledFallback(name: String): Pair<String, String>? = when {
        "dns" in name.lowercase() -> "adguard-dns" to "com.midroid.mi5gtv.dns"
        "مبار" in name || "matches" in name.lowercase() -> "tv-matches" to "com.midroid.mi5gtv.matches"
        "صلاة" in name || "prayer" in name.lowercase() -> "prayer-weather" to "com.midroid.mi5gtv.prayer"
        "ملفات" in name || "files" in name.lowercase() -> "tv-files" to "com.midroid.mi5gtv.files"
        "سرعة" in name || "speed" in name.lowercase() -> "tv-speedtest" to "com.midroid.mi5gtv.speedtest"
        else -> null
    }

    fun similarWarning(label: String, hit: String, arabic: Boolean): String = if (arabic) {
        "يوجد تطبيق مشابه مثبت ($hit) أثناء اختيار $label. الاكتفاء بواحد يتجنب تعارض الصلاحيات. يمكنك المتابعة رغم ذلك."
    } else {
        "A similar app is already installed ($hit) while choosing $label. Keeping one avoids a permission clash. You can continue anyway."
    }

    fun noneSelected(arabic: Boolean): String = if (arabic) {
        "لم يُختر أي برنامج. لا يُثبَّت شاهد أو غيره إلا إذا اخترته."
    } else {
        "Nothing is selected. Shahid and the other apps install only if you choose them."
    }

    enum class MapFail { NO_SESSION, BAD_PACKAGE, TARGET_MISSING, NOT_OUR_HOME, WRITE_FAILED, DIGEST_MISMATCH, FOREGROUND }

    fun mapMessage(fail: MapFail, arabic: Boolean): String = if (arabic) when (fail) {
        MapFail.NO_SESSION -> "لا يوجد اتصال بالتلفزيون. أعد الربط ثم أعد المحاولة."
        MapFail.BAD_PACKAGE -> "اسم التطبيق الهدف غير صالح. لم يُحفظ التحويل."
        MapFail.TARGET_MISSING -> "التطبيق الهدف غير مثبت. ثبّته أولاً ثم أعد تحويل الزر."
        MapFail.NOT_OUR_HOME -> "الواجهة الحالية ليست واجهة Mi5G، لذلك التحويل لا يثبت. ثبّت الواجهة ثم أعد المحاولة."
        MapFail.WRITE_FAILED -> "أمر الكتابة فشل. أوقف التطبيق الذي يغطي الشاشة ثم أعد المحاولة. الزر لم يُفعَّل."
        MapFail.DIGEST_MISMATCH -> "كُتبت الأوامر لكن الإعداد لم يُحفظ. التحويل غير مفعّل. أعد المحاولة بعد إيقاف ما يغطي الشاشة."
        MapFail.FOREGROUND -> "حُفظ التحويل، لكن إعادة تشغيل الواجهة فشلت لأن تطبيقاً يغطي الشاشة. أوقفه ثم أعد تشغيل التلفاز."
    } else when (fail) {
        MapFail.NO_SESSION -> "No TV connection. Connect again, then retry."
        MapFail.BAD_PACKAGE -> "The target package name is invalid. The mapping was not saved."
        MapFail.TARGET_MISSING -> "The target app is not installed. Install it, then map the key again."
        MapFail.NOT_OUR_HOME -> "The current home screen is not the Mi5G launcher, so the mapping cannot stick. Install that home screen, then retry."
        MapFail.WRITE_FAILED -> "The write command failed. Close the app covering the screen, then retry. The key was not mapped."
        MapFail.DIGEST_MISMATCH -> "Commands ran, but the setting was not saved. The mapping is not active. Retry after closing whatever covers the screen."
        MapFail.FOREGROUND -> "The mapping was saved, but restarting the home screen failed because an app is covering it. Close that app, then reboot the TV."
    }

    fun mapSaved(arabic: Boolean): String = if (arabic) {
        "تم الحفظ في إعداد الواجهة ويبقى بعد إعادة التشغيل."
    } else {
        "Saved in the launcher settings and kept after reboot."
    }

    private fun arabicLink(failure: LinkFailure): String = when (failure) {
        LinkFailure.SAME_WIFI -> "الهاتف والتلفاز ليسا على شبكة الواي فاي نفسها. صلهما بالشبكة نفسها ثم أعد المحاولة."
        LinkFailure.PORT_CLOSED -> "منفذ الربط مغلق. فعّل تصحيح USB وتصحيح الشبكة، أو أدخل عنوان IP والمنفذ يدوياً."
        LinkFailure.PAIRING_REFUSED -> "الاقتران مرفوض. اقبل نافذة السماح على التلفاز، أو أدخل رمز الاقتران من شاشة التصحيح اللاسلكي."
        LinkFailure.SCREEN_COVERED -> "تطبيق يغطي الشاشة أو الجهاز يرفض الخطة. أغلق التطبيق الظاهر ثم أعد المحاولة."
        LinkFailure.TOO_OLD -> "جهازك أقدم من الحد الأدنى. لا نحاول الربط على نظام أقدم من أندرويد 8."
        LinkFailure.WIRELESS_MISSING -> "تصحيح الأخطاء اللاسلكي غير ظاهر. المسار البديل: كابل USB، أو إدخال عنوان IP والمنفذ 5555 يدوياً."
        LinkFailure.GENERIC -> "توقف الربط بسبب غير مصنّف. أرسل التشخيص، أو جرّب إدخال العنوان يدوياً."
    }

    private fun englishLink(failure: LinkFailure): String = when (failure) {
        LinkFailure.SAME_WIFI -> "The phone and TV are not on the same Wi-Fi. Join the same network, then retry."
        LinkFailure.PORT_CLOSED -> "The connection port is closed. Turn on USB debugging and network debugging, or type the IP and port."
        LinkFailure.PAIRING_REFUSED -> "Pairing was refused. Accept the Allow prompt on the TV, or enter the pairing code from wireless debugging."
        LinkFailure.SCREEN_COVERED -> "An app is covering the screen, or the TV refused the plan. Close the app in front, then retry."
        LinkFailure.TOO_OLD -> "This TV is older than the minimum. We do not try to connect on Android older than 8."
        LinkFailure.WIRELESS_MISSING -> "Wireless debugging is not available. Use a USB cable, or type the IP and port 5555."
        LinkFailure.GENERIC -> "Connection stopped for an unclassified reason. Send the diagnostic, or type the address."
    }
}
