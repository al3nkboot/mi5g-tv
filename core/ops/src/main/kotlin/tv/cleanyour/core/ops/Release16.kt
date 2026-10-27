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

