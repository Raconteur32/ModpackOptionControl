import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.3.21"
	id("org.jetbrains.compose")
	id("org.jetbrains.kotlin.plugin.compose")
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
	mavenCentral()
	maven("https://us-central1-maven.pkg.dev/varabyte-repos/public")
	maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
	google()
}

sourceSets {
	val common by creating
	val commonTest by creating {
		compileClasspath += common.output
		runtimeClasspath += common.output
	}
	val cli by creating {
		compileClasspath += common.output
		runtimeClasspath += common.output
	}
	val gui by creating {
		compileClasspath += common.output
		runtimeClasspath += common.output
	}
	val cliTest by creating {
		compileClasspath += common.output + cli.output + commonTest.output
		runtimeClasspath += common.output + cli.output + commonTest.output
	}
	main {
		compileClasspath += common.output
		runtimeClasspath += common.output
	}
}

configurations {
	named("implementation") {
		extendsFrom(configurations["commonImplementation"])
	}
	named("cliImplementation") {
		extendsFrom(configurations["commonImplementation"])
	}
	named("guiImplementation") {
		extendsFrom(configurations["commonImplementation"])
	}
	named("commonTestImplementation") {
		extendsFrom(configurations["commonImplementation"])
	}
	named("cliTestImplementation") {
		extendsFrom(configurations["cliImplementation"])
	}
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	// Common deps — available to both common and main (via configuration extension)
	"commonImplementation"("com.ibm.icu:icu4j:76.1")
	"commonImplementation"("com.jayway.jsonpath:json-path:2.9.0")
	"commonImplementation"("com.google.code.gson:gson:2.11.0")
	"commonImplementation"("de.marhali:json5-java:3.0.0")
	"commonImplementation"("com.fasterxml.jackson.dataformat:jackson-dataformat-properties:2.18.3")
	"commonImplementation"("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.18.3")

	// Embedded in the mod JAR via Jar-in-Jar
	include("com.ibm.icu:icu4j:76.1")
	include("com.jayway.jsonpath:json-path:2.9.0")
	include("de.marhali:json5-java:3.0.0")
	include("com.fasterxml.jackson.dataformat:jackson-dataformat-properties:2.18.3")
	include("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.18.3")

	// CLI deps
	"cliImplementation"("com.github.ajalt.clikt:clikt:5.0.3")
	"cliImplementation"("com.varabyte.kotter:kotter-jvm:1.1.2")

	// GUI deps
	"guiImplementation"(compose.desktop.currentOs)

	// Compose runtime needed on classpath for all compilations (compose compiler plugin requirement)
	// compileOnly keeps it out of the mod JAR and CLI runtime
	val composeRuntime = "org.jetbrains.compose.runtime:runtime-desktop:1.11.0"
	"commonCompileOnly"(composeRuntime)
	"commonTestCompileOnly"(composeRuntime)
	"cliCompileOnly"(composeRuntime)
	"cliTestCompileOnly"(composeRuntime)
	compileOnly(composeRuntime)

	// Test deps (no Minecraft runtime needed)
	"commonTestImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
	"commonTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
	"cliTestImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
	"cliTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.register<JavaExec>("runGui") {
	group = "application"
	classpath = sourceSets["gui"].runtimeClasspath
	mainClass.set("fr.raconteur.moc.gui.MainKt")
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("runCli") {
	group = "application"
	classpath = sourceSets["cli"].runtimeClasspath
	mainClass.set("fr.raconteur.moc.cli.MainKt")
	jvmArgs("--enable-native-access=ALL-UNNAMED")
	standardInput = System.`in`
}

tasks.register<Test>("testCommon") {
	group = "verification"
	description = "Run unit tests for the common sourceset (no Minecraft runtime)"
	useJUnitPlatform()
	testClassesDirs = sourceSets["commonTest"].output.classesDirs
	classpath = sourceSets["commonTest"].runtimeClasspath
}

tasks.register<Test>("testCli") {
	group = "verification"
	description = "Run unit tests for the CLI sourceset (no Minecraft runtime)"
	useJUnitPlatform()
	testClassesDirs = sourceSets["cliTest"].output.classesDirs
	classpath = sourceSets["cliTest"].runtimeClasspath
}

tasks.register("generateCliScript") {
	group = "application"
	dependsOn("compileCliKotlin", "compileCommonKotlin", "processResources")
	doLast {
		val cp = sourceSets["cli"].runtimeClasspath.asPath
		val script = rootProject.file("run-cli.sh")
		script.writeText("#!/bin/bash\nexec java --enable-native-access=ALL-UNNAMED -cp \"$cp\" fr.raconteur.moc.cli.MainKt \"\$@\"\n")
		script.setExecutable(true)
		println("Script généré : ${script.absolutePath}")
	}
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
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

compose.desktop {
	application {
		mainClass = "fr.raconteur.moc.gui.MainKt"
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
