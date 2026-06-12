import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":common"))
    implementation(compose.desktop.currentOs)
    implementation("com.google.code.gson:gson:2.11.0")
}

compose.desktop {
    application {
        mainClass = "fr.raconteur.moc.gui.MainKt"
    }
}

tasks.withType<JavaExec>().configureEach {
    project.findProperty("moc.gameDir")?.let { systemProperty("moc.gameDir", it.toString()) }
}

tasks.register<Exec>("packageTarGz") {
    dependsOn("createDistributable")
    group = "compose desktop"
    description = "Creates a tar.gz with the GUI app and a root wrapper script"

    val appBase = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
    val outDir  = layout.buildDirectory.dir("compose/binaries/main").get().asFile

    doFirst {
        val script = File(appBase, "gui/moc.sh")
        script.writeText("#!/bin/sh\nSCRIPT_DIR=\"\$(dirname \"\$(readlink -f \"\$0\")\")\"\nexec \"\$SCRIPT_DIR/bin/gui\" \"\$@\"\n")
        script.setExecutable(true)
        outDir.mkdirs()
    }

    workingDir(appBase)
    commandLine("tar", "-czf", "${outDir.absolutePath}/moc-linux.tar.gz", "gui")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
