// FROZEN INTERFACE (PRODUCT-SPEC section 5.2, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W1 (vocabulary; W3 renders it).

package tv.cleanyour.core.model

// R0: 1 the vocabulary moved here from :core:ops: :adb:session (RemotePath, BlobRef), :core:probe (ProbeKind),
// :core:planner and :core:backup (Op, Prior, ChangeRecord) and the module index (postInstall) all name these types

enum class RemoteDir(val path: String) { TMP("/data/local/tmp/cleanyourtv"), OVERLAY("/sdcard/Android/data/com.gathin.tvhome/files"), RESTORE("/sdcard/cleanyourtv") }
data class RemotePath(val dir: RemoteDir, val segments: List<String>) {
    init { require(segments.size in 1..3 && segments.all { FILE_NAME_RE.matches(it) && !it.contains("..") }) }   // R0: 3
    val absolute: String get() = dir.path + "/" + segments.joinToString("/") }
data class BlobRef(val sha256: Sha256, val size: Long, val path: String)   // a file in the BlobStore or a bundled asset on the phone, never a TV path
enum class ProbeKind { PROPS, PACKAGES, DISABLED, HOME, HOME_FOCUS, TIF_INPUTS, CEC, SETTINGS, IME, SCREENSAVER, SCREENCAP,
    SHA256_OF_FILE, SHA256_OF_INSTALLED, PACKAGE_DUMP, PERSISTENT, DF_DATA, WATCHDOG_ALIVE, BOOT_ID, BOOT_COMPLETED, LAUNCHER_DIR }
// R0: 3 the one argument a read takes is typed; the renderer refuses a kind and argument that do not pair
// (SETTINGS takes Ns; SHA256_OF_FILE takes Path; SHA256_OF_INSTALLED and PACKAGE_DUMP take Pkg; WATCHDOG_ALIVE takes Token; the rest take none)
sealed interface ReadArg { data class Pkg(val pkg: PackageName) : ReadArg; data class Path(val path: RemotePath) : ReadArg
    data class Token(val token: RunToken) : ReadArg; data class Ns(val ns: SettingsNs) : ReadArg }
enum class InstallFlag { REPLACE }      // renders -r; no other flag exists
// RFC-CURTAIN-1: the stages of the TV curtain and the fixed group vocabulary its words come from.
// No free text reaches the TV; the curtain app carries its own strings for every value here.
enum class CurtainStage { SCAN, RESTORE_SAVED, OFF, GROUP_DONE, INSTALL, INSTALLED, FINALE }
enum class CurtainGroup { NONE, ADVERTS, TRACKING, NOBODY_ASKED, BRAND_EXTRAS, SHOP_DEMO, OTHER }
data class Prior(val packageState: EnabledState? = null, val settingValue: String? = null, val home: Component? = null,
    val overlay: ByteArray? = null, val present: Boolean? = null)

sealed interface Op {
    data class Disable(val pkg: PackageName) : Op
    data class Enable(val pkg: PackageName) : Op
    data class DefaultState(val pkg: PackageName) : Op
    data class Install(val apk: RemotePath, val sha256: Sha256, val pkg: PackageName, val versionCode: Long, val signer: Sha256, val flags: Set<InstallFlag>) : Op   // apk.dir must be TMP
    data class InstallStream(val blob: BlobRef, val sha256: Sha256, val pkg: PackageName, val versionCode: Long, val signer: Sha256) : Op
    data class Uninstall(val pkg: PackageName) : Op
    data class SettingsPut(val key: SettingKey, val value: String?) : Op   // value matches key.valueShape; null renders settings delete
    data class SetHome(val component: Component) : Op
    data class VerifyHome(val expected: Component) : Op
    data class ArmHomeWatchdog(val stock: Component, val seconds: Int, val token: RunToken) : Op   // seconds 30 to 300
    data class ConfirmHome(val token: RunToken) : Op
    data class KillWatchdog(val token: RunToken) : Op
    data object PressHome : Op
    data class Mkdir(val dir: RemoteDir) : Op
    data class PushAsset(val src: BlobRef, val dest: RemotePath, val sha256: Sha256) : Op
    data class RemoveFile(val path: RemotePath) : Op
    data class RestartHome(val component: Component) : Op          // am force-stop + am start, two lines, two steps
    data class StartActivity(val component: Component, val wait: Boolean) : Op
    data class Read(val kind: ProbeKind, val arg: ReadArg? = null) : Op   // R0: 3
    data class Settle(val seconds: Int) : Op                        // phone-side sleep, renders no line; 1 to 60
    data object Reboot : Op
    data object DebugOff : Op
    // RFC-CURTAIN-1: one am broadcast to the curtain's receiver. count and total are the switch-offs
    // over the whole plan, groupCount and groupTotal those of `group`; every number is 0 to 999.
    data class CurtainCue(val receiver: Component, val token: RunToken, val stage: CurtainStage, val count: Int, val total: Int,
        val group: CurtainGroup = CurtainGroup.NONE, val groupCount: Int = 0, val groupTotal: Int = 0) : Op }

data class ChangeRecord(val planId: PlanId, val step: Int, val op: Op, val prior: Prior?, val atMs: Long)   // R0: 1 derived from journals, for undo and restore files
