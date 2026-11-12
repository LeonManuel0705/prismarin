group = "net.prismarin"
version = "1.1.0"

tasks.register<Copy>("dist") {
    group = "distribution"
    description = "Collects the deployable plugin jars into the dist directory."
    into(layout.projectDirectory.dir("dist"))
    from(project(":core-paper").tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }) {
        rename { "PrismarinCore.jar" }
    }
    from(project(":lobby-paper").tasks.named<Jar>("jar").flatMap { it.archiveFile }) {
        rename { "PrismarinLobby.jar" }
    }
    from(project(":core-velocity").tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }) {
        rename { "PrismarinVelocity.jar" }
    }
}
