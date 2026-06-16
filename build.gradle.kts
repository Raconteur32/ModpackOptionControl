plugins {
    base
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }
}

tasks.named("build") {
    dependsOn(":fabric:build")
}

tasks.register("dist") {
    group = "build"
    description = "Builds the Fabric mod and packages the GUI distribution"
    dependsOn(":fabric:build", ":gui:packageTarGz")
}
