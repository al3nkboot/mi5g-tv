package tv.cleanyour.core.flows

// Mi5G 2.0 M3 (item 27): what the phone sends to مواقيتي on the TV as settings.json.
// The TV module (tv/prayer Settings.java) validates every field again and keeps defaults for anything off.

data class PrayerCity(val id: String, val ar: String, val lat: Double, val lon: Double, val zone: String, val countryAr: String)

enum class PrayerMethod(val ar: String) {
    OMAN("وزارة الأوقاف - عُمان"), UMM_AL_QURA("أم القرى"), MWL("رابطة العالم الإسلامي"), DUBAI("دبي"),
    QATAR("قطر"), KUWAIT("الكويت"), EGYPTIAN("الهيئة المصرية"), KARACHI("كراتشي"), ISNA("أمريكا الشمالية"),
}

enum class OverlayCorner(val key: String, val ar: String) {
    TOP_END("top_end", "أعلى اليسار"), TOP_START("top_start", "أعلى اليمين"),
    BOTTOM_END("bottom_end", "أسفل اليسار"), BOTTOM_START("bottom_start", "أسفل اليمين"),
}

data class PrayerTvSettings(
    val city: PrayerCity = PrayerCities.ALL.first(),
    val method: PrayerMethod = PrayerMethod.OMAN,
    val hanafi: Boolean = false,
    val forcedAdhan: Boolean = true,
    val forcedVolume: Int = 70,
    val preMinutes: Int = 10,
    val overlay: Boolean = false,
    val corner: OverlayCorner = OverlayCorner.TOP_END,
    val overlaySize: Int = 100,
    val overlayOpacity: Int = 85,
    val kahf: Boolean = true,
) {
    fun toJson(): String = buildString {
        append("{\"city\":{\"id\":").append(q(city.id)).append(",\"ar\":").append(q(city.ar))
        append(",\"lat\":").append(city.lat).append(",\"lon\":").append(city.lon).append(",\"zone\":").append(q(city.zone)).append("},")
        append("\"hanafi\":").append(hanafi).append(',')
        append("\"method\":").append(q(method.name)).append(',')
        append("\"forced_adhan\":").append(forcedAdhan).append(",\"forced_volume\":").append(forcedVolume.coerceIn(10, 100)).append(',')
        append("\"pre_minutes\":").append(preMinutes.coerceIn(0, 60)).append(',')
        append("\"overlay\":{\"on\":").append(overlay).append(",\"corner\":").append(q(corner.key))
        append(",\"size\":").append(overlaySize.coerceIn(60, 160)).append(",\"opacity\":").append(overlayOpacity.coerceIn(30, 100)).append("},")
        append("\"kahf\":{\"on\":").append(kahf).append(",\"hour\":10}}")
    }

    companion object {
        val PRE_MINUTES = listOf(0, 5, 10, 15, 20, 30)
        val SIZES = listOf(80, 100, 130, 160)
        val OPACITIES = listOf(50, 70, 85, 100)
        val VOLUMES = listOf(40, 55, 70, 85, 100)
        private fun q(s: String): String = buildString {
            append('"')
            for (c in s) when {
                c == '"' || c == '\\' -> append('\\').append(c)
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
            append('"')
        }
    }
}

/** Cycle to the next value in a list (wraps; an unknown value goes to the first). */
fun <T> List<T>.after(current: T): T = this[(indexOf(current) + 1).mod(size)]

// The same places as tv/prayer/src/main/assets/cities.json (GeoNames, CC BY 4.0); PrayerTvSettingsTest keeps them in step.
object PrayerCities {
    val ALL: List<PrayerCity> = listOf(
        PrayerCity("muscat", "مسقط", 23.58413, 58.40778, "Asia/Muscat", "عُمان"),
        PrayerCity("muttrah", "مطرح", 23.61775, 58.55717, "Asia/Muscat", "عُمان"),
        PrayerCity("bawshar", "بوشر", 23.57769, 58.39982, "Asia/Muscat", "عُمان"),
        PrayerCity("seeb", "السيب", 23.67027, 58.18911, "Asia/Muscat", "عُمان"),
        PrayerCity("amerat", "العامرات", 23.52239, 58.49866, "Asia/Muscat", "عُمان"),
        PrayerCity("quriyat", "قريات", 23.26298, 58.92014, "Asia/Muscat", "عُمان"),
        PrayerCity("buraimi", "البريمي", 24.25088, 55.79312, "Asia/Muscat", "عُمان"),
        PrayerCity("mahdah", "محضة", 24.40052, 55.96718, "Asia/Muscat", "عُمان"),
        PrayerCity("sunaynah", "السنينة", 23.6065, 55.95448, "Asia/Muscat", "عُمان"),
        PrayerCity("sohar", "صحار", 24.34745, 56.70937, "Asia/Muscat", "عُمان"),
        PrayerCity("shinas", "شناص", 24.7426, 56.46698, "Asia/Muscat", "عُمان"),
        PrayerCity("liwa", "لوى", 24.53077, 56.563, "Asia/Muscat", "عُمان"),
        PrayerCity("saham", "صحم", 24.17222, 56.88861, "Asia/Muscat", "عُمان"),
        PrayerCity("khaburah", "الخابورة", 23.97144, 57.09313, "Asia/Muscat", "عُمان"),
        PrayerCity("suwayq", "السويق", 23.84944, 57.43861, "Asia/Muscat", "عُمان"),
        PrayerCity("barka", "بركاء", 23.67872, 57.88605, "Asia/Muscat", "عُمان"),
        PrayerCity("rustaq", "الرستاق", 23.39083, 57.42444, "Asia/Muscat", "عُمان"),
        PrayerCity("musannah", "المصنعة", 23.77997, 57.64014, "Asia/Muscat", "عُمان"),
        PrayerCity("nakhal", "نخل", 23.39849, 57.8218, "Asia/Muscat", "عُمان"),
        PrayerCity("maawil", "وادي المعاول", 23.46423, 57.82177, "Asia/Muscat", "عُمان"),
        PrayerCity("awabi", "العوابي", 23.30611, 57.53111, "Asia/Muscat", "عُمان"),
        PrayerCity("nizwa", "نزوى", 22.93333, 57.53333, "Asia/Muscat", "عُمان"),
        PrayerCity("bahla", "بهلا", 22.97886, 57.3047, "Asia/Muscat", "عُمان"),
        PrayerCity("adam", "أدم", 22.37934, 57.52718, "Asia/Muscat", "عُمان"),
        PrayerCity("izki", "إزكي", 22.93333, 57.76667, "Asia/Muscat", "عُمان"),
        PrayerCity("samail", "سمائل", 23.29838, 57.9531, "Asia/Muscat", "عُمان"),
        PrayerCity("bidbid", "بدبد", 23.40787, 58.1283, "Asia/Muscat", "عُمان"),
        PrayerCity("manah", "منح", 22.79203, 57.60593, "Asia/Muscat", "عُمان"),
        PrayerCity("hamra", "الحمراء", 23.11519, 57.29143, "Asia/Muscat", "عُمان"),
        PrayerCity("ibri", "عبري", 23.22573, 56.51572, "Asia/Muscat", "عُمان"),
        PrayerCity("yanqul", "ينقل", 23.58645, 56.53969, "Asia/Muscat", "عُمان"),
        PrayerCity("dhank", "ضنك", 23.55054, 56.25725, "Asia/Muscat", "عُمان"),
        PrayerCity("sur", "صور", 22.56667, 59.52889, "Asia/Muscat", "عُمان"),
        PrayerCity("jbbali", "جعلان بني بو علي", 22.01559, 59.33381, "Asia/Muscat", "عُمان"),
        PrayerCity("jbbhasan", "جعلان بني بو حسن", 22.08167, 59.28565, "Asia/Muscat", "عُمان"),
        PrayerCity("kamil", "الكامل والوافي", 22.20772, 59.20643, "Asia/Muscat", "عُمان"),
        PrayerCity("masirah", "مصيرة", 20.65063, 58.87277, "Asia/Muscat", "عُمان"),
        PrayerCity("ibra", "إبراء", 22.69057, 58.53337, "Asia/Muscat", "عُمان"),
        PrayerCity("mudaybi", "المضيبي", 22.57032, 58.12115, "Asia/Muscat", "عُمان"),
        PrayerCity("bidiyah", "بدية", 22.4405, 58.79928, "Asia/Muscat", "عُمان"),
        PrayerCity("qabil", "القابل", 22.571, 58.69472, "Asia/Muscat", "عُمان"),
        PrayerCity("wbk", "وادي بني خالد", 22.58001, 59.09329, "Asia/Muscat", "عُمان"),
        PrayerCity("dima", "دماء والطائيين", 23.033, 58.689, "Asia/Muscat", "عُمان"),
        PrayerCity("salalah", "صلالة", 17.01505, 54.09237, "Asia/Muscat", "عُمان"),
        PrayerCity("taqah", "طاقة", 17.04826, 54.39063, "Asia/Muscat", "عُمان"),
        PrayerCity("mirbat", "مرباط", 16.99638, 54.69063, "Asia/Muscat", "عُمان"),
        PrayerCity("thumrait", "ثمريت", 17.67, 54.03333, "Asia/Muscat", "عُمان"),
        PrayerCity("sadah", "سدح", 17.05, 55.06667, "Asia/Muscat", "عُمان"),
        PrayerCity("rakhyut", "رخيوت", 16.74768, 53.42189, "Asia/Muscat", "عُمان"),
        PrayerCity("dhalkut", "ضلكوت", 16.70403, 53.24219, "Asia/Muscat", "عُمان"),
        PrayerCity("muqshin", "مقشن", 19.49805, 54.84341, "Asia/Muscat", "عُمان"),
        PrayerCity("shalim", "شليم وجزر الحلانيات", 18.10252, 55.65333, "Asia/Muscat", "عُمان"),
        PrayerCity("khasab", "خصب", 26.17993, 56.24774, "Asia/Muscat", "عُمان"),
        PrayerCity("dibba", "دبا", 25.63643, 56.26597, "Asia/Muscat", "عُمان"),
        PrayerCity("bukha", "بخا", 26.14107, 56.15202, "Asia/Muscat", "عُمان"),
        PrayerCity("madha", "مدحاء", 25.28357, 56.33196, "Asia/Muscat", "عُمان"),
        PrayerCity("haima", "هيماء", 19.95931, 56.27575, "Asia/Muscat", "عُمان"),
        PrayerCity("duqm", "الدقم", 19.66207, 57.70367, "Asia/Muscat", "عُمان"),
        PrayerCity("mahout", "محوت", 20.76133, 58.28952, "Asia/Muscat", "عُمان"),
        PrayerCity("jazer", "الجازر", 18.545, 56.36, "Asia/Muscat", "عُمان"),
        PrayerCity("abudhabi", "أبوظبي", 24.4539, 54.3773, "Asia/Dubai", "الإمارات"),
        PrayerCity("dubai", "دبي", 25.2048, 55.2708, "Asia/Dubai", "الإمارات"),
        PrayerCity("sharjah", "الشارقة", 25.3463, 55.4209, "Asia/Dubai", "الإمارات"),
        PrayerCity("alain", "العين", 24.2075, 55.7447, "Asia/Dubai", "الإمارات"),
        PrayerCity("ajman", "عجمان", 25.4052, 55.5136, "Asia/Dubai", "الإمارات"),
        PrayerCity("rak", "رأس الخيمة", 25.8007, 55.9762, "Asia/Dubai", "الإمارات"),
        PrayerCity("fujairah", "الفجيرة", 25.1288, 56.3265, "Asia/Dubai", "الإمارات"),
        PrayerCity("makkah", "مكة المكرمة", 21.4225, 39.8262, "Asia/Riyadh", "السعودية"),
        PrayerCity("madinah", "المدينة المنورة", 24.4672, 39.6024, "Asia/Riyadh", "السعودية"),
        PrayerCity("riyadh", "الرياض", 24.7136, 46.6753, "Asia/Riyadh", "السعودية"),
        PrayerCity("jeddah", "جدة", 21.4858, 39.1925, "Asia/Riyadh", "السعودية"),
        PrayerCity("dammam", "الدمام", 26.4207, 50.0888, "Asia/Riyadh", "السعودية"),
        PrayerCity("doha", "الدوحة", 25.2854, 51.531, "Asia/Qatar", "قطر"),
        PrayerCity("kuwait", "الكويت", 29.3759, 47.9774, "Asia/Kuwait", "الكويت"),
        PrayerCity("manama", "المنامة", 26.2285, 50.586, "Asia/Bahrain", "البحرين"),
        PrayerCity("sanaa", "صنعاء", 15.3694, 44.191, "Asia/Aden", "اليمن"),
        PrayerCity("aden", "عدن", 12.7855, 45.0187, "Asia/Aden", "اليمن"),
        PrayerCity("baghdad", "بغداد", 33.3152, 44.3661, "Asia/Baghdad", "العراق"),
        PrayerCity("amman", "عمّان", 31.9454, 35.9284, "Asia/Amman", "الأردن"),
        PrayerCity("quds", "القدس", 31.7683, 35.2137, "Asia/Jerusalem", "فلسطين"),
        PrayerCity("damascus", "دمشق", 33.5138, 36.2765, "Asia/Damascus", "سوريا"),
        PrayerCity("beirut", "بيروت", 33.8938, 35.5018, "Asia/Beirut", "لبنان"),
        PrayerCity("cairo", "القاهرة", 30.0444, 31.2357, "Africa/Cairo", "مصر"),
        PrayerCity("khartoum", "الخرطوم", 15.5007, 32.5599, "Africa/Khartoum", "السودان"),
        PrayerCity("tripoli", "طرابلس", 32.8872, 13.1913, "Africa/Tripoli", "ليبيا"),
        PrayerCity("tunis", "تونس", 36.8065, 10.1815, "Africa/Tunis", "تونس"),
        PrayerCity("algiers", "الجزائر", 36.7538, 3.0588, "Africa/Algiers", "الجزائر"),
        PrayerCity("rabat", "الرباط", 34.0209, -6.8416, "Africa/Casablanca", "المغرب"),
        PrayerCity("nouakchott", "نواكشوط", 18.0735, -15.9582, "Africa/Nouakchott", "موريتانيا"),
    )
}
