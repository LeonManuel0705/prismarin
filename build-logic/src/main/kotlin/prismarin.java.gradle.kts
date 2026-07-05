plugins {
    java
}

group = "net.prismarin"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.register<Test>("integrationTest") {
    description = "Runs tests tagged with 'integration' against real containers."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    shouldRunAfter(tasks.test)
}
