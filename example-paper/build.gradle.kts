plugins {
    id("prismarin.java")
}

dependencies {
    compileOnly(project(":api"))
    compileOnly(libs.paper.api)
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}
