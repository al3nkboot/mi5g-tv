package tv.cleanyour.buildlogic

import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

// The APK size gate. 8 MB hard (ARCHITECTURE D14). The limit can be lowered for a proof run with
// -Pcleanyourtv.apkSizeLimitBytes=N; raising it needs an RFC.
abstract class ApkSizeCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkFolder: DirectoryProperty

    @get:Internal
    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    @get:Input
    abstract val limitBytes: Property<Long>

    @get:Input
    abstract val variantName: Property<String>

    @TaskAction
    fun check() {
        val built = builtArtifactsLoader.get().load(apkFolder.get())
            ?: throw GradleException("APK size gate: no APK found for ${variantName.get()}")
        val limit = limitBytes.get()
        for (element in built.elements) {
            val apk = File(element.outputFile)
            val size = apk.length()
            val line = "APK size gate (${variantName.get()}): ${apk.name} is $size bytes, limit $limit bytes"
            if (size > limit) throw GradleException("$line: TOO LARGE")
            logger.lifecycle("$line: ok")
        }
    }

    companion object {
        const val DEFAULT_LIMIT_BYTES: Long = 8L * 1024 * 1024
        // RFC-CURTAIN-1: the TV curtain, budget 200 KB, gate 256 KB.
        const val TV_APP_LIMIT_BYTES: Long = 256L * 1024
    }
}
