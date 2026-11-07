plugins {
    id("prismarin.java")
    `java-library`
}

dependencies {
    api(project(":api"))
    api(libs.configurate.yaml)
    api(libs.lettuce.core)
    api(libs.mongodb.driver.sync)
    compileOnly(libs.gson)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.gson)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.plain)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.testcontainers.redis)
}
