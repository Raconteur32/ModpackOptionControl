pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
		mavenCentral()
		gradlePluginPortal()
	}

	plugins {
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
		id("org.jetbrains.kotlin.jvm") version "2.3.21"
		id("org.jetbrains.compose") version "1.11.0"
		id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
	}
}

rootProject.name = "moc"

include("common", "fabric", "gui")
