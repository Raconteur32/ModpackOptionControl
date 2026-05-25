package fr.raconteur.moc.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import fr.raconteur.moc.platform.PlatformService
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.text.bold
import com.varabyte.kotter.foundation.text.green
import com.varabyte.kotter.foundation.text.textLine

class MocCli : CliktCommand(name = "moc") {
    override fun run() {
        session {
            section {
                bold { green { textLine("MOC — Modpack Option Control") } }
                textLine("Hello from the CLI!")
            }.run()
        }
    }
}

fun main(args: Array<String>) {
    PlatformService.INSTANCE = CliPlatformService
    MocCli().main(args)
}
