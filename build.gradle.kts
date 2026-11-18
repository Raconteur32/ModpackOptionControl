plugins {
    base
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
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
