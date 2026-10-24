import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import tv.cleanyour.buildlogic.ExternalDep
import tv.cleanyour.buildlogic.ModuleGraph
import tv.cleanyour.buildlogic.ModuleInfo
import tv.cleanyour.buildlogic.ModuleRules
import tv.cleanyour.buildlogic.ProjectEdge

// Applied to the root project only. Registers checkModuleGraph and makes the root `check`
// depend on it and on the build-logic unit tests (which hold the planted-violation tests).
class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) { "cleanyourtv.root is for the root project" }
        target.pluginManager.apply("base")

        val check = target.tasks.register("checkModuleGraph", CheckModuleGraphTask::class.java) {
            group = "verification"
            description = "Fails when a module edge breaks the ARCHITECTURE 1.3 rules (layers, features, test scope, allowlist)."
        }

        target.gradle.projectsEvaluated {
            val graph = collect(target)
            check.configure { encodedGraph.set(ModuleRules.encode(graph)) }
        }

        target.tasks.named("check") {
            dependsOn(check)
            dependsOn(target.gradle.includedBuild("build-logic").task(":convention:test"))
        }
    }

    private val pluginIds = listOf(
        "cleanyourtv.jvm", "cleanyourtv.serialization", "cleanyourtv.android.lib", "cleanyourtv.android.compose",
        "cleanyourtv.android.feature", "cleanyourtv.android.app", "cleanyourtv.tv.app",
        "com.android.library", "com.android.application", "com.android.test",
        "org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.android",
    )

    private fun collect(root: Project): ModuleGraph {
        val modules = mutableListOf<ModuleInfo>()
        val edges = mutableListOf<ProjectEdge>()
        val externals = mutableListOf<ExternalDep>()
        for (p in root.subprojects) {
            // Container projects such as :core have no build file and no configurations of interest.
            if (!p.buildFile.exists()) continue
            modules += ModuleInfo(p.path, pluginIds.filter { p.pluginManager.hasPlugin(it) }.toSet())
            for (c in p.configurations) {
                if (!ModuleRules.isDeclarable(c.name)) continue
                for (d in c.dependencies) {
                    when (d) {
                        is ProjectDependency -> edges += ProjectEdge(p.path, d.path, c.name)
                        is ExternalModuleDependency -> externals += ExternalDep(p.path, "${d.group}:${d.name}", c.name)
                    }
                }
            }
        }
        return ModuleGraph(modules, edges, externals)
    }
}

abstract class CheckModuleGraphTask : DefaultTask() {
    @get:Input
    abstract val encodedGraph: ListProperty<String>

    @TaskAction
    fun run() {
        val graph = ModuleRules.decode(encodedGraph.get())
        val problems = ModuleRules.violations(graph)
        if (problems.isNotEmpty()) {
            throw GradleException("checkModuleGraph found ${problems.size} violation(s):\n" + problems.joinToString("\n") { "  $it" })
        }
        logger.lifecycle("checkModuleGraph: ${graph.modules.size} modules, ${graph.edges.size} project edges, ${graph.externals.size} external dependencies, no violations")
    }
}
