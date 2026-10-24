import org.gradle.api.Plugin
import org.gradle.api.Project

// kotlinx-serialization compiler plugin; the runtime library is declared by the module itself.
class SerializationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
    }
}
