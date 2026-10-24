package tv.cleanyour.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Planted violations: each test adds one bad edge to a clean graph and expects exactly the rule
// it breaks. The real build is checked by the checkModuleGraph task with the same function.
class ModuleRulesTest {

    private val jvm = setOf("cleanyourtv.jvm", "org.jetbrains.kotlin.jvm")
    private val lib = setOf("cleanyourtv.android.lib", "com.android.library")
    private val feature = lib + "cleanyourtv.android.feature"

    private fun clean(): ModuleGraph = ModuleGraph(
        modules = listOf(
            ModuleInfo(":core:model", jvm),
            ModuleInfo(":core:ops", jvm),
            ModuleInfo(":core:engine", jvm),
            ModuleInfo(":core:flows", jvm),
            ModuleInfo(":adb:session", jvm),
            ModuleInfo(":testing:fixtures", jvm),
            ModuleInfo(":ui:design", lib),
            ModuleInfo(":feature:setup", feature),
            ModuleInfo(":app", setOf("cleanyourtv.android.app", "com.android.application")),
        ),
        edges = listOf(
            ProjectEdge(":core:ops", ":core:model", "api"),
            ProjectEdge(":core:engine", ":core:ops", "api"),
            ProjectEdge(":core:flows", ":core:engine", "api"),
            ProjectEdge(":feature:setup", ":core:flows", "implementation"),
            ProjectEdge(":feature:setup", ":ui:design", "implementation"),
            ProjectEdge(":app", ":feature:setup", "implementation"),
            ProjectEdge(":core:engine", ":testing:fixtures", "testImplementation"),
        ),
        externals = listOf(
            ExternalDep(":core:model", "org.jetbrains.kotlinx:kotlinx-coroutines-core", "api"),
            ExternalDep(":ui:design", "androidx.compose:compose-bom", "implementation"),
            ExternalDep(":core:engine", "junit:junit", "testImplementation"),
            ExternalDep(":core:engine", "org.jetbrains.kotlin:kotlin-test-junit", "testImplementation"),
        ),
    )

    private fun ModuleGraph.plus(edge: ProjectEdge) = copy(edges = edges + edge)

    @Test fun cleanGraphHasNoViolations() {
        assertEquals(emptyList<String>(), ModuleRules.violations(clean()))
    }

    @Test fun upwardEdgeFails() {
        val v = ModuleRules.violations(clean().plus(ProjectEdge(":core:model", ":core:engine", "implementation")))
        assertTrue(v.toString(), v.any { "rule 1" in it && it.startsWith(":core:model") })
        assertTrue(v.toString(), v.any { it.startsWith("cycle:") })
    }

    @Test fun sameLayerEdgeFails() {
        val g = clean().copy(modules = clean().modules + ModuleInfo(":core:spec", jvm))
            .plus(ProjectEdge(":core:ops", ":core:spec", "implementation"))
        assertTrue(ModuleRules.violations(g).single().contains("rule 1"))
    }

    @Test fun featureReachingTransportFails() {
        val v = ModuleRules.violations(clean().plus(ProjectEdge(":feature:setup", ":adb:session", "implementation")))
        assertTrue(v.toString(), v.single().contains("rule 3"))
    }

    @Test fun testingModuleInMainScopeFails() {
        val v = ModuleRules.violations(clean().plus(ProjectEdge(":core:flows", ":testing:fixtures", "implementation")))
        assertTrue(v.toString(), v.single().contains("rule 5"))
    }

    @Test fun androidPluginInCoreFails() {
        val g = clean().copy(modules = clean().modules.map {
            if (it.path == ":core:ops") ModuleInfo(":core:ops", lib) else it
        })
        val v = ModuleRules.violations(g)
        assertTrue(v.toString(), v.any { "must apply cleanyourtv.jvm" in it })
        assertTrue(v.toString(), v.any { "forbidden" in it })
    }

    @Test fun unlistedThirdPartyFails() {
        val g = clean().copy(externals = clean().externals + ExternalDep(":core:ops", "com.squareup.okhttp3:okhttp", "implementation"))
        assertTrue(ModuleRules.violations(g).single().contains("rule 6"))
    }

    @Test fun unknownModuleFails() {
        val g = clean().copy(modules = clean().modules + ModuleInfo(":core:extra", jvm))
        assertTrue(ModuleRules.violations(g).single().contains("not listed"))
    }

    // Rule 7 (RFC-CURTAIN-1): the TV curtain is reached by no main edge, reaches nothing, and has no
    // third-party dependency in main, not even Kotlin's own; its tests may use the test libraries.
    @Test fun tvAppIsAnIsland() {
        val tv = clean().copy(modules = clean().modules + ModuleInfo(":tv:curtain", setOf("com.android.application")))
        assertEquals(emptyList<String>(), ModuleRules.violations(tv.copy(externals = tv.externals + ExternalDep(":tv:curtain", "junit:junit", "testImplementation"))))
        val into = ModuleRules.violations(tv.plus(ProjectEdge(":app", ":tv:curtain", "implementation")))
        assertTrue(into.toString(), into.single().contains("rule 7"))
        val out = ModuleRules.violations(tv.plus(ProjectEdge(":tv:curtain", ":core:model", "implementation")))
        assertTrue(out.toString(), out.single().contains("rule 7"))
        val kotlin = ModuleRules.violations(tv.copy(externals = tv.externals + ExternalDep(":tv:curtain", "org.jetbrains.kotlin:kotlin-stdlib", "implementation")))
        assertTrue(kotlin.toString(), kotlin.single().contains("rule 7"))
        val androidx = ModuleRules.violations(tv.copy(externals = tv.externals + ExternalDep(":tv:curtain", "androidx.core:core", "implementation")))
        assertTrue(androidx.toString(), androidx.single().contains("rule 7"))
        // A test configuration may still reach it (an e2e test reading its sources, say).
        assertEquals(emptyList<String>(), ModuleRules.violations(tv.plus(ProjectEdge(":app", ":tv:curtain", "testImplementation"))))
    }

    @Test fun encodeDecodeRoundTrip() {
        assertEquals(clean(), ModuleRules.decode(ModuleRules.encode(clean())).let { d ->
            d.copy(modules = d.modules.map { m -> m.copy(plugins = m.plugins.toSet()) })
        })
    }
}
