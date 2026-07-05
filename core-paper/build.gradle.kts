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
    dependsOn(includedProjects)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(includedProjects.elements.map { elements -> elements.map { zipTree(it.asFile) } })
}
