import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import tv.cleanyour.buildlogic.libs

// Screenshot tests on the JVM (ARCHITECTURE 6.5): Roborazzi on Robolectric with native graphics,
// Compose UI test and the ATF accessibility checks. Apply after cleanyourtv.android.compose.
// Goldens live in src/test/screenshots so verifyRoborazziDebug can compare them in CI.
class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
        extensions.configure<LibraryExtension> {
            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                }
            }
        }
        extensions.configure<io.github.takahirom.roborazzi.RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }
        dependencies {
            val bom = libs.findLibrary("androidx-compose-bom").get()
            add("testImplementation", platform(bom))
            add("testImplementation", libs.findLibrary("robolectric").get())
            add("testImplementation", libs.findLibrary("roborazzi").get())
            add("testImplementation", libs.findLibrary("roborazzi-compose").get())
            add("testImplementation", libs.findLibrary("roborazzi-junit-rule").get())
            add("testImplementation", libs.findLibrary("roborazzi-accessibility-check").get())
            add("testImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
            add("testImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
        }
        tasks.withType<Test>().configureEach {
            // forge builds run in a 3 GB container: one fork, a bounded heap.
            maxParallelForks = 1
            maxHeapSize = "1280m"
            systemProperty("robolectric.graphicsMode", "NATIVE")
            systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStandardStreams = false
            }
        }
    }
}
