import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("com.github.albfernandez:juniversalchardet:2.5.0")
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("de.marhali:json5-java:3.0.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-properties:2.18.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.18.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
