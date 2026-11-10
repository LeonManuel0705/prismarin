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

tasks.shadowJar {
    archiveFileName.set("prismarin-velocity.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api:.*"))
    }
    relocate("org.spongepowered.configurate", "net.prismarin.libs.configurate")
    relocate("io.leangen.geantyref", "net.prismarin.libs.geantyref")
    relocate("net.kyori.option", "net.prismarin.libs.kyori.option")
    relocate("org.yaml.snakeyaml", "net.prismarin.libs.snakeyaml")
    relocate("io.lettuce", "net.prismarin.libs.lettuce")
    relocate("com.mongodb", "net.prismarin.libs.mongodb")
    relocate("org.bson", "net.prismarin.libs.bson")
    relocate("io.netty", "net.prismarin.libs.netty")
    relocate("reactor", "net.prismarin.libs.reactor")
    relocate("org.reactivestreams", "net.prismarin.libs.reactivestreams")
    relocate("com.zaxxer.hikari", "net.prismarin.libs.hikari")
    relocate("org.mariadb.jdbc", "net.prismarin.libs.mariadb")
}
