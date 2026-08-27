import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.gradleup.shadow") version "8.3.9"
}

val ktorVersion = "3.1.3"

dependencies {
    implementation(project(":common"))

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-gson-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("de.marhali:json5-java:3.0.0")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-websockets:$ktorVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<ShadowJar> {
    archiveBaseName = "moc-web"
    archiveClassifier = ""
    mergeServiceFiles()
    manifest {
        attributes("Main-Class" to "fr.raconteur.moc.web.MainKt")
    }
}

// Frontend React (gui_web/frontend, design D2) — vite build génère
// src/main/resources/static/assets/ (bundle embarqué dans la page legacy
// pendant la migration). Incremental : npm ci ne tourne que si le lockfile
// change, vite build que si les sources changent.
val frontendDir = layout.projectDirectory.dir("frontend")

val npmCi = tasks.register<Exec>("npmCi") {
    workingDir(frontendDir)
    commandLine("npm", "ci", "--no-audit", "--no-fund")
    inputs.file(frontendDir.file("package-lock.json"))
    outputs.dir(frontendDir.dir("node_modules"))
}

val viteBuild = tasks.register<Exec>("viteBuild") {
    dependsOn(npmCi)
    workingDir(frontendDir)
    commandLine("npm", "run", "build")
    inputs.dir(frontendDir.dir("src"))
    inputs.files(
        frontendDir.file("package.json"),
        frontendDir.file("vite.config.ts"),
        frontendDir.file("tsconfig.json"),
    )
    outputs.dir(layout.projectDirectory.dir("src/main/resources/static/assets"))
}

tasks.named("processResources") {
    dependsOn(viteBuild)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Singleton objects (DraftPatch, RecompositionDraft, PlatformService.INSTANCE, ...)
    // carry state across test classes within the same JVM — isolate each test class
    // in its own forked JVM to avoid cross-class contamination.
    forkEvery = 1
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
