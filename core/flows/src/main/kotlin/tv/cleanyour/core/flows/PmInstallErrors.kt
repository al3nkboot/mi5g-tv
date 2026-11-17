package tv.cleanyour.core.flows

/**
 * 2.4: M1 item 10 — Chromecast "paired but nothing installs" error diagnosis.
 * Maps raw PackageManager / adb install failure codes to short Gulf-neutral Arabic and English explanations,
 * keeping the raw code available for display in small text.
 */
object PmInstallErrors {

    data class Explanation(
        val code: String,
        val arabic: String,
        val english: String,
        val rawCode: String = code,
    )

    private val CODE_REGEX = Regex("(INSTALL_FAILED_[A-Z0-9_]+|INSTALL_PARSE_FAILED_[A-Z0-9_]+|INSTALL_CANCELLED_BY_USER|UNREACHABLE)")

    fun extractCode(raw: String?): String? {
        if (raw == null) return null
        if (raw.contains("unreachable", ignoreCase = true)) {
            return "UNREACHABLE"
        }
        val m = CODE_REGEX.find(raw)
        if (m != null) return m.value
        if (raw.contains("not installed for user 0", ignoreCase = true) || raw.contains("no readable apk", ignoreCase = true)) {
            return "INSTALL_FAILED_WRONG_USER"
        }
        return null
    }

    fun map(codeOrText: String?): Explanation? {
        if (codeOrText.isNullOrBlank()) return null
        val code = extractCode(codeOrText) ?: if (codeOrText.startsWith("INSTALL_") || codeOrText == "UNREACHABLE") codeOrText.trim() else return null
        return when (code) {
            "UNREACHABLE" -> Explanation(
                code = "UNREACHABLE",
                arabic = "قد يكون التلفاز مطفأً أو في وضع السكون، أو الهاتف على شبكة Wi-Fi مختلفة، أو تغيّر عنوان IP، أو أُوقف تصحيح الأخطاء. تحقق من هذه الأمور ثم أعد المحاولة.",
                english = "The TV may be off or asleep, the phone may be on a different Wi-Fi network, the TV’s IP may have changed, or debugging may be off. Check these and retry.",
                rawCode = "",
            )
            "INSTALL_FAILED_INSUFFICIENT_STORAGE" -> Explanation(
                code = code,
                arabic = "ذاكرة التلفاز ممتلئة ولا توجد مساحة كافية لتثبيت التطبيق. احذف بعض التطبيقات أو نظف الذاكرة وحاول مجددًا.",
                english = "The TV is low on storage space. Free up space and try again.",
            )
            "INSTALL_FAILED_USER_RESTRICTED", "INSTALL_CANCELLED_BY_USER" -> Explanation(
                code = code,
                arabic = "تم منع التثبيت بسبب قيود المستخدم أو حماية Play Protect على التلفاز.",
                english = "Installation was blocked by user restrictions or Play Protect on the TV.",
            )
            "INSTALL_FAILED_VERIFICATION_FAILURE" -> Explanation(
                code = code,
                arabic = "فشل التحقق من أمان التطبيق على التلفاز. تحقق من إعدادات Play Protect أو الحماية.",
                english = "Package verification failed on the TV. Check Play Protect settings.",
            )
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE" -> Explanation(
                code = code,
                arabic = "توقيع الحزمة يتعارض مع تطبيق مثبت مسبقًا. احذف الإصدار القديم من التلفاز أولًا.",
                english = "The package signature conflicts with an existing app. Uninstall the old version from the TV first.",
            )
            "INSTALL_FAILED_VERSION_DOWNGRADE" -> Explanation(
                code = code,
                arabic = "يوجد إصدار أحدث من هذا التطبيق مثبت بالفعل على التلفاز.",
                english = "A newer version of this app is already installed on the TV.",
            )
            "INSTALL_FAILED_OLDER_SDK" -> Explanation(
                code = code,
                arabic = "يتطلب هذا التطبيق إصدار أندرويد أحدث من نظام التلفاز الحالي.",
                english = "This app requires a newer version of Android than what is on the TV.",
            )
            "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE" -> Explanation(
                code = code,
                arabic = "هذا التطبيق غير متوافق مع معالج التلفاز.",
                english = "This app is not compatible with the TV's processor architecture.",
            )
            "INSTALL_FAILED_INVALID_APK", "INSTALL_PARSE_FAILED_NOT_APK" -> Explanation(
                code = code,
                arabic = "ملف التثبيت غير صالح أو تالف.",
                english = "The APK file is corrupted or invalid.",
            )
            "INSTALL_FAILED_TEST_ONLY" -> Explanation(
                code = code,
                arabic = "هذه الحزمة مخصصة للاختبار فقط ولا يمكن تثبيتها بالشكل العادي.",
                english = "This is a test-only package and cannot be installed normally.",
            )
            "INSTALL_FAILED_CONFLICTING_PROVIDER" -> Explanation(
                code = code,
                arabic = "يتعارض هذا التطبيق مع تطبيق آخر يشارك نفس موفر المحتوى على التلفاز.",
                english = "This app conflicts with another installed app sharing the same content provider.",
            )
            "INSTALL_FAILED_WRONG_USER" -> Explanation(
                code = code,
                arabic = "وصل التطبيق إلى التلفاز لكن ليس للمستخدم الرئيسي (شائع على Google TV وChromecast). أعد تشغيل التلفاز وحاول مجددًا.",
                english = "The app reached the TV but not the main user (common on Google TV / Chromecast). Reboot the TV and try again.",
                rawCode = if (codeOrText.contains("no readable apk")) "no readable apk" else "not installed for user 0",
            )
            else -> Explanation(
                code = code,
                arabic = "تعذّر اكتمال التثبيت على التلفاز.",
                english = "Installation failed on the TV.",
            )
        }
    }

    fun format(rawText: String?, isArabic: Boolean): String? {
        val exp = map(rawText) ?: return null
        val text = if (isArabic) exp.arabic else exp.english
        val raw = (extractCode(rawText) ?: exp.rawCode).trim()
        return if (raw.isNotEmpty() && raw != "UNREACHABLE") "$text\n\n[$raw]" else text
    }
}
