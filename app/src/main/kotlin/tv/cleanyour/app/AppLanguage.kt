package tv.cleanyour.app

// 1.6: the in-app language choices. Arabic is the default.
object AppLanguage {
    const val ARABIC = "ar"
    const val ENGLISH = "en"
    const val SYSTEM = "system"
    val ALL = listOf(ARABIC, ENGLISH, SYSTEM)
    fun normalize(raw: String?): String = raw?.takeIf { it in ALL } ?: ARABIC
}
