package tv.cleanyour.buildlogic

// The module graph rules of ARCHITECTURE 1.3, as data plus one pure function so they can be
// unit tested with planted violations (ModuleRulesTest) and enforced on the real build by the
// checkModuleGraph task. Layer numbers: a module may depend only on modules with a smaller number.
// Changing a layer or an allowlist here is a section 5 or ARCHITECTURE 1.3 change: it needs an RFC.

data class ModuleInfo(val path: String, val plugins: Set<String>)

data class ProjectEdge(val from: String, val to: String, val configuration: String)

data class ExternalDep(val module: String, val coordinate: String, val configuration: String)

data class ModuleGraph(
    val modules: List<ModuleInfo>,
    val edges: List<ProjectEdge>,
    val externals: List<ExternalDep>,
)

object ModuleRules {

    // Layer order, top to bottom: app, feature, ui, android, flows, engine, planner and registry
    // and backup, probe and trust and adb session, spec and ops and adb wire, model.
    // After gate R0 every shared type lives in :core:model, so the adb modules only need model.
    // RFC-W1-1 (DECISIONS-W1.md): :core:engine sits one layer above :core:planner because
    // Engine.start takes a Plan.
    val layers: Map<String, Int> = mapOf(
        ":core:model" to 0,
        ":core:spec" to 1,
        ":core:ops" to 1,
        ":adb:wire" to 1,
        ":adb:pairing" to 1,
        ":adb:session" to 2,
        ":core:probe" to 2,
        ":core:trust" to 2,
        ":testing:fake-adbd" to 2,
        ":core:planner" to 3,
        ":core:registry" to 3,
        ":core:backup" to 3,
        ":core:engine" to 4,
        ":core:flows" to 5,
        ":testing:fixtures" to 5,
        ":android:platform" to 6,
        ":android:work" to 6,
        ":e2e" to 6,
        ":ui:design" to 7,
        ":feature:connect" to 8,
        ":feature:setup" to 8,
        ":feature:manage" to 8,
        ":feature:launcher" to 8,
        ":feature:modules" to 8,
        ":feature:backup" to 8,
        ":app" to 9,
        ":benchmark" to 10,
        // RFC-CURTAIN-1: a separate TV app, bundled as a signed asset, never a Gradle dependency (rule 7).
        ":tv:curtain" to 10,
    )

    // Rule 5: these may only be reached through test configurations.
    val testOnlyModules: Set<String> = setOf(":testing:fixtures", ":testing:fake-adbd", ":e2e")

    // Rule 3: what a feature module may see.
    val featureMayUse: Set<String> = setOf(":core:flows", ":core:model", ":ui:design")

    // Rule 6: third-party dependencies allowed per module (non-test configurations).
    // "group:name" or "group:*". Kotlin's own artifacts are allowed everywhere.
    private val coreCommon = setOf(
        "org.jetbrains.kotlinx:kotlinx-coroutines-core",
        "org.jetbrains.kotlinx:kotlinx-serialization-core",
        "org.jetbrains.kotlinx:kotlinx-serialization-json",
    )
    private val androidCommon = setOf(
        "androidx.*:*",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core",
        "org.jetbrains.kotlinx:kotlinx-coroutines-android",
        "org.jetbrains.kotlinx:kotlinx-serialization-core",
        "org.jetbrains.kotlinx:kotlinx-serialization-json",
    )
    val allowedExternal: Map<String, Set<String>> = mapOf(
        ":core:model" to coreCommon,
        ":core:spec" to coreCommon,
        ":core:ops" to coreCommon,
        ":core:probe" to coreCommon,
        ":core:planner" to coreCommon,
        ":core:engine" to coreCommon,
        ":core:trust" to coreCommon + "org.bouncycastle:bcprov-jdk18on",
        ":core:registry" to coreCommon,
        ":core:backup" to coreCommon,
        ":core:flows" to coreCommon,
        ":adb:wire" to setOf("com.squareup.okio:okio", "org.jetbrains.kotlinx:kotlinx-coroutines-core"),
        ":adb:pairing" to setOf("org.jetbrains.kotlinx:kotlinx-coroutines-core"),
        ":adb:session" to setOf("org.jetbrains.kotlinx:kotlinx-coroutines-core"),
        ":testing:fixtures" to coreCommon + "junit:junit" + "org.jetbrains.kotlinx:kotlinx-coroutines-test",
        ":testing:fake-adbd" to coreCommon + "com.squareup.okio:okio",
        ":e2e" to coreCommon + "junit:junit",
        ":android:platform" to androidCommon + "org.bouncycastle:bcprov-jdk18on",
        ":android:work" to androidCommon,
        ":ui:design" to androidCommon,
        ":feature:connect" to androidCommon,
        ":feature:setup" to androidCommon,
        ":feature:manage" to androidCommon,
        ":feature:launcher" to androidCommon,
        ":feature:modules" to androidCommon,
        ":feature:backup" to androidCommon,
        ":app" to androidCommon,
        ":benchmark" to androidCommon,
        ":tv:curtain" to emptySet(),
    )

    // Test configurations: test libraries only (section 7.2 of ARCHITECTURE).
    val allowedTestExternal: Set<String> = setOf(
        "junit:junit",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core",
        "org.jetbrains.kotlinx:kotlinx-serialization-json",
        "app.cash.turbine:turbine",
        "io.kotest:*",
        "org.robolectric:*",
        "io.github.takahirom.roborazzi:*",
        "androidx.*:*",
        "com.android.tools.build:apksig",
        "org.bouncycastle:bcprov-jdk18on",
        "com.squareup.okio:okio",
    )

    fun isTestConfiguration(name: String): Boolean = name.contains("test", ignoreCase = true)

