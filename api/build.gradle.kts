plugins {
    id("prismarin.java")
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
}

dependencies {
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.gson)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.gson)
}

publishing {
    publications {
        register<MavenPublication>("api") {
            groupId = "net.prismarin"
            artifactId = "prismarin-api"
            from(components["java"])
            pom {
                name = "PRISMARIN API"
                description = "Public API of PRISMARIN, a framework for Minecraft networks on Paper and Velocity."
                url = "https://github.com/LeonManuel0705/prismarin"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                scm {
                    url = "https://github.com/LeonManuel0705/prismarin"
                    connection = "scm:git:https://github.com/LeonManuel0705/prismarin.git"
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/LeonManuel0705/prismarin")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}
