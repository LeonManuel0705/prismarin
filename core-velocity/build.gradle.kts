import java.security.MessageDigest

plugins {
    id("prismarin.java")
    id("com.gradleup.shadow") version "9.4.3"
}

dependencies {
    implementation(project(":api"))
    implementation(project(":shared"))

    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.velocity.api)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.plain)
}

val sqliteLibraries by configurations.creating
val mongoLibraries by configurations.creating

configurations.named { it == "sqliteLibraries" || it == "mongoLibraries" }.configureEach {
    exclude(group = "org.slf4j")
}

dependencies {
    sqliteLibraries(libs.sqlite.jdbc)
    mongoLibraries(libs.mongodb.driver.sync)
}

val generateLibraryIndex by tasks.registering {
    val sqlite = sqliteLibraries.incoming.artifacts.resolvedArtifacts
    val mongo = mongoLibraries.incoming.artifacts.resolvedArtifacts
    val output = layout.buildDirectory.dir("generated/resources/libraries")
    inputs.files(sqliteLibraries, mongoLibraries)
    outputs.dir(output)
    doLast {
        val lines = mutableListOf<String>()
        fun add(mode: String, artifacts: Set<ResolvedArtifactResult>) {
            artifacts.forEach { artifact ->
                val id = artifact.id.componentIdentifier as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
                    ?: return@forEach
                val digest = MessageDigest.getInstance("SHA-256").digest(artifact.file.readBytes())
                val sha = digest.joinToString("") { "%02x".format(it) }
                lines += "$mode ${id.group}:${id.module}:${id.version} $sha"
            }
        }
        add("SQLITE", sqlite.get())
        add("MONGO", mongo.get())
        val file = output.get().file("prismarin-libraries.txt").asFile
        file.parentFile.mkdirs()
        file.writeText(lines.sorted().joinToString("\n") + "\n")
    }
}

sourceSets.main {
    resources.srcDir(generateLibraryIndex)
}

val generateBuildConstants by tasks.registering {
    val version = project.version.toString()
    val output = layout.buildDirectory.dir("generated/sources/buildConstants/java/main")
    inputs.property("version", version)
    outputs.dir(output)
    doLast {
        val file = output.get().file("net/prismarin/velocity/BuildConstants.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package net.prismarin.velocity;\n\npublic final class BuildConstants {\n\n" +
                "    public static final String VERSION = \"" + version + "\";\n\n" +
                "    private BuildConstants() {\n    }\n}\n"
        )
    }
}

sourceSets.main {
    java.srcDir(generateBuildConstants)
}

tasks.shadowJar {
    archiveFileName.set("prismarin-velocity.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api:.*"))
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        exclude(dependency("org.mongodb:.*:.*"))
    }
    relocate("org.spongepowered.configurate", "net.prismarin.libs.configurate")
    relocate("io.leangen.geantyref", "net.prismarin.libs.geantyref")
    relocate("net.kyori.option", "net.prismarin.libs.kyori.option")
    relocate("org.yaml.snakeyaml", "net.prismarin.libs.snakeyaml")
    relocate("io.lettuce", "net.prismarin.libs.lettuce")
    relocate("io.netty", "net.prismarin.libs.netty")
    relocate("reactor", "net.prismarin.libs.reactor")
    relocate("org.reactivestreams", "net.prismarin.libs.reactivestreams")
    relocate("com.zaxxer.hikari", "net.prismarin.libs.hikari")
    relocate("org.mariadb.jdbc", "net.prismarin.libs.mariadb")
}
