package tv.cleanyour.core.engine

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.ops.ConfigTransport
import tv.cleanyour.core.ops.ConfigTransportKind
import tv.cleanyour.core.probe.Prober
import tv.cleanyour.core.session.TvSession

// D15, ARCHITECTURE 3.11: what upstream's tools/theme.sh does. Start the launcher once so its
// external files directory exists, push config.json (and wallpaper.jpg, or remove a stale one),
// each push verified by the renderer's sha256 read, then force-stop and start Home. Reset removes
// both files and restarts Home. The digest read on every connect is what the drift banner compares.
class FileOverlayTransport(
    private val prober: Prober,
    private val launcher: Component = Component(PackageName("com.gathin.tvhome"), ".MainActivity"),
) : ConfigTransport {
    override val kind: ConfigTransportKind = ConfigTransportKind.FILE_OVERLAY

    override suspend fun readDigest(session: TvSession): Sha256? = prober.fileSha256(session, CONFIG)

    override fun writePlanSteps(config: BlobRef, wallpaper: BlobRef?): List<Op> {
        require(config.size in 1..MAX_CONFIG_BYTES) { "config.json must be 1 to $MAX_CONFIG_BYTES bytes" }
        require(wallpaper == null || wallpaper.size in 1..MAX_WALLPAPER_BYTES) { "wallpaper.jpg must be 1 to $MAX_WALLPAPER_BYTES bytes" }
        return listOf(
            Op.StartActivity(launcher, wait = true),
            Op.Mkdir(RemoteDir.OVERLAY),
            Op.PushAsset(config, CONFIG, config.sha256),
            if (wallpaper != null) Op.PushAsset(wallpaper, WALLPAPER, wallpaper.sha256) else Op.RemoveFile(WALLPAPER),
            Op.RestartHome(launcher),
        )
    }

    override fun resetPlanSteps(): List<Op> = listOf(Op.RemoveFile(CONFIG), Op.RemoveFile(WALLPAPER), Op.RestartHome(launcher))

    companion object {
        val CONFIG = RemotePath(RemoteDir.OVERLAY, listOf("config.json"))
        val WALLPAPER = RemotePath(RemoteDir.OVERLAY, listOf("wallpaper.jpg"))
        const val MAX_CONFIG_BYTES = 64L * 1024
        const val MAX_WALLPAPER_BYTES = 300L * 1024
    }
}
