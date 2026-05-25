package fr.raconteur.moc.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import fr.raconteur.moc.platform.PlatformService

class MocCli : CliktCommand(name = "moc") {
    override fun run() {
        runFileBrowser()
    }
}

fun main(args: Array<String>) {
    PlatformService.INSTANCE = CliPlatformService
    MocCli().main(args)
}
