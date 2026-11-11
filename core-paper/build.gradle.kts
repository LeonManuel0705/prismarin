plugins {
    id("prismarin.java")
}

val includedProjects: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(project(":api"))
    implementation(project(":shared"))
    includedProjects(project(":api"))
    includedProjects(project(":shared"))

    compileOnly(libs.paper.api)
    compileOnly(libs.luckperms.api)
    compileOnly(libs.vaultunlocked.api) {
        isTransitive = false
    }
    compileOnly(libs.placeholderapi) {
        isTransitive = false
    }
    compileOnly(libs.guice)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.plain)
    testImplementation(libs.gson)
}

val pluginLibraries = mapOf(
    "guice" to libs.versions.guice.get(),
    "configurate" to libs.versions.configurate.get(),
    "mongodb" to libs.versions.mongodb.get(),
    "lettuce" to libs.versions.lettuce.get(),
    "hikaricp" to libs.versions.hikaricp.get(),
    "mariadb" to libs.versions.mariadb.get(),
    "sqlite" to libs.versions.sqlite.get()
)

tasks.processResources {
    inputs.property("version", version)
    inputs.properties(pluginLibraries)
    filesMatching("plugin.yml") {
        expand(pluginLibraries + ("version" to version))
    }
}

tasks.jar {
    dependsOn(includedProjects)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Multi-Release" to "true")
    }
    from(includedProjects.elements.map { elements -> elements.map { zipTree(it.asFile) } })
}
