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
    dependsOn(":fabric:build", ":gui:packageTarGz")
}
