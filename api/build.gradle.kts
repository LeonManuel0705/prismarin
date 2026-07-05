plugins {
    id("prismarin.java")
}

dependencies {
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
}