    // Declarable dependency buckets only; plugin-internal configurations are ignored.
    fun isDeclarable(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith("implementation") || n.endsWith("api") || n.endsWith("compileonly") ||
            n.endsWith("runtimeonly") || n.endsWith("compileonlyapi")
    }

    private fun matches(pattern: String, coordinate: String): Boolean {
        val (pg, pn) = pattern.split(":", limit = 2)
        val (g, n) = coordinate.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val prefix = pg.removeSuffix(".*")
        val groupOk = if (pg.endsWith(".*")) g == prefix || g.startsWith("$prefix.") else g == pg
        val nameOk = pn == "*" || pn == n
        return groupOk && nameOk
    }

    private fun isJvmCoreOrAdb(path: String) = path.startsWith(":core:") || path.startsWith(":adb:")

    // Rule 7 (RFC-CURTAIN-1): a :tv: module is an app of its own that runs on the TV. The phone app
    // takes its signed APK as an asset, so no main edge reaches it, it has no main edge to any other
    // module and no third-party dependency in a main configuration, Kotlin's own included.
    fun isTvApp(path: String) = path.startsWith(":tv:")

    fun violations(graph: ModuleGraph): List<String> {
        val out = mutableListOf<String>()
        val known = graph.modules.associateBy { it.path }

        for (m in graph.modules) {
            if (m.path !in layers) out += "${m.path}: not listed in ModuleRules.layers"
            // Rule 2: pure Kotlin core, no Android Gradle plugin.
            if (isJvmCoreOrAdb(m.path)) {
                if ("cleanyourtv.jvm" !in m.plugins) out += "${m.path}: must apply cleanyourtv.jvm (rule 2)"
                if (m.plugins.any { it.startsWith("com.android.") }) out += "${m.path}: Android Gradle plugin is forbidden here (rule 2)"
            }
        }

        for (e in graph.edges) {
            if (e.from == e.to) continue
            if (e.to !in known) {
                out += "${e.from} -> ${e.to} (${e.configuration}): unknown module"
                continue
            }
            val test = isTestConfiguration(e.configuration)
            // Rule 7
            if (!test && isTvApp(e.to)) {
                out += "${e.from} -> ${e.to} (${e.configuration}): a TV app is bundled as an asset, never a dependency (rule 7)"
                continue
            }
            if (!test && isTvApp(e.from)) {
                out += "${e.from} -> ${e.to} (${e.configuration}): a TV app depends on no other module (rule 7)"
                continue
            }
            if (test) continue // test scope may use fixtures and fakes from anywhere
            // Rule 5
            if (e.to in testOnlyModules && e.from !in testOnlyModules) {
                out += "${e.from} -> ${e.to} (${e.configuration}): test modules only in test configurations (rule 5)"
                continue
            }
            // Rule 3
            if (e.from.startsWith(":feature:") && e.to !in featureMayUse) {
                out += "${e.from} -> ${e.to} (${e.configuration}): a feature sees only ${featureMayUse.sorted()} (rule 3)"
                continue
            }
            // Rule 1
            val lf = layers[e.from]
            val lt = layers[e.to]
            if (lf != null && lt != null && lt >= lf) {
                out += "${e.from} (layer $lf) -> ${e.to} (layer $lt) (${e.configuration}): may depend only on lower layers (rule 1)"
            }
        }

        // Rule 6
        for (x in graph.externals) {
            if (isTvApp(x.module) && !isTestConfiguration(x.configuration)) {
                out += "${x.module}: ${x.coordinate} (${x.configuration}): a TV app has no third-party dependency (rule 7)"
                continue
            }
            if (x.coordinate.startsWith("org.jetbrains.kotlin:")) continue
            val allowed = if (isTestConfiguration(x.configuration)) allowedTestExternal + (allowedExternal[x.module] ?: emptySet())
            else allowedExternal[x.module] ?: emptySet()
            if (allowed.none { matches(it, x.coordinate) }) {
                out += "${x.module}: ${x.coordinate} (${x.configuration}) is not in the build-logic allowlist (rule 6)"
            }
        }

        // Cycles among non-test edges (rule 1 already implies none; kept as a second net).
        val adj = graph.edges.filter { !isTestConfiguration(it.configuration) && it.from != it.to }
            .groupBy({ it.from }, { it.to })
        val state = mutableMapOf<String, Int>()
        fun dfs(n: String, stack: List<String>) {
            when (state[n]) {
                1 -> { out += "cycle: " + (stack.dropWhile { it != n } + n).joinToString(" -> "); return }
                2 -> return
            }
            state[n] = 1
            for (m in adj[n].orEmpty()) dfs(m, stack + n)
            state[n] = 2
        }
        for (n in adj.keys.sorted()) dfs(n, emptyList())

        return out.distinct()
    }

    // Line encoding used to carry the graph into the task (configuration cache friendly).
    fun encode(graph: ModuleGraph): List<String> =
        graph.modules.map { "M|${it.path}|${it.plugins.sorted().joinToString(",")}" } +
            graph.edges.map { "P|${it.from}|${it.to}|${it.configuration}" } +
            graph.externals.map { "X|${it.module}|${it.coordinate}|${it.configuration}" }

    fun decode(lines: List<String>): ModuleGraph {
        val modules = mutableListOf<ModuleInfo>()
        val edges = mutableListOf<ProjectEdge>()
        val externals = mutableListOf<ExternalDep>()
        for (l in lines) {
            val p = l.split("|")
            when (p[0]) {
                "M" -> modules += ModuleInfo(p[1], p[2].split(",").filter { it.isNotEmpty() }.toSet())
                "P" -> edges += ProjectEdge(p[1], p[2], p[3])
                "X" -> externals += ExternalDep(p[1], p[2], p[3])
            }
        }
        return ModuleGraph(modules, edges, externals)
    }
}
