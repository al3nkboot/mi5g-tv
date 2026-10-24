plugins {
    id("cleanyourtv.jvm")
    id("cleanyourtv.serialization")
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.serialization.json)
    testImplementation(project(":testing:fixtures"))
}

// The upstream launcher repository, pinned by W1 (DECISIONS-W1.md, D-W1-8).
val upstream = rootProject.layout.projectDirectory.dir("third_party/cleanyourtv")
val upstreamCommit = "9071ec8"

// D28: profiles are converted at build time from the pinned upstream examples and shipped as
// data (profiles.json, with the D26 sure sets). The converter is this module's own code, run
// from its compiled classes; profile-meta.json is passed as a file so the task does not depend
// on the resources it feeds.
val generatedProfiles = layout.buildDirectory.dir("generated/profiles")
val convertProfiles by tasks.registering(JavaExec::class) {
    description = "Converts upstream examples/*/debloat.txt into profiles.json"
    val meta = layout.projectDirectory.file("src/main/resources/tv/cleanyour/core/spec/profile-meta.json")
    // D2: upstream folders without a curated entry, skipped on purpose (ProfilesTest pins the list).
    val unconverted = layout.projectDirectory.file("upstream-feedback/unconverted.txt")
    val examples = upstream.dir("examples")
    inputs.dir(examples).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(meta).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(unconverted).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedProfiles)
    classpath(sourceSets.main.map { it.output.classesDirs }, configurations.named("runtimeClasspath"))
    mainClass.set("tv.cleanyour.core.spec.ProfileConverter")
    maxHeapSize = "256m"
    args(examples.asFile.absolutePath, meta.asFile.absolutePath, upstreamCommit, generatedProfiles.get().asFile.absolutePath, unconverted.asFile.absolutePath)
}

// The three bundled themes, from the pinned upstream themes/ folder.
val bundleThemes by tasks.registering(Sync::class) {
    from(upstream.dir("themes")) {
        include("default/config.json", "aurora/config.json", "aurora/wallpaper.jpg", "six-columns/config.json")
        into("tv/cleanyour/core/spec/themes")
    }
    into(layout.buildDirectory.dir("generated/themes"))
}

sourceSets.main {
    resources.srcDir(convertProfiles)
    resources.srcDir(bundleThemes)
}

// Differential test (F8): upstream Config.java and Festival.java at the pinned commit (v2.4.0),
// moved into their own package so they sit beside the vendored 1bbf0c9 copy (launcher 2.0) in the
// test sources.
val pinnedConfigSource by tasks.registering(Sync::class) {
    from(upstream.dir("launcher/app/src/main/java/com/gathin/tvhome")) {
        include("Config.java", "Festival.java")
        into("com/gathin/tvhome/pinned")
        filter { line: String -> if (line == "package com.gathin.tvhome;") "package com.gathin.tvhome.pinned;" else line }
    }
    into(layout.buildDirectory.dir("generated/upstream-config/java"))
}

sourceSets.test {
    java.srcDir(pinnedConfigSource)
}
