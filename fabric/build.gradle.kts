import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    id("org.jetbrains.kotlin.jvm")
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

    implementation(project(":common"))
    include(project(":common"))

    include("com.github.albfernandez:juniversalchardet:2.5.0")
    include("com.jayway.jsonpath:json-path:2.9.0")
    include("de.marhali:json5-java:3.0.0")
    include("com.fasterxml.jackson.dataformat:jackson-dataformat-properties:2.18.3")
    include("com.electronwill.night-config:core:3.6.7")
    include("com.electronwill.night-config:toml:3.6.7")
}

tasks.processResources {
    val version = version
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
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
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${rootProject.name}" }
    }
}
