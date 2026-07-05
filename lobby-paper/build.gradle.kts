plugins {
    id("prismarin.java")
}

dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":shared"))
    compileOnly(project(":core-paper"))

    compileOnly(libs.paper.api)
    compileOnly(libs.guice)
    compileOnly(libs.configurate.yaml)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.configurate.yaml)
    testImplementation(project(":api"))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}
