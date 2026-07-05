plugins {
    id("prismarin.java")
}

val includedProjects: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

val bundledLibraries: Configuration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    implementation(project(":api"))
    implementation(project(":shared"))
    includedProjects(project(":api"))
    includedProjects(project(":shared"))
    bundledLibraries(libs.guice)
    bundledLibraries(libs.configurate.yaml)

    compileOnly(libs.paper.api)
    compileOnly(libs.luckperms.api)
    compileOnly(libs.guice)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.plain)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.jar {
    dependsOn(includedProjects, bundledLibraries)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Multi-Release" to "true")
    }
    from(includedProjects.elements.map { elements -> elements.map { zipTree(it.asFile) } })
    from(bundledLibraries.elements.map { elements -> elements.map { zipTree(it.asFile) } }) {
        exclude(
            "META-INF/MANIFEST.MF",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "module-info.class",
            "META-INF/versions/**/module-info.class"
        )
    }
}
