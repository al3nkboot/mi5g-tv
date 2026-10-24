import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import tv.cleanyour.buildlogic.libs
import tv.cleanyour.buildlogic.repoRootProperty

// Pure Kotlin JVM module: no android.jar on the classpath (ARCHITECTURE D5, rule 2).
class JvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(JDK)
            // Explicit API mode is left off: section 5 is copied verbatim without visibility
            // modifiers (DECISIONS-W1.md, D-W1-6).
        }
        dependencies {
            add("testImplementation", libs.findLibrary("junit4").get())
            add("testImplementation", libs.findLibrary("kotlin-test-junit").get())
        }
        tasks.withType<Test>().configureEach {
            useJUnit()
            systemProperty(repoRootProperty, rootDir.absolutePath)
            // A hung test fails after 5 minutes instead of holding the shared forge build lock.
            timeout.set(java.time.Duration.ofMinutes(5))
            testLogging {
                events("failed")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }

    companion object {
        const val JDK = 21
    }
}
