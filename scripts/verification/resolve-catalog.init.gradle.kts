// Init script for scripts/update-verification-metadata.sh only (never part of a normal build).
// Registers :resolveCatalogForVerification on the root project, which resolves every library in
// gradle/libs.versions.toml with its transitive dependencies, so the verification metadata also
// covers catalogued libraries that no module uses yet (other streams add them at merge).
// Resolution is lenient: a library whose variants need Android attributes is still downloaded
// as far as Gradle can go, and the module builds record the rest.
gradle.rootProject {
    val root = this
    tasks.register("resolveCatalogForVerification") {
        group = "verification"
        description = "Resolves every version catalog library (for --write-verification-metadata)."
        val catalog = root.extensions.getByType<VersionCatalogsExtension>().named("libs")
        val deps = catalog.libraryAliases.map { catalog.findLibrary(it).get().get() }
        val configs = deps.map { dep ->
            root.configurations.detachedConfiguration(root.dependencies.create(dep)).apply { isTransitive = true }
        }
        notCompatibleWithConfigurationCache("resolves detached configurations at execution")
        doLast {
            var files = 0
            configs.forEach { c -> files += c.incoming.artifactView { lenient(true) }.files.files.size }
            logger.lifecycle("resolveCatalogForVerification: ${deps.size} libraries, $files files")
        }
    }
}
